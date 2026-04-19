package org.example.reader.tasks;

import com.impinj.octane.*;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import org.example.reader.ReaderOperations;
import org.example.reader.listener.BpiScanListener;
import org.example.reader.listener.PowerScanListener;
import org.example.util.Point;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

public class BpiReference {
    public static ImpinjReader READER = ReaderOperations.READER;
    public static double minPtx = 0.0; // 最小输出功率
    public static double maxPtx = 30.0; // 最大输出功率
    //    public static int targetAntenna = 1; // 目标天线端口
    public static CountDownLatch readLatch = null;
    public static BpiScanListener bpiScanListener = null;
    public static double noListenedRssi = Double.NEGATIVE_INFINITY; // -200;
    public static String BPI_Tag_EPC = "";
    public static String Tag_Feature = "";
    public static Path DATA_DIR = Path.of("data\\BPI_Reference");
    static String dateMsg ="";
    static Path now_DATA_DIR = null;
    //    static ArrayList<String> received_data = new ArrayList<>();
//    public static TagDataCache dataCache = new TagDataCache();
    static {
        dateMsg = LocalDate.now().toString();
        now_DATA_DIR = DATA_DIR.resolve("data-"+dateMsg);
    }

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
        System.out.println("=== Impinj读写器扫频获取BPI参考值程序 ===");
        try {
            Config config = null;
            config = ConfigFactory.load("config.conf");
            BPI_Tag_EPC = config.getString("bpi_tag_epc");
            Tag_Feature = config.getString("tag_feature");

            Path now_data_path = null;
            if (Tag_Feature == null) {
                LocalTime time = LocalTime.now(); // 例如: 14:30:45.123
                // 1. 转换为 "HH-mm-ss" 格式
                DateTimeFormatter formatter1 = DateTimeFormatter.ofPattern("HH-mm-ss");
                String timeStr1 = time.format(formatter1);
                // 示例: "14-30-45"
                now_data_path = now_DATA_DIR.resolve(timeStr1 + ".json");
            } else {
                now_data_path = DATA_DIR.resolve("tag-features").resolve(Tag_Feature + ".json");
            }

            Point.savePointsToJsonFile(get_BPI_for_each_Ptx(), now_data_path);

            System.out.println("=== 已扫描各频率BPI ===");
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
     * 扫频获取BPI
     */
    public static List<Point> get_BPI_for_each_Ptx()
            throws OctaneSdkException, InterruptedException {

        // 先停止阅读器
        ReaderOperations.stopReader();

//        // 设置 EPC 过滤
//        Settings settings = READER.querySettings();
//        FilterSettings filterSettings = settings.getFilters();
//        TagFilter filter1 = filterSettings.getTagFilter1();
//        filter1.setFilterOp(TagFilterOp.Match);
//        filter1.setMemoryBank(MemoryBank.Epc);
//        filter1.setBitPointer(32);
//        filter1.setBitCount(96);
//        filter1.setTagMask(BPI_Tag_EPC);
//        filterSettings.setTagFilter1(filter1);
//        settings.setFilters(filterSettings);
//        READER.applySettings(settings);

        // 创建标签报告监听器
        readLatch = new CountDownLatch(1);
        bpiScanListener = new BpiScanListener(BPI_Tag_EPC, readLatch);
        READER.setTagReportListener(bpiScanListener);

        // 读写器支持的功率
        FeatureSet features = READER.queryFeatureSet();
        List<TxPowerTableEntry> listPtx = features.getTxPowers();
        List<Point> Ptx_Bpi_Image = new ArrayList<>(listPtx.size());
//        for(TxPowerTableEntry p : listPtx){
//            System.out.println(p.Dbm);
//        }

//        // 只启用目标天线
//        Settings settings = READER.querySettings();
//        AntennaConfigGroup antennas = settings.getAntennas();
//        antennas.disableAll();
////        antennas.enableById(new short[]{ (short)targetAntenna });
//        AntennaConfig antennaConfig = antennas.getAntenna(targetAntenna);
//        antennaConfig.setEnabled(true);
//        antennaConfig.setIsMaxTxPower(false);
//        READER.applySettings(settings);

        minPtx = listPtx.getFirst().Dbm;
        maxPtx = listPtx.getLast().Dbm;

        // 扫频测试
        System.out.println("\n开始扫频测试...");
//        System.out.println("天线端口: " + targetAntenna);
        System.out.println("功率测试范围: " + minPtx + "dBm 到 " + maxPtx + "dBm");
        System.out.println("==========================================");

        // 首先检查最大功率是否能检测到标签
        double maxRssi = get_Rssi_at_now_Ptx(maxPtx);
        if (Double.isNaN(maxRssi)) {
            System.out.println("错误: 在最大功率 " + maxPtx + "dBm 下未检测到标签！");
            System.out.println("请检查: 1. 标签距离 2. 天线连接 3. 标签是否正常工作");
        }

        // 逐个检测不同输出功率的RSSI
        for (TxPowerTableEntry now : listPtx) {
            double nowPtx = now.Dbm;
            System.out.println("\n--- 当前输出功率：" + nowPtx + "Dbm ---");

            double nowRssi = get_Rssi_at_now_Ptx(nowPtx);

            if (!Double.isNaN(nowRssi)) {
                System.out.println("√ 检测到标签，RSSI 为：" + nowRssi + "Dbm");
            } else {
                System.out.println("× 未检测到标签");
                nowRssi = noListenedRssi;
            }
            Ptx_Bpi_Image.add(new Point(nowPtx, nowRssi + nowPtx));
        }

        // 停止阅读器
        ReaderOperations.stopReader();

        return Ptx_Bpi_Image;
    }

    /**
     * 在指定功率级别测试标签检测
     * @param nowPtx 测试功率
     * @return 检测到的RSSI
     */
    private static double get_Rssi_at_now_Ptx(double nowPtx)
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
        settings.setAntennas(antennas);
        READER.applySettings(settings);

        // 读取标签
        readLatch = new CountDownLatch(1);
        bpiScanListener.readLatch = readLatch;
        ReaderOperations.startReader();

        // 等待读取完成（最多 500 毫秒）
        boolean tagDetected = readLatch.await(500, TimeUnit.MILLISECONDS);

        // 停止阅读器
        ReaderOperations.stopReader();

        if (tagDetected) {
            System.out.println("能读到指定标签");
        } else {
            System.out.println("不能读到指定标签");
        }

        double rssi = bpiScanListener.rssi;
        bpiScanListener.rssi = Double.NaN;
        return rssi;
    }
}
