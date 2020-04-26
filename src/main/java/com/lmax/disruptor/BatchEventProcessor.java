/*
 * Copyright 2011 LMAX Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.lmax.disruptor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;


/**
 * EventProcessor的本质是一个Runable线程可执行体。
 * 至于为什么要叫做BatchEventProcessor，
 * 可以看看在run()方法里每次waitFor获取的availableSequence是当前能够使用的最大值，
 * 然后再循环处理这些数据。这样当消费者有瞬时抖动，
 * 导致暂时落后生产者时，可在下一次循环中，批量处理所有落后的事件。
 *
 * Convenience class for handling the batching semantics of consuming entries from a {@link RingBuffer}
 * and delegating the available events to an {@link EventHandler}.
 * <p>
 * If the {@link EventHandler} also implements {@link LifecycleAware} it will be notified just after the thread
 * is started and just before the thread is shutdown.
 *
 * @param <T> event implementation storing the data for sharing during exchange or parallel coordination of an event.
 */
public final class BatchEventProcessor<T> implements EventProcessor {
    private static final Logger logger = LoggerFactory.getLogger(BatchEventProcessor.class);

    private static final int IDLE = 0;
    private static final int HALTED = IDLE + 1;
    private static final int RUNNING = HALTED + 1;

    private final AtomicInteger running = new AtomicInteger(IDLE);

    private ExceptionHandler<? super T> exceptionHandler = new FatalExceptionHandler();

    //这个dataProvider就是RingBuffer，可以理解为数据源。
    private final DataProvider<T> dataProvider;

    /**
     * 消费者要想消费事件，首先得通过sequenceBarrier的放行
     */
    private final SequenceBarrier sequenceBarrier;

    private final EventHandler<? super T> eventHandler;

    // 当前消费者Handler线程上一次执行到的位置
    private final Sequence sequence = new Sequence(-1L);

    private final TimeoutHandler timeoutHandler;
    // 每次循环取得一批可用事件后，在实际处理前调用
    private final BatchStartAware batchStartAware;

    /**
     * Construct a {@link EventProcessor} that will automatically track the progress by updating its sequence when
     * the {@link EventHandler#onEvent(Object, long, boolean)} method returns.
     *
     * @param dataProvider    to which events are published.
     * @param sequenceBarrier on which it is waiting.
     * @param eventHandler    is the delegate to which events are dispatched.
     */
    public BatchEventProcessor(
            final DataProvider<T> dataProvider,
            final SequenceBarrier sequenceBarrier,
            final EventHandler<? super T> eventHandler) {
        this.dataProvider = dataProvider;
        this.sequenceBarrier = sequenceBarrier;
        this.eventHandler = eventHandler;

        if (eventHandler instanceof SequenceReportingEventHandler) {
            ((SequenceReportingEventHandler<?>) eventHandler).setSequenceCallback(sequence);
        }

        batchStartAware =
                (eventHandler instanceof BatchStartAware) ? (BatchStartAware) eventHandler : null;
        timeoutHandler =
                (eventHandler instanceof TimeoutHandler) ? (TimeoutHandler) eventHandler : null;
    }

    @Override
    public Sequence getSequence() {
        return sequence;
    }

    @Override
    public void halt() {
        running.set(HALTED);
        sequenceBarrier.alert();
    }

    @Override
    public boolean isRunning() {
        return running.get() != IDLE;
    }

    /**
     * Set a new {@link ExceptionHandler} for handling exceptions propagated out of the {@link BatchEventProcessor}
     *
     * @param exceptionHandler to replace the existing exceptionHandler.
     */
    public void setExceptionHandler(final ExceptionHandler<? super T> exceptionHandler) {
        if (null == exceptionHandler) {
            throw new NullPointerException();
        }

        this.exceptionHandler = exceptionHandler;
    }

    /**
     * 这种方法有点像JDK ThreadPoolExecutor的线程池
     * It is ok to have another thread rerun this method after a halt().
     *
     * @throws IllegalStateException if this object instance is already running in a thread
     */
    @Override
    public void run() {
        logger.error("开始执行线程:[{}]的run方法，run是个死循环", Thread.currentThread().getName());
        if (running.compareAndSet(IDLE, RUNNING)) {
            sequenceBarrier.clearAlert();

            notifyStart();
            try {
                if (running.get() == RUNNING) {
                    processEvents();
                }
            } finally {
                notifyShutdown();
                running.set(IDLE);
            }
        } else {
            // This is a little bit of guess work.  The running state could of changed to HALTED by
            // this point.  However, Java does not have compareAndExchange which is the only way
            // to get it exactly correct.
            if (running.get() == RUNNING) {
                throw new IllegalStateException("Thread is already running");
            } else {
                earlyExit();
            }
        }
        logger.error("线程[{}]的run方法结束", Thread.currentThread().getName());
    }

    private void processEvents() {
        T event = null;
        long nextSequence = sequence.get() + 1L;

        while (true) {
            try {
                logger.info("获取最大可以消费的槽位序号，表示在之前的都可以安全消费");
                final long availableSequence = sequenceBarrier.waitFor(nextSequence);
                if (batchStartAware != null) {
                    batchStartAware.onBatchStart(availableSequence - nextSequence + 1);
                }

                // 批处理区间的事件s在此处得以体现
                //todo:此处可以优化为：对于相同的event的参数，合并为一次操作，不再执行这么多次的while循环。
                while (nextSequence <= availableSequence) {
                    event = dataProvider.get(nextSequence);
                    eventHandler.onEvent(event, nextSequence, nextSequence == availableSequence);
                    nextSequence++;
                }

                // 记录对应消费者下一次该消费哪个槽位
                sequence.set(availableSequence);
            } catch (final TimeoutException e) {
                notifyTimeout(sequence.get());
            } catch (final AlertException ex) {
                if (running.get() != RUNNING) {
                    break;
                }
            } catch (final Throwable ex) {
                exceptionHandler.handleEventException(ex, nextSequence, event);
                sequence.set(nextSequence);
                nextSequence++;
            }
        }
    }

    private void earlyExit() {
        notifyStart();
        notifyShutdown();
    }

    private void notifyTimeout(final long availableSequence) {
        try {
            if (timeoutHandler != null) {
                timeoutHandler.onTimeout(availableSequence);
            }
        } catch (Throwable e) {
            exceptionHandler.handleEventException(e, availableSequence, null);
        }
    }

    /**
     * Notifies the EventHandler when this processor is starting up
     */
    private void notifyStart() {
        if (eventHandler instanceof LifecycleAware) {
            try {
                ((LifecycleAware) eventHandler).onStart();
            } catch (final Throwable ex) {
                exceptionHandler.handleOnStartException(ex);
            }
        }
    }

    /**
     * Notifies the EventHandler immediately prior to this processor shutting down
     */
    private void notifyShutdown() {
        if (eventHandler instanceof LifecycleAware) {
            try {
                ((LifecycleAware) eventHandler).onShutdown();
            } catch (final Throwable ex) {
                exceptionHandler.handleOnShutdownException(ex);
            }
        }
    }
}