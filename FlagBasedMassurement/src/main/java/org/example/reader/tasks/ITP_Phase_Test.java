package org.example.reader.tasks;

import com.impinj.octane.*;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import org.apache.commons.lang3.tuple.Pair;
import org.example.reader.ReaderOperations;
import org.example.reader.listener.PhaseListener;
import org.example.util.math.Point;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ITP_Phase_Test {
    public static ImpinjReader READER = ReaderOperations.READER;
    public static String Target_EPC = null;
    public static Path DATA_DIR = Path.of("data");
    public static Path reference_DATA_DIR = DATA_DIR.resolve("ITP_Phase_Reference");
    public static String dateMsg = LocalDate.now().toString();

    public static boolean can_set_frequency = false;
    public static double detect_frequency = 902.75;
    public static int numFreq = 0;
    public static double minPtx = 0.0; // 最小输出功率
    public static double maxPtx = 30.0; // 最大输出功率
    public static int numPtx = 91;
    public static double stepPtx = 0.25;
    public static CountDownLatch readLatch = null;
    public static CountDownLatch channelLatch = null;
    public static PhaseListener phaseListener = null;
    public static double noListenedPhase = Double.NaN; // Double.NEGATIVE_INFINITY; // -200;
    public static Map<Double, Pair<Double, Double>> freq_ITP_Phase = null;
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
        System.out.println("=== Impinj读写器在ITP下的Phase检测 ===");
        Path frequency_Parent_Dir = null;
        Path reference_file_self = null;
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



            first_test_maxPtx = config.hasPath("first_test_maxPtx")
                    && config.getBoolean("first_test_maxPtx");
            read_timeout = config.hasPath("read_timeout")
                    ? config.getLong("read_timeout")
                    : 500;
            hopping_timeout = config.hasPath("hopping_timeout")
                    ? config.getLong("hopping_timeout")
                    : 500;

            frequency_Parent_Dir = getReference_ITP_Phase_Data_File_Dir(config);
            reference_file_self = getReference_ITP_Phase_Data_File_Self(config);

            freq_ITP_Phase = get_Freq_ITP_Phase();

            System.out.println();
            System.out.println(freq_ITP_Phase);
            System.out.println("\"\"\",");
            System.out.println("\"\"\"");
            freq_ITP_Phase.forEach((freq, itp_phase) -> {
                System.out.println("====== 频率：" + freq + " MHz "
                        + "|| ITP：" + itp_phase.getKey() + " dBm "
                        + "|| 相位：" + itp_phase.getValue() + " rad ======");
            });

            System.out.println("=== 已扫描各频率的ITP和Phase ===");
        } catch (ConfigException.IO ec){
            System.out.println("无法找到 config.conf 文件");
        } catch (OctaneSdkException ex) {
            System.out.println(ex.getMessage());
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
            ex.printStackTrace(System.out);
        }

        if(frequency_Parent_Dir != null && reference_file_self != null){
            for(Map.Entry<Double, Pair<Double, Double>> entry : freq_ITP_Phase.entrySet()){
                double freq = entry.getKey();
                Pair<Double, Double> itp_phase = entry.getValue();
                List<String> lines = List.of(
                        itp_phase.getKey() + ", " + itp_phase.getValue()
                );
                try {
                    Files.write(frequency_Parent_Dir.resolve(String.valueOf(freq)).resolve(reference_file_self),
                            lines,
                            StandardOpenOption.CREATE,      // 文件不存在则创建
                            StandardOpenOption.APPEND);     // 追加模式
                } catch (IOException e) {
                    System.out.println(e.getMessage());
                }
            }
        }
    }

    /**
     * 获取不同频率下的不同功率下的 phase
     * @return Map{频率, Pair{ITP, 相位}}
     */
    public static Map<Double, Pair<Double, Double>> get_Freq_ITP_Phase()
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


        Map<Double, Pair<Double, Double>> Freq_ITP_Phase = new HashMap<>();
        listFreq.forEach(freq ->
                Freq_ITP_Phase.put(freq, null));

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
                return Freq_ITP_Phase;
            }
        }
        read_at_maxPtx = true;

        // 逐个检测不同输出功率的相位
        int num = numPtx;
        if(first_test_maxPtx) num--;
        int num_of_freq_know_itp = 0;
        for (int i=0; i<num && num_of_freq_know_itp<numFreq; ++i) {
            TxPowerTableEntry now = listPtx.get(i);
            double nowPtx = now.Dbm;
            System.out.println("\n--- 当前输出功率：" + nowPtx + "Dbm ---");

            Map<Double, Double> nowPhase = get_Phases_at_now_Ptx(nowPtx);

            if (!nowPhase.isEmpty()) {
                System.out.println("√ 检测到标签");
                for(Map.Entry<Double, Double> entry : nowPhase.entrySet()){
                    double freq = entry.getKey();
                    double phase = entry.getValue();
                    if(Freq_ITP_Phase.get(freq) == null){
                        Freq_ITP_Phase.put(freq, Pair.of(nowPtx, phase));
                        num_of_freq_know_itp++;
                    }
                }
            } else {
                System.out.println("× 未检测到标签");
            }
//            Freq_ITP_Phase.add(new Point(nowPtx, nowPhase));
        }
        if(first_test_maxPtx && maxPtx_Phase != null && num_of_freq_know_itp < numFreq){
            for(Map.Entry<Double, Double> entry : maxPtx_Phase.entrySet()){
                double freq = entry.getKey();
                double phase = entry.getValue();
                if(Freq_ITP_Phase.get(freq) == null){
                    Freq_ITP_Phase.put(freq, Pair.of(maxPtx, phase));
                    num_of_freq_know_itp++;
                }
            }
        }

        // 停止阅读器
        ReaderOperations.stopReader();

        return Freq_ITP_Phase;
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

    public static Path getReference_ITP_Phase_Data_File_Dir(Config config){
        String target_epc = config.hasPath("target_epc")
                ? config.getString("target_epc")
                : "unknown_epc";
        String feature_DIR = config.hasPath("feature_dir")
                ? config.getString("feature_dir")
                : "tag-features";
//        String distance = config.hasPath("reference_distance")
//                ? config.getString("reference_distance")
//                : "unknown_distance";

        return reference_DATA_DIR
                .resolve(target_epc)
                .resolve(feature_DIR);
//                .resolve(distance);
    }

    public static Path getReference_ITP_Phase_Data_File_Self(Config config){
        Path file_path = null;

        boolean feature_lineSet = config.hasPath("feature_lineSet")
                && config.getBoolean("feature_lineSet");
        String tag_feature = config.hasPath("tag_feature")
                ? config.getString("tag_feature")
                : null;

        if (tag_feature == null) {
            LocalTime time = LocalTime.now(); // 例如: 14:30:45.123
            // 1. 转换为 "HH-mm-ss" 格式
            DateTimeFormatter formatter1 = DateTimeFormatter.ofPattern("HH-mm-ss");
            String timeStr1 = time.format(formatter1);
            // 示例: "14-30-45"
            file_path = Path.of(
                    "data-"+dateMsg,
                    timeStr1 + ".csv");
        } else {
            if(!feature_lineSet) {
                file_path = Path.of(
                        tag_feature + ".csv");
            } else {
                LocalDateTime time = LocalDateTime.now();
                DateTimeFormatter formatter1 = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");
                String timeStr1 = time.format(formatter1);
                file_path = Path.of(
                        tag_feature,
                        timeStr1 + ".csv");
            }
        }
        return file_path;
    }

}
