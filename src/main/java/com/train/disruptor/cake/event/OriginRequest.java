package com.train.disruptor.cake.event;

import java.io.Serializable;
import java.util.concurrent.CountDownLatch;

/**
 * @Author yananz_1@tujia.com
 * @Date 2020-03-21 11:55
 * @Description
 */
public class OriginRequest implements Serializable {
    private static final long serialVersionUID = 1411159627892944138L;
    private String linkMan;
    private Integer bookingCount;

    public OriginRequest(String linkMan, Integer bookingCount) {
        this.linkMan = linkMan;
        this.bookingCount = bookingCount;
    }

    @Override
    public String toString() {
        return "OriginRequest{" +
                "linkMan='" + linkMan + '\'' +
                ", bookingCount=" + bookingCount +
                '}';
    }

    public Integer getBookingCount() {
        return bookingCount;
    }

    public void setBookingCount(Integer bookingCount) {
        this.bookingCount = bookingCount;
    }

    public String getLinkMan() {
        return linkMan;
    }

    public void setLinkMan(String linkMan) {
        this.linkMan = linkMan;
    }


}
