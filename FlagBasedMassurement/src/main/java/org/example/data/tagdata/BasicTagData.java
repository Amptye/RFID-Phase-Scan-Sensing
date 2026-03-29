package org.example.data.tagdata;

import java.time.*;

public class BasicTagData {
    // 标签唯一标识
    private final String epc;
    // 标签TID
    private final String tid;
    // 读取时间
    private final Instant readTime;


    public BasicTagData(String epc, String tid) {
        this.epc = epc;
        this.tid = tid;
        this.readTime = Instant.now();
    }
    /**
     * 不提供tid时，tid为"0000"
     */
    public BasicTagData(String epc){
        this(epc, "0000");
    }
    /**
     * 不提供epc、tid时，epc、tid均为"0000"
     */
    public BasicTagData(){
        this("0000","0000");
    }

    // 获取标签EPC
    public String getEpc() {
        return epc;
    }

    // 获取标签TID
    public String getTid() {
        return tid;
    }

    // 获取读取时间
    public Instant getReadTime() {
        return readTime;
    }
}