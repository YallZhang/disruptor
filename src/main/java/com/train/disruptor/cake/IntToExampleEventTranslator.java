package com.train.disruptor.cake;

import com.lmax.disruptor.EventTranslatorOneArg;
import com.train.disruptor.cake.event.ExampleEvent;
import com.train.disruptor.cake.event.OriginRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class IntToExampleEventTranslator implements EventTranslatorOneArg<ExampleEvent, OriginRequest> {

    private static final Logger logger = LoggerFactory.getLogger(IntToExampleEventTranslator.class);
    public static final IntToExampleEventTranslator INSTANCE = new IntToExampleEventTranslator();

    @Override
    public void translateTo(ExampleEvent event, long sequence, OriginRequest originRequest) {
        event.setOriginRequest(originRequest);
        event.setCommitTime(System.currentTimeMillis());
        //logger.error("Thread:{} put data:{},sequence:{},arg0:{}", Thread.currentThread().getName(), event, sequence, originRequest);
    }
}
