package org.example.reader.tasks;

import com.impinj.octane.*;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import org.example.reader.ReaderOperations;
import org.example.reader.listener.RssiListener;
import org.example.util.math.Point;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class BpiTest {
    public static ImpinjReader READER = ReaderOperations.READER;
    public static String Target_EPC = null;
    public static double minPtx = 0.0; // 最小输出功率
    public static double maxPtx = 30.0; // 最大输出功率
    public static int numPtx = 91;
    public static CountDownLatch readLatch = null;
    public static RssiListener rssiListener = null;
    public static double noListenedRssi = Double.NaN; // Double.NEGATIVE_INFINITY; // -200;
    public static List<Point> bpiList = null;


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
        System.out.println("=== Impinj读写器在所有功率下的BPI检测 ===");
        try {
            Config config = null;
            config = ConfigFactory.load("config.conf");
            Target_EPC = config.hasPath("target_epc")
                    ? config.getString("target_epc")
                    : null;

            bpiList = get_BPI_for_each_Ptx();

            System.out.println("=== 已扫描各功率BPI ===");
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

        // 创建标签报告监听器
        readLatch = new CountDownLatch(1);
//        rssiListener = new RssiListener(BPI_Tag_EPC, readLatch);
        rssiListener = new RssiListener(Target_EPC, readLatch);
        READER.setTagReportListener(rssiListener);

        // 读写器支持的功率
        FeatureSet features = READER.queryFeatureSet();
        List<TxPowerTableEntry> listPtx = features.getTxPowers();
        List<Point> Ptx_Bpi_Image = new ArrayList<>(listPtx.size());
//        for(TxPowerTableEntry p : listPtx){
//            System.out.println(p.Dbm);
//        }

        minPtx = listPtx.getFirst().Dbm;
        maxPtx = listPtx.getLast().Dbm;
        numPtx = listPtx.size();

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
            return Ptx_Bpi_Image;
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
    public static double get_Rssi_at_now_Ptx(double nowPtx)
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
        rssiListener.readLatch = readLatch;
        rssiListener.ResetRssi();

        // 读取标签
        ReaderOperations.startReader();

        // 等待读取完成（最多 300 毫秒）
        boolean tagDetected = readLatch.await(300, TimeUnit.MILLISECONDS);

        double rssi = rssiListener.rssi;
        rssiListener.ResetRssi();

        // 停止阅读器
        ReaderOperations.stopReader();

        if (tagDetected) {
            System.out.println("能读到指定标签");
        } else {
            System.out.println("不能读到指定标签");
        }

        return rssi;
    }

}
