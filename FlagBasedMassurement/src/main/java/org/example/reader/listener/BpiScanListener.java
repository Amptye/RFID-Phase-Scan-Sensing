package org.example.reader.listener;

import com.impinj.octane.ImpinjReader;
import com.impinj.octane.Tag;
import com.impinj.octane.TagReport;
import com.impinj.octane.TagReportListener;
import org.apache.logging.log4j.core.pattern.NotANumber;
import org.example.reader.tasks.PowerScan;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class BpiScanListener implements TagReportListener {
    public final String epc;
    public CountDownLatch readLatch;
    public double rssi = Double.NaN;
    public BpiScanListener(String epc, CountDownLatch readLatch){
        super();
        this.epc = epc;
        this.readLatch = readLatch;
        if(epc == null){
            System.out.println("epc cannot be null!!!!!!!!!!");
        }
    }

    @Override
    public void onTagReported(ImpinjReader reader, TagReport report) {
        if(epc == null) return;

        List<Tag> tags = report.getTags();

        int times = 0;
        double total_rssi = 0;
        for (Tag t : tags) {
//            System.out.println(epc);
//            System.out.println(t.getEpc().toHexString());
            if(epc.equalsIgnoreCase(t.getEpc().toHexString())){
                ++times;
                total_rssi += t.getPeakRssiInDbm();
            }
        }
        if(times > 0){
            rssi = total_rssi/times;
            if ((!Double.isNaN(rssi)) && (readLatch != null)) {
                readLatch.countDown();
            }
        }
    }
}
