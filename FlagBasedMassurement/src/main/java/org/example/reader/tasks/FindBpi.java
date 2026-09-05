package org.example.reader.tasks;

import com.impinj.octane.*;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import org.example.reader.ReaderOperations;
import org.example.reader.listener.RssiListener;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Impinj读写器功率扫描程序（二分查找优化版）
 * 功能：通过单天线使用二分查找算法，快速寻找标签刚好被激活的最小输出功率，并计算该功率下的BPI
 */
public class FindBpi {
    public static ImpinjReader READER = ReaderOperations.READER;
    public static String Target_EPC = null;
    public static String Tag_Feature = "";
    public static double PTX = 20.0;
    public static double RSSI = Double.NaN;
    public static double BPI = PTX + RSSI;
    public static double minPtx = 0.0; // 最小输出功率
    public static double maxPtx = 30.0; // 最大输出功率
    public static CountDownLatch readLatch = null;
    public static RssiListener rssiListener = null;
    public static double noListenedRssi = Double.NaN; // Double.NEGATIVE_INFINITY; // -200;

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
        System.out.println("=== Impinj读写器最小激活功率扫描程序 ===");
        try {
            Config config = null;
            config = ConfigFactory.load("config.conf");
            Target_EPC = config.hasPath("target_epc")
                    ? config.getString("target_epc")
                    : null;
            Tag_Feature = config.hasPath("tag_feature")
                    ? config.getString("tag_feature")
                    : null;

//            PTX = find_min_activation_Ptx();
            find_min_activation_Ptx();
            System.out.println("=== 最低输出功率：" + PTX + " dBm ===");
            System.out.println("=== BPI: " + BPI + " dBm ===");
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
     * 寻找最小激活功率
     */
    public static void find_min_activation_Ptx()
            throws OctaneSdkException, InterruptedException {

        // 先停止阅读器
        ReaderOperations.stopReader();

        // 创建标签报告监听器
        readLatch = new CountDownLatch(1);
        rssiListener = new RssiListener(Target_EPC, readLatch);
        READER.setTagReportListener(rssiListener);

        // 读写器支持的功率
        FeatureSet features = READER.queryFeatureSet();
        List<TxPowerTableEntry> listPtx= features.getTxPowers();
//        for(TxPowerTableEntry p : listPtx){
//            System.out.println(p.Dbm);
//        }

        // 采用二分查找
        minPtx = listPtx.getFirst().Dbm;
        maxPtx = listPtx.getLast().Dbm;
        double result = maxPtx;
        int left = 0;
        int right = listPtx.size()-1;
        int iteration = 1;

        // 扫频测试
        System.out.println("\n开始扫频测试...");
//        System.out.println("天线端口: " + targetAntenna);
        System.out.println("功率测试范围: " + minPtx + "dBm 到 " + maxPtx + "dBm");
        System.out.println("==========================================");

        // 首先检查最大功率是否能检测到标签
        double maxRssi = get_Rssi_at_now_Ptx(maxPtx);
        if (!Double.isNaN(maxRssi)) {
            PTX = maxPtx;
            RSSI = maxRssi;
            BPI = PTX + RSSI;
        } else {
            System.out.println("错误: 在最大功率 " + maxPtx + "dBm 下未检测到标签！");
            System.out.println("请检查: 1. 标签距离 2. 天线连接 3. 标签是否正常工作");
//            return result;
            return;
        }

        // 二分查找主循环
        while (right > left) {
            System.out.println("\n--- 第 " + iteration + " 轮查找 ---");
            System.out.printf("搜索区间: [%.2f, %.2f] dBm%n", listPtx.get(left).Dbm, listPtx.get(right).Dbm);

            int mid = (left + right)/2;
            double power = listPtx.get(mid).Dbm;
            System.out.printf("测试功率: %.2f dBm%n", power);

            // 测试中间功率
            double nowRssi = get_Rssi_at_now_Ptx(power);

            if (!Double.isNaN(nowRssi)) {
                // 中间功率能检测到，向左搜索（更小功率）
                right = mid;
                PTX = power;
                RSSI = nowRssi;
                BPI = PTX + RSSI;
                System.out.println("√ 检测到标签，向左缩小搜索区间");
            } else {
                // 中间功率不能检测到，向右搜索（更大功率）
                left = mid+1;
                System.out.println("× 未检测到标签，向右缩小搜索区间");
            }

            iteration++;
        }

        // 停止阅读器
        ReaderOperations.stopReader();

//        result = listPtx.get(right).Dbm;
//        return result;
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
        settings.setAntennas(antennas);
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
