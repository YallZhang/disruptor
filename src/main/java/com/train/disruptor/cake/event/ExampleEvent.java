package com.train.disruptor.cake.event;

import java.util.concurrent.CountDownLatch;

/**
 * @Author yananz_1@tujia.com
 * @Date 2019-04-04 16:59
 * @Description
 */
public class ExampleEvent {
    private OriginRequest originRequest;
    private long commitTime;
    private String logInfo;
    private String copyInfo;
    private String decompressInfo;
    private CountDownLatch latch = new CountDownLatch(1);

    public String getLogInfo() {
        return logInfo;
    }

    public void setLogInfo(String logInfo) {
        this.logInfo = logInfo;
    }

    public String getCopyInfo() {
        return copyInfo;
    }

    public void setCopyInfo(String copyInfo) {
        this.copyInfo = copyInfo;
    }

    public String getDecompressInfo() {
        return decompressInfo;
    }

    public void setDecompressInfo(String decompressInfo) {
        this.decompressInfo = decompressInfo;
    }

    public CountDownLatch getLatch() {
        return latch;
    }

    public void setLatch(CountDownLatch latch) {
        this.latch = latch;
    }

    public OriginRequest getOriginRequest() {
        return originRequest;
    }

    public void setOriginRequest(OriginRequest originRequest) {
        this.originRequest = originRequest;
    }


    public long getCommitTime() {
        return commitTime;
    }

    public void setCommitTime(long commitTime) {
        this.commitTime = commitTime;
    }

    @Override
    public String toString() {
        return "ExampleEvent{" +
                "originRequest=" + originRequest +
                ", commitTime=" + commitTime +
                ", logInfo='" + logInfo + '\'' +
                ", copyInfo='" + copyInfo + '\'' +
                ", decompressInfo='" + decompressInfo + '\'' +
                '}';
    }
}
