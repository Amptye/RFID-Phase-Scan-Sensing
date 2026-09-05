package org.example.reader.listener;

import com.impinj.octane.ImpinjReader;
import com.impinj.octane.Tag;
import com.impinj.octane.TagReport;
import com.impinj.octane.TagReportListener;

import java.util.List;
import java.util.concurrent.CountDownLatch;

public class RssiListener implements TagReportListener {
    public final String target_epc;
    public CountDownLatch readLatch;
    public double rssi = Double.NaN;

    /**
     * 构造函数
     * @param target_epc 目标 EPC，为 null 或 "" 则不筛选 EPC
     * @param readLatch 读到标签则减一的计数器，用来通知
     */
    public RssiListener(String target_epc, CountDownLatch readLatch){
        super();
        this.target_epc = target_epc;
        this.readLatch = readLatch;
//        if(epc == null){
//            System.out.println("epc cannot be null!!!!!!!!!!");
//        }
    }

    public void ResetRssi(){
        rssi = Double.NaN;
    }

    @Override
    public void onTagReported(ImpinjReader reader, TagReport report) {
//        if(epc == null) return;

        List<Tag> tags = report.getTags();

        int times = 0;
        double total_rssi = 0;
        for (Tag t : tags) {
            System.out.println("Target EPC : " + target_epc);
            System.out.println("Read EPC : " + t.getEpc().toHexString());
            // target_epc 为空则不筛选
            if(target_epc == null || target_epc.isEmpty() || target_epc.equalsIgnoreCase(t.getEpc().toHexString())){
                ++times;
                total_rssi += t.getPeakRssiInDbm();
            }
//            ++times;
//            total_rssi += t.getPeakRssiInDbm();
        }
        if(times > 0){
            double nowRssi = total_rssi/times;
            if(!Double.isNaN(nowRssi) && (Double.isNaN(rssi) || nowRssi > rssi)){
                rssi = nowRssi;
                if (readLatch != null) {
                    readLatch.countDown();
                }
            }
        }
    }
}
