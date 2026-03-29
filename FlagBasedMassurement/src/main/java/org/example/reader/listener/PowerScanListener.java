package org.example.reader.listener;

import com.impinj.octane.ImpinjReader;
import com.impinj.octane.Tag;
import com.impinj.octane.TagReport;
import com.impinj.octane.TagReportListener;
import org.example.reader.tasks.PowerScan;

import java.util.List;

public class PowerScanListener implements TagReportListener {
    @Override
    public void onTagReported(ImpinjReader reader, TagReport report) {
        List<Tag> tags = report.getTags();

//        for (Tag t : tags) {
//            // 只记录目标天线的标签
//            if (t.getAntennaPortNumber() == PowerScan.targetAntenna) {
//                tagActivations.add(t.getPeakRssiInDbm());
//            }
//        }

        if (PowerScan.readLatch != null) {
            PowerScan.readLatch.countDown();
        }
    }
}