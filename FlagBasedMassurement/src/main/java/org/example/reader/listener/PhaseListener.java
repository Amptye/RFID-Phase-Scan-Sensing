package org.example.reader.listener;

import com.impinj.octane.ImpinjReader;
import com.impinj.octane.Tag;
import com.impinj.octane.TagReport;
import com.impinj.octane.TagReportListener;
import org.apache.commons.math3.util.MathUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

public class PhaseListener implements TagReportListener {
    public final String target_epc;
    public CountDownLatch readLatch;
    public CountDownLatch channelLatch;
//    public double channel = Double.NaN;
//    public double phase = Double.NaN;
    public Map<Double, List<Double>> channel_phases = new HashMap<>();

    /**
     * 构造函数
     * @param target_epc 目标 EPC，为 null 或 "" 则不筛选 EPC
     * @param readLatch 读到标签则减一的计数器，用来通知
     */
    public PhaseListener(String target_epc, CountDownLatch readLatch){
        super();
        this.target_epc = target_epc;
        this.readLatch = readLatch;
//        if(epc == null){
//            System.out.println("epc cannot be null!!!!!!!!!!");
//        }
    }

//    public void ResetPhase(){
//        phase = Double.NaN;
//    }
//    public void ResetChannel() {
//        channel = Double.NaN;
//    }
    public void ResetChannel_Phases(){
        channel_phases = new HashMap<>();
    }
    public void ResetReadLatch(int count) {
        readLatch = new CountDownLatch(count);
    }
    public void ResetChannelLatch(int count){
        channelLatch = new CountDownLatch(count);
    }
    public void ResetAll(int read_count, int channel_count) {
        channel_phases = new HashMap<>();
        readLatch = new CountDownLatch(read_count);
        channelLatch = new CountDownLatch(channel_count);
//        phase = Double.NaN;
//        channel = Double.NaN;
    }

    @Override
    public void onTagReported(ImpinjReader reader, TagReport report) {
//        if(epc == null) return;

        List<Tag> tags = report.getTags();

        double now_channel = 0;
        double now_phase = 0;
        int num_new_channel = 0;
        for (Tag t : tags) {
//            System.out.println("Target EPC : " + target_epc);
//            System.out.println("Read EPC : " + t.getEpc().toHexString());
            // target_epc 为空则不筛选
            if(target_epc == null || target_epc.isEmpty() || target_epc.equalsIgnoreCase(t.getEpc().toHexString())){
                readLatch.countDown();
                now_channel = t.getChannelInMhz();
                now_phase = t.getPhaseAngleInRadians();
//                System.out.println("------------------------ Channel: " + now_channel + " MHz");
//                System.out.println("------------------------ Phase: " + now_phase + " rad");
                if(!channel_phases.containsKey(now_channel)){
                    channel_phases.put(now_channel, new ArrayList<>());
                    num_new_channel++;
                    System.out.println("new channel: " + now_channel + " MHz");
                }
                channel_phases.get(now_channel).add(now_phase);
            }
        }
        for(int i=0; i<num_new_channel; ++i){
            channelLatch.countDown();
        }
    }

    /**
     * 计算相位的平均值，以列表中第一个相位为基准，后续相位如果与基准值相差超过 pi，则 +2pi/-2pi 后再计算
     * @param phases 相位列表
     * @return 平均值（模 2pi）
     */
    public static double get_mean_phase(List<Double> phases){
        if(phases == null || phases.isEmpty()) return Double.NaN;
        double total_phase = 0;
        int time = 0;

        double first_phase = phases.getFirst();
        int i = 0;
        while (Double.isNaN(first_phase)){
            i++;
            first_phase = phases.get(i);
        }
        if(Double.isNaN(first_phase)) return Double.NaN;
        total_phase += first_phase;
        time++;

        i++;
        double now_phase = 0;
        for (; i<phases.size(); ++i){
            now_phase = phases.get(i);
            if(Double.isNaN(now_phase)) continue;
            if(now_phase > first_phase
                    && now_phase - first_phase > Math.PI){
                total_phase += (now_phase - Math.TAU);
            }else if(now_phase < first_phase
                    && first_phase - now_phase > Math.PI){
                total_phase += (now_phase + Math.TAU);
            }else{
                total_phase += now_phase;
            }
            time++;
        }

        double mean_phase = Double.NaN;
        if(time > 0){
            mean_phase = total_phase/time;
            if(mean_phase < 0){
                mean_phase += Math.TAU;
            }else if(mean_phase > Math.TAU){
                mean_phase -= Math.TAU;
            }
        }

        return  mean_phase;
    }
}
