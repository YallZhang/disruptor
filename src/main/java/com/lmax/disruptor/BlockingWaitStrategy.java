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

import com.lmax.disruptor.util.ThreadHints;

/**
 * Blocking strategy that uses a lock and condition variable for {@link EventProcessor}s waiting on a barrier.
 * <p>
 * This strategy can be used when throughput and low-latency are not as important as CPU resource.
 */
public final class BlockingWaitStrategy implements WaitStrategy {
    private final Object mutex = new Object();

    //阻塞等待策略使用Lock+Condition的方式等待生产者生产可用事件，
    //而使用Busy Spin的方式等待可能出现的上一个消费者组未消费完成的情况。
    @Override
    public long waitFor(long sequence, Sequence cursorSequence, Sequence dependentSequence, SequenceBarrier barrier)
            throws AlertException, InterruptedException {
        long availableSequence;
        // 我理解的是当前生产者的游标 小于消费者即将要消费的序号时，也就是说此时消费者空闲了，没有可以消费的事件了。
        //这种其实是比较好的状态：也就是消费者的速度比生产者速度快。
        //在单生产者、多消费者场景下，且使用BlockingWaitStrategy时，这里是唯一需要加锁解锁的地方。
        if (cursorSequence.get() < sequence) {
            synchronized (mutex) {
                while (cursorSequence.get() < sequence) {
                    barrier.checkAlert();
                    // 循环等待，在Sequencer中publish进行唤醒；等待消费时也会在循环中定时唤醒。
                    // 循环等待的原因，是要检查alert状态。如果不检查将导致不能关闭Disruptor。
                    mutex.wait();
                }
            }
        }

        //如果现在生产者的待生产槽位 大于 消费者即将请求的槽位，那证明RingBuffer中还有事件可以消费。
        //这时只需要再判断当前消费者EventHandler线程之前是否依赖的有前驱消费者了。
        //todo:这个思想没觉得跟AQS的前驱Node节点for循环等待机制很像吗？

        // 给定序号大于上一个消费者组最慢消费者（如当前消费者为第一组则和生产者游标序号比较）序号时，需要等待。
        // 不能超前消费上一个消费者组未消费完毕的事件。
        // 那么为什么这里没有锁呢？可以想一下此时的场景，代码运行至此，已能保证生产者有新事件，如果进入循环，
        // 说明上一组消费者还未消费完毕。
        // 而通常我们的消费者都是较快完成任务的，所以这里才会考虑使用Busy Spin的方式等待上一组消费者完成消费。
        while ((availableSequence = dependentSequence.get()) < sequence) {
            barrier.checkAlert();
            ThreadHints.onSpinWait();
        }

        return availableSequence;
    }

    @Override
    public void signalAllWhenBlocking() {
        synchronized (mutex) {
            mutex.notifyAll();
        }
    }

    @Override
    public String toString() {
        return "BlockingWaitStrategy{" +
                "mutex=" + mutex +
                '}';
    }
}
