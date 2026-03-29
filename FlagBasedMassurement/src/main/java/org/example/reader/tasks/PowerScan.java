package org.example.reader.tasks;

import com.impinj.octane.*;
import org.example.reader.ReaderOperations;
import org.example.reader.listener.PowerScanListener;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Impinj读写器功率扫描程序（二分查找优化版）
 * 功能：通过单天线使用二分查找算法，快速寻找标签刚好被激活的最小输出功率
 */
public class PowerScan {
    public static ImpinjReader READER = ReaderOperations.READER;
    public static double PTX = 20.0;
    public static double minPtx = 0.0; // 最小输出功率
    public static double maxPtx = 30.0; // 最大输出功率
//    public static int targetAntenna = 1; // 目标天线端口
    public static CountDownLatch readLatch = null;

    public static void main(String[] args) {
        run();
    }

    public static void run() {
        System.out.println("=== Impinj读写器最小激活功率扫描程序 ===");
        try {
            PTX = find_min_activation_Ptx();
            System.out.println("=== 最低输出功率：" + PTX + " dBm ===");
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
    public static double find_min_activation_Ptx()
            throws OctaneSdkException, InterruptedException {

        // 先停止阅读器
        ReaderOperations.stopReader();

        // 创建标签报告监听器
        READER.setTagReportListener(new PowerScanListener());

        // 读写器支持的功率
        FeatureSet features = READER.queryFeatureSet();
        List<TxPowerTableEntry> listPtx= features.getTxPowers();
        for(TxPowerTableEntry p : listPtx){
            System.out.println(p.Dbm);
        }

//        // 只启用目标天线
//        Settings settings = READER.querySettings();
//        AntennaConfigGroup antennas = settings.getAntennas();
//        antennas.disableAll();
////        antennas.enableById(new short[]{ (short)targetAntenna });
//        AntennaConfig antennaConfig = antennas.getAntenna(targetAntenna);
//        antennaConfig.setEnabled(true);
//        antennaConfig.setIsMaxTxPower(false);
//        READER.applySettings(settings);

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
        boolean maxPowerDetected = testPowerAtLevel(maxPtx);
        if (!maxPowerDetected) {
            System.out.println("错误: 在最大功率 " + maxPtx + "dBm 下未检测到标签！");
            System.out.println("请检查: 1. 标签距离 2. 天线连接 3. 标签是否正常工作");
            return result;
        }

        // 二分查找主循环
        while (right > left) {
            System.out.println("\n--- 第 " + iteration + " 轮查找 ---");
            System.out.printf("搜索区间: [%.2f, %.2f] dBm%n", listPtx.get(left).Dbm, listPtx.get(right).Dbm);

            int mid = (left + right)/2;
            double power = listPtx.get(mid).Dbm;
            System.out.printf("测试功率: %.2f dBm%n", power);

            // 测试中间功率
            boolean detected = testPowerAtLevel(power);

            if (detected) {
                // 中间功率能检测到，向左搜索（更小功率）
                right = mid;
                System.out.println("√ 检测到标签，向左缩小搜索区间");
            } else {
                // 中间功率不能检测到，向右搜索（更大功率）
                left = mid+1;
                System.out.println("× 未检测到标签，向右扩大搜索区间");
            }

            iteration++;
        }

        // 停止阅读器
        ReaderOperations.stopReader();

        result = listPtx.get(right).Dbm;
        return result;
    }

    /**
     * 在指定功率级别测试标签检测
     * @param power 测试功率
     * @return 是否检测到标签
     */
    private static boolean testPowerAtLevel(double power)
            throws OctaneSdkException, InterruptedException {

        // 先停止阅读器
        ReaderOperations.stopReader();

        // 设置当前功率
        Settings settings = READER.querySettings();
        AntennaConfigGroup antennas = settings.getAntennas();
        for(AntennaConfig ac : antennas){
            if(ac.isEnabled()) {
                ac.setIsMaxTxPower(false);
                ac.setTxPowerinDbm(power);
            }
        }
        READER.applySettings(settings);

        // 读取标签
        readLatch = new CountDownLatch(1);
        ReaderOperations.startReader();

        // 等待读取完成（最多 500 毫秒）
        boolean tagDetected = readLatch.await(500, TimeUnit.MILLISECONDS);

        // 停止阅读器
        ReaderOperations.stopReader();

        if (tagDetected) {
            System.out.println("能读到标签");
        } else {
            System.out.println("不能读到标签");
        }

        return tagDetected;
    }
}
