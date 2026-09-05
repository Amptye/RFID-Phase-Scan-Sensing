package org.example.data;

/**
 * P_tx - RSSI = 2*路径损耗 + 标签消耗（就用 DPR 或 dpr 表示吧）。
 * 要求：读写器功率（P_tx）不变，距离不变（即“路径损耗”不变）。
 * 先随便测个基准值，以后测量取差值，即为“标签消耗”的变化量，可以用来感知阻抗变化
 */
public class PowerDifference {
    public static double REFER = Double.NaN;
    public double dpr = 0;

    public static double getDifference(double dpr){
        if(Double.isNaN(REFER)){
            REFER = dpr;
            return 0;
        }
        return dpr - REFER;
    }
}
