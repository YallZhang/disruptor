package com.train.disruptor.cake;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.EventHandlerGroup;
import com.lmax.disruptor.dsl.ProducerType;
import com.train.disruptor.cake.event.ExampleEvent;
import com.train.disruptor.cake.event.OriginRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @Author yananz_1@tujia.com
 * @Date 2019-04-04 17:06
 * @Description
 */
public class ExampleMain {
    private static final Logger logger = LoggerFactory.getLogger(ExampleMain.class);

    //ringBuffer数组设置为2的N次方，ringBuffer中的数据不会主动删除，但会被覆盖
    //这样可以不用发生GC
    private static final int RING_BUFFER_SIZE = 64;

    private static final Disruptor<ExampleEvent> DISRUPTOR = new Disruptor<>(
            ExampleEvent::new,
            RING_BUFFER_SIZE,
            new ThreadFactory() {
                AtomicInteger ato = new AtomicInteger();

                @Override
                public Thread newThread(Runnable r) {
                    return new Thread(r, "consumer_thread_of_" + ato.addAndGet(1));
                }
            },
            ProducerType.SINGLE,
            new BlockingWaitStrategy());

    private static ExecutorService producerPool = new ThreadPoolExecutor(
            1000,
            3000,
            10,
            TimeUnit.MINUTES,
            new ArrayBlockingQueue<>(40),
            new ThreadFactory() {
                AtomicInteger ato = new AtomicInteger();

                @Override
                public Thread newThread(Runnable r) {
                    return new Thread(r, "producer_thread_of_" + ato.addAndGet(1));
                }
            }
    );

    private static AtomicReference<String> holder = new AtomicReference<>("");

    static {
        logger.error("开始注册消费者组以及编排消费者执行顺序，并启动disruptor");
        //消费者1 日志处理
        EventHandler<ExampleEvent> printLogHandler = (event, sequence, endOfBatch) -> {

            long start = System.currentTimeMillis();
            logger.error("Thread:[{}] printLogHandler    开始消费事件:{}", Thread.currentThread().getName(), event.getOriginRequest().getLinkMan());
            //Thread.sleep(100);
            event.setLogInfo("记录了日志");
            logger.error("Thread:[{}] printLogHandler    结束消费事件:{} ,耗时：{} ms", Thread.currentThread().getName(), event.getOriginRequest().getLinkMan(), System.currentTimeMillis() - start);
        };

        //消费者2：复制处理
        EventHandler<ExampleEvent> copyHandler = (event, sequence, endOfBatch) -> {
            long start = System.currentTimeMillis();
            String linkMan = event.getOriginRequest().getLinkMan();
            logger.error("Thread:[{}] copyHandler        开始消费事件：{}", Thread.currentThread().getName(), event.getOriginRequest().getLinkMan());
            Thread.sleep(1000 * 60);


            event.setCopyInfo("已经复制了");
            logger.error("Thread:[{}] copyHandler        结束消费事件:{} ,耗时：{} ms", Thread.currentThread().getName(), event.getOriginRequest().getLinkMan(), System.currentTimeMillis() - start);

        };

        //消费者3：解码处理
        EventHandler<ExampleEvent> decompressHandler = (event, sequence, endOfBatch) -> {
            Thread.sleep(1000 * 60);

            long start = System.currentTimeMillis();
            logger.error("Thread:[{}] decompressHandler  开始消费事件：{}", Thread.currentThread().getName(), event.getOriginRequest().getLinkMan());
            //Thread.sleep(1000);
            event.setDecompressInfo("又解压了");
            logger.error("Thread:[{}] decompressHandler  结束消费事件:{} ,耗时：{} ms", Thread.currentThread().getName(), event.getOriginRequest().getLinkMan(), System.currentTimeMillis() - start);
        };

        EventHandler<ExampleEvent> checkHandler = (event, sequence, endOfBatch) -> {
            long start = System.currentTimeMillis();
            logger.error("Thread:[{}] checkHandler   开始消费事件：{}", Thread.currentThread().getName(), event.getOriginRequest().getLinkMan());
            logger.error("Thread:[{}] checkHandler   消费事件:{} 结束,耗时：{} ms", Thread.currentThread().getName(), event.getOriginRequest().getLinkMan(), System.currentTimeMillis() - start);
        };

        EventHandler<ExampleEvent> resultHandler = (event, sequence, endOfBatch) -> {
            long start = System.currentTimeMillis();
            logger.error("Thread:[{}] resultHandler  开始消费事件：{}", Thread.currentThread().getName(), event.getOriginRequest().getLinkMan());
            logger.error("Thread:[{}] resultHandler  消费事件:{} 整体结束,耗时：{} ms", Thread.currentThread().getName(), event.getOriginRequest().getLinkMan(), System.currentTimeMillis() - start);
        };

        EventHandlerGroup<ExampleEvent> eventHandlerGroup =
                DISRUPTOR.handleEventsWith(printLogHandler, copyHandler, decompressHandler);

        eventHandlerGroup = eventHandlerGroup.then(checkHandler);

        eventHandlerGroup.then(resultHandler);

        RingBuffer<ExampleEvent> ringBuffer = DISRUPTOR.start();
        logger.error("disruptor启动完毕");
    }


    public static void main(String[] args) throws InterruptedException {
        logger.error("主线程启动 Thread:{}", Thread.currentThread().getName());


        OriginRequest originRequest1 = new OriginRequest("张亚楠-" + 1, 1);
        logger.error("Producer Thread:{} publishing origin request event:{}", Thread.currentThread().getName(), originRequest1);
        DISRUPTOR.publishEvent(IntToExampleEventTranslator.INSTANCE, originRequest1);

        OriginRequest originRequest2 = new OriginRequest("张亚楠-" + 2, 1);
        logger.error("Producer Thread:{} publishing origin request event:{}", Thread.currentThread().getName(), originRequest2);
        DISRUPTOR.publishEvent(IntToExampleEventTranslator.INSTANCE, originRequest2);
    }


    public static void shutdown() {
        //关闭disruptor,方法会阻塞直到所有的事件都得到处理
        DISRUPTOR.shutdown();
        producerPool.shutdown();
    }

}
