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


/**
 * {@link SequenceBarrier} handed out for gating {@link EventProcessor}s on a cursor sequence and optional dependent {@link EventProcessor}(s),
 * using the given WaitStrategy.
 */
final class ProcessingSequenceBarrier implements SequenceBarrier {
    private final WaitStrategy waitStrategy;

    /**
     * 当前SequenceBarrier依赖的消费者组的序号
     * 是一个FixedSequenceGroup类型，继承了Sequence类，FixedSequenceGroup内部是一个Sequence[]数组
     */
    private final Sequence dependentSequence;

    private volatile boolean alerted = false;

    /**
     * 重要的游标cursor
     */
    private final Sequence cursorSequence;

    /**
     * sequencer是SingleProducerSequencer或者MultiProducerSequencer
     */
    private final Sequencer sequencer;

    ProcessingSequenceBarrier(
            final Sequencer sequencer,
            final WaitStrategy waitStrategy,
            final Sequence cursorSequence,
            final Sequence[] dependentSequences
    ) {
        this.sequencer = sequencer;
        this.waitStrategy = waitStrategy;
        this.cursorSequence = cursorSequence;
        if (0 == dependentSequences.length) {
            dependentSequence = cursorSequence;
        } else {
            dependentSequence = new FixedSequenceGroup(dependentSequences);
        }
    }

    //每个消费者EventHandler线程在消费时候，需要获取其最大可消费的序列号，
    //这个最大可消费序列号是通过 SequenceBarrier # waitFor 方法来实现的

    /**
     *
     * @param sequence 消费者EventHandler正在等待消费的槽位点，这个属性维护在BatchEventProcessor中，每消费一段
     *                 sequence就会进行更新
     */
    @Override
    public long waitFor(final long sequence) throws AlertException, InterruptedException, TimeoutException {

        // 判断是否中断，如果中断就抛出异常，这样上层捕捉，就可以停止 BatchEventProcessor
        checkAlert();

        // 根据不同的策略获取可用的序列
        long availableSequence = waitStrategy.waitFor(sequence, cursorSequence, dependentSequence, this);

        // 可用序列比申请的序列小，直接返回
        if (availableSequence < sequence) {
            return availableSequence;
        }

        // 如果是单生产者，直接返回 availableSequence；对于多生产者判断是否可用，不可用返回sequence-1
        return sequencer.getHighestPublishedSequence(sequence, availableSequence);
    }

    @Override
    public long getCursor() {
        return dependentSequence.get();
    }

    @Override
    public boolean isAlerted() {
        return alerted;
    }

    @Override
    public void alert() {
        alerted = true;
        waitStrategy.signalAllWhenBlocking();
    }

    @Override
    public void clearAlert() {
        alerted = false;
    }

    @Override
    public void checkAlert() throws AlertException {
        if (alerted) {
            throw AlertException.INSTANCE;
        }
    }
}