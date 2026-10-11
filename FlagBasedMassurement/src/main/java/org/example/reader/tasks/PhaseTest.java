package org.example.reader.tasks;

import com.impinj.octane.*;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import org.example.reader.ReaderOperations;
import org.example.reader.listener.PhaseListener;
import org.example.util.math.Point;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class PhaseTest {
    public static ImpinjReader READER = ReaderOperations.READER;
    public static String Target_EPC = null;
    public static boolean can_set_frequency = false;
    public static double detect_frequency = 902.75;
    public static int numFreq = 0;
    public static double start_ptx = 0;
    public static int start_index = 0;
    public static int end_index = 90;
    public static double minPtx = 0.0; // 最小输出功率
    public static double maxPtx = 30.0; // 最大输出功率
    public static int numPtx = 91;
    public static double stepPtx = 0.25;
    public static boolean calculate_delta_phi = false;
    public static int limit_num_ptx = 91;
    public static int delta_ptx_time = 1;
    public static CountDownLatch readLatch = null;
    public static CountDownLatch channelLatch = null;
    public static PhaseListener phaseListener = null;
    public static double noListenedPhase = Double.NaN; // Double.NEGATIVE_INFINITY; // -200;
    public static Map<Double, List<Point>> freq_PhaseList = null;
    public static boolean first_test_maxPtx = false;
    public static boolean read_at_maxPtx = false;
    public static long read_timeout = 500;
    public static long hopping_timeout = 500;


    public static void main(String[] args) {
        ReaderOperations.setNewReader();
        try {
            // 连接阅读器
            ReaderOperations.connectReader();

            // 配置阅读器
            ReaderOperations.configReader();
            READER = ReaderOperations.READER;

            // 运行
            run();

            // 停止阅读器
            ReaderOperations.stopReader();

            // 与阅读器断开连接
            ReaderOperations.disconnectReader();

        } catch (OctaneSdkException ex) {
            System.out.println(ex.getMessage());
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
            ex.printStackTrace(System.out);
        }
    }

    public static void run() {
        System.out.println("=== Impinj读写器在所有功率下的Phase检测 ===");
        try {
            Config config = null;
            config = ConfigFactory.load("config.conf");
            Target_EPC = config.hasPath("target_epc")
                    ? config.getString("target_epc")
                    : null;

            // 如果阅读器处于跳频地区，则不能设置频率；
            // 如果可以设置频率，还要看 config 中是否规定了“不能”设置频率
            can_set_frequency =
                    !(
                            READER.queryFeatureSet().isHoppingRegion()
                                    || (
                                            config.hasPath("can_set_frequency")
                                                    && !config.getBoolean("can_set_frequency")
                            )
                    );
            detect_frequency = config.hasPath("detect_frequency")
                    ? config.getDouble("detect_frequency")
                    : 902.75;

            calculate_delta_phi = config.hasPath("calculate_delta_phi")
                    && config.getBoolean("calculate_delta_phi");

            start_ptx = config.hasPath("start_ptx")
                    ? config.getDouble("start_ptx")
                    : 0;
            limit_num_ptx = config.hasPath("limit_num_ptx")
                    ? config.getInt("limit_num_ptx")
                    : 91;
            delta_ptx_time = calculate_delta_phi
                    ? (config.hasPath("delta_ptx_time")
                        ? config.getInt("delta_ptx_time")
                        : 1)
                    : 0;

            first_test_maxPtx = config.hasPath("first_test_maxPtx")
                    && config.getBoolean("first_test_maxPtx");
            read_timeout = config.hasPath("read_timeout")
                    ? config.getLong("read_timeout")
                    : 500;
            hopping_timeout = config.hasPath("hopping_timeout")
                    ? config.getLong("hopping_timeout")
                    : 500;

            if(calculate_delta_phi){
                freq_PhaseList = get_Freq_DeltaPhaseList();
            } else {
                freq_PhaseList = get_Freq_PhaseList();
            }

            System.out.println();
            System.out.println(freq_PhaseList);
            System.out.println("\"\"\",");
            System.out.println("\"\"\"");

            System.out.println("=== 已扫描各功率Phase ===");
        } catch (ConfigException.IO ec){
            System.out.println("无法找到 config.conf 文件");
        } catch (OctaneSdkException ex) {
            System.out.println(ex.getMessage());
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
            ex.printStackTrace(System.out);
        }
    }

    /**
     * 获取不同频率下的：
     * 若 calculate_delta_phi 为 true，不同功率对 (p1, p1+0.25) 下的 phase1 - phase2（带正负号）
     * 若 calculate_delta_phi 为 false，不同功率对应的相位
     */
    public static Map<Double, List<Point>> get_Freq_DeltaPhaseList()
            throws OctaneSdkException, InterruptedException {
        Map<Double, List<Point>> Freq_Ptx_Phase_Image = get_Freq_PhaseList();
        Map<Double, List<Point>> Freq_Ptx_DeltaPhase_Image = new HashMap<>();
        Freq_Ptx_Phase_Image.forEach((freq, phases) -> {
            if(phases.isEmpty() || phases.size()<2){
                Freq_Ptx_DeltaPhase_Image.put(freq, new ArrayList<>());
            }else{
                List<Point> deltaPhases = new ArrayList<>(phases.size()-1);
                Point p1;
                Point p2;
                for(int i=0,j=limit_num_ptx; j>0; ++i,--j){
                    p1 = phases.get(i);
                    p2 = phases.get(phases.size() - j);
                    deltaPhases.add(
                            new Point(
                                    p1.x(),
                                    get_DeltaPhase(p1.y(), p2.y())
                            )
                    );
                }
                Freq_Ptx_DeltaPhase_Image.put(freq, deltaPhases);
            }
        });
        return Freq_Ptx_DeltaPhase_Image;
    }


    /**
     * 获取不同频率下的不同功率下的 phase
     * @return Map{频率, List{Point{功率, 相位}}}
     */
    public static Map<Double, List<Point>> get_Freq_PhaseList()
            throws OctaneSdkException, InterruptedException {

        // 先停止阅读器
        ReaderOperations.stopReader();

        // 创建标签报告监听器
        readLatch = new CountDownLatch(1);
        phaseListener = new PhaseListener(Target_EPC, readLatch);
        READER.setTagReportListener(phaseListener);

        FeatureSet features = READER.queryFeatureSet();

        // 读写器支持的功率
        List<TxPowerTableEntry> listPtx = features.getTxPowers();
//        for(TxPowerTableEntry p : listPtx){
//            System.out.println(p.Dbm);
//        }
        minPtx = listPtx.getFirst().Dbm;
        maxPtx = listPtx.getLast().Dbm;
        numPtx = listPtx.size();
        stepPtx = (maxPtx - minPtx) / (numPtx - 1);

        // 设置当前频率
        List<Double> listFreq = features.getTxFrequencies();
        System.out.println("Valid Frequencies: " + listFreq);
        if(can_set_frequency){
            listFreq = new ArrayList<>();
            listFreq.add(detect_frequency);
            Settings settings = READER.querySettings();
            settings.setTxFrequenciesInMhz((ArrayList<Double>) listFreq);
            READER.applySettings(settings);
        } else {
            System.out.println("This reader does not allow the transmit frequencies to be specified.");
        }
        System.out.println(listFreq);
        numFreq = listFreq.size();
        System.out.println(numFreq);

        start_index = 0;
        end_index = numPtx-1;
        if(start_ptx > 0 && start_ptx > minPtx){
            start_index++;
            for (; start_index < numPtx; ++start_index) {
                if (listPtx.get(start_index).Dbm >= start_ptx){
                    start_ptx = listPtx.get(start_index).Dbm;
                    break;
                }
            }
            if(start_index == numPtx){
                start_ptx = maxPtx;
                start_index = numPtx-1;
            }
            minPtx = start_ptx;
        } else {
            start_ptx = minPtx;
        }

        if(limit_num_ptx <= 0) {
            limit_num_ptx = numPtx;
        }
        if (start_index + limit_num_ptx + delta_ptx_time > numPtx) {
            limit_num_ptx = numPtx - start_index - delta_ptx_time;
            if(limit_num_ptx <= 0) {
                System.out.println("xxxxxxxxxxxxxxxxxxxxxxxx 起始功率 + 功率差值 > 最大功率！！！ xxxxxxxxxxxxxxxxxxxxxxx");
                return new HashMap<>();
            }
        }
        numPtx = limit_num_ptx + delta_ptx_time;
        end_index = start_index + numPtx - 1;
        maxPtx = listPtx.get(end_index).Dbm;


        Map<Double, List<Point>> Freq_Ptx_Phase = new HashMap<>();
        listFreq.forEach(freq ->
                Freq_Ptx_Phase.put(freq, new ArrayList<>(numPtx)));

        // 扫功率测试
        System.out.println("\n开始扫功率测试...");
//        System.out.println("天线端口: " + targetAntenna);
        System.out.println("功率测试范围: " + minPtx + "dBm 到 " + maxPtx + "dBm");
        System.out.println("==========================================");

        // 首先检查最大功率是否能检测到标签
        Map<Double, Double> maxPtx_Phase = null;
        if(first_test_maxPtx) {
            maxPtx_Phase = get_Phases_at_now_Ptx(maxPtx);
            if (maxPtx_Phase.isEmpty()) {
                System.out.println("错误: 在最大功率 " + maxPtx + "dBm 下未检测到标签！");
                System.out.println("请检查: 1. 标签距离 2. 天线连接 3. 标签是否正常工作");
                read_at_maxPtx = false;
                return Freq_Ptx_Phase;
            }
        }
        read_at_maxPtx = true;

        // 逐个检测不同输出功率的相位
        int num = numPtx;
        if(first_test_maxPtx) num--;
        for (int i=0; i<num; ++i) {
            if(i >= limit_num_ptx && i < delta_ptx_time) continue;
            TxPowerTableEntry now = listPtx.get(i+start_index);
            double nowPtx = now.Dbm;
            System.out.println("\n--- 当前输出功率：" + nowPtx + "Dbm ---");

            Map<Double, Double> nowPhase = get_Phases_at_now_Ptx(nowPtx);

            if (!nowPhase.isEmpty()) {
                System.out.println("√ 检测到标签");
//                nowPhase.forEach((freq, phase) -> {
//                    System.out.println("Channel " + freq + "下的 Phase 为：" + phase + " rad");
//                    Freq_Ptx_Phase.get(freq).add(new Point(nowPtx, phase));
//                });
                Freq_Ptx_Phase.forEach((freq, phases) -> {
                    double phase = nowPhase.containsKey(freq)
                            ? nowPhase.get(freq)
                            : noListenedPhase;
                    phases.add(new Point(nowPtx, phase));
                });
            } else {
                System.out.println("× 未检测到标签");
//                nowPhase = noListenedPhase;
                Freq_Ptx_Phase.forEach((freq, phases) -> {
                    phases.add(new Point(nowPtx, noListenedPhase));
                });
            }
//            Freq_Ptx_Phase.add(new Point(nowPtx, nowPhase));
        }
        if(first_test_maxPtx && maxPtx_Phase != null){
            maxPtx_Phase.forEach((freq, phase) -> {
//                System.out.println("Channel " + freq + "下的 Phase 为：" + phase + " rad");
                Freq_Ptx_Phase.get(freq).add(new Point(maxPtx, phase));
            });
        }

        // 停止阅读器
        ReaderOperations.stopReader();

        return Freq_Ptx_Phase;
    }

    /**
     * 计算两个相位的差值在 [-pi, pi] 范围内的对应值
     * @return phase1 - phase2 在 [-pi, pi] 范围内的对应值
     */
    public static double get_DeltaPhase(double phase1, double phase2){
        if(Double.isNaN(phase1) || Double.isNaN(phase2)) return Double.NaN;
        double diff = phase1 - phase2;
        if(diff > Math.PI) diff -= Math.TAU;
        else if(diff < -Math.PI) diff += Math.TAU;
        return diff;
    }

    /**
     * 在指定功率下检测相位
     * @param nowPtx 测试功率
     * @return 检测到的 Map{Frequency, Phase}，如果未检测到标签则返回 空映射 new HashMap<>()
     */
    public static Map<Double, Double> get_Phases_at_now_Ptx(double nowPtx)
            throws OctaneSdkException, InterruptedException {

        // 先停止阅读器
        ReaderOperations.stopReader();

        // 设置当前功率
        Settings settings = READER.querySettings();
        AntennaConfigGroup antennas = settings.getAntennas();
        for(AntennaConfig ac : antennas){
            if(ac.isEnabled()) {
                ac.setIsMaxTxPower(false);
                ac.setTxPowerinDbm(nowPtx);
            }
        }
//        settings.setAntennas(antennas);
        READER.applySettings(settings);

        // 重置监听器
        readLatch = new CountDownLatch(1);
        phaseListener.readLatch = readLatch;
        channelLatch = new CountDownLatch(numFreq);
        phaseListener.channelLatch = channelLatch;
        phaseListener.ResetChannel_Phases();

        // 读取标签
        ReaderOperations.startReader();

        // 等待读取完成（最多 500 毫秒）
        boolean tagDetected = readLatch.await(2*read_timeout, TimeUnit.MILLISECONDS);
        if(tagDetected) channelLatch.await(2*hopping_timeout * numFreq, TimeUnit.MILLISECONDS);

        // 停止阅读器
        ReaderOperations.stopReader();

        Map<Double, List<Double>> channel_phases = phaseListener.channel_phases;
        phaseListener.ResetChannel_Phases();

        Map<Double, Double> channel_phase = new HashMap<>();
        channel_phases.forEach((channel, phases) ->
                channel_phase.put(channel, PhaseListener.get_mean_phase(phases)));

        if (tagDetected) {
            System.out.println("能读到指定标签");
        } else {
            System.out.println("不能读到指定标签");
        }

        return channel_phase;
    }

}
