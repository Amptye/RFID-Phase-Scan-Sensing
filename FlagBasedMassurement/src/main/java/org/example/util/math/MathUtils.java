package org.example.util.math;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.math3.fitting.PolynomialCurveFitter;
import org.apache.commons.math3.fitting.WeightedObservedPoints;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

public final class MathUtils {
    private MathUtils() { // 私有构造器禁止实例化
        throw new AssertionError("工具类不可实例化");
    }

    /**
     * 计算列表的方差
     * @param numbers 数字列表
     * @return 方差值
     */
    public static double calculateVariance(List<Double> numbers) {
        if (numbers == null || numbers.isEmpty()) {
            return 0;
        }

        int n = numbers.size();

        // 计算平均值
        double sum = 0.0;
        for (double num : numbers) {
            sum += num;
        }
        double mean = sum / n;

        // 计算方差
        double variance = 0.0;
        for (double num : numbers) {
            double diff = num - mean;
            variance += diff * diff;
        }
        variance = variance / n;

        return variance;
    }

    /**
     * 计算均方误差
     * @param aList 从头开始取值，直到结束或 bList 结束
     * @param bList 从 start 开始取值，直到结束或 aList 结束
     * @param start bList 从 start 开始取值
     * @param interval 每 interval 个元素取一次值，例如：interval=2 就是 aList 和 bList 都是每 2 个元素取一次值用来计算
     * @return 均方误差
     */
    public static double calculateMSE(List<Double> aList, List<Double> bList, int start, int interval){
        int l1 = aList.size(), l2 = bList.size();
        // 误差的平方和
        double ss = 0;
        // 采样个数
        int cnt = 0;
        for(int i=0; i<l1 && i+start<l2; i+=interval){
            double diff = aList.get(i) - bList.get(i+start);
            ss += diff * diff;
            cnt++;
        }
        if(cnt==0) return 0;
        return ss/cnt;
    }

    /**
     * 计算最小均方误差。
     * 平移短的 List，尝试所有重叠区间（无重叠 -> 全覆盖 -> 无重叠），计算均方误差，返回最小均方误差
     * @param interval 每 interval 个元素取一次值，例如：interval=2 就是 aList 和 bList 都是每 2 个元素取一次值用来计算
     * @param minOverlapNum 最小重叠点数（因为这里已经将曲线划分成许多点，且只传了 y 值）
     * @return Pair{最小均方误差, 此时的重叠长度}
     */
    public static Pair<Double, Integer> calculateMinMSE(List<Double> aList, List<Double> bList, int interval, int minOverlapNum){
        int l1 = aList.size(), l2 = bList.size();
        int minLength = Math.min(l1, l2);
        if(minOverlapNum > minLength) minOverlapNum = minLength;
        double minmse = Double.POSITIVE_INFINITY;
        double nowmse;
        // 重叠区间长度
        int now_overlap_length = 0;
        int minmse_overlap_length = 0;
        for(int i=l1-minOverlapNum; i>0; --i){
            now_overlap_length = Math.min(l2, l1-i);
            nowmse = calculateMSE(bList, aList, i, interval);
            if((nowmse < minmse) || (nowmse == minmse && now_overlap_length > minmse_overlap_length)){
                minmse = nowmse;
                minmse_overlap_length = now_overlap_length;
            }
        }
        for(int i=0; i<=l2-minOverlapNum; ++i){
            now_overlap_length = Math.min(l1, l2-i);
            nowmse = calculateMSE(aList, bList, i, interval);
            if((nowmse < minmse) || (nowmse == minmse && now_overlap_length > minmse_overlap_length)){
                minmse = nowmse;
                minmse_overlap_length = now_overlap_length;
            }
        }
        return Pair.of(minmse, minmse_overlap_length);
    }

    public static String hexToBinary(String hexString) {
        hexString = hexString.replace("\\s+","");
        // 移除可能的前缀
        hexString = hexString.replaceFirst("^0x", "").replaceFirst("^0X", "");

        // 使用 BigInteger 转换
        String binary = new BigInteger(hexString, 16).toString(2);

        // 补齐位数（每个十六进制字符对应4位二进制）
        int expectedLength = hexString.length() * 4;
        if (binary.length() < expectedLength) {
            binary = "0".repeat(expectedLength - binary.length()) + binary;
        }

        return binary;
    }

    public static int hexToDecimal(String hexString) {
        hexString = hexString.replace("\\s+","");
        // 移除可能的前缀
        hexString = hexString.replaceFirst("^0x", "").replaceFirst("^0X", "");

        // 转换为十进制整数
        return Integer.parseInt(hexString, 16);
    }

    public static int binaryToDecimal(String binaryString) {
        // 移除可能的空格
        binaryString = binaryString.replaceAll("\\s+", "");

        // 验证二进制格式
        if (!binaryString.matches("[01]+")) {
            throw new IllegalArgumentException("无效的二进制字符串: " + binaryString);
        }

        // 转换为十进制整数
        return Integer.parseInt(binaryString, 2);
    }

    /**
     * 拟合多项式
     * @param degree 多项式的最高次
     * @return 返回值: [c, b, a] -> y = a·x² + b·x + c
     */
    public static double[] fitPolynomial(List<Point> points, int degree) {
        WeightedObservedPoints obs = new WeightedObservedPoints();
        for (Point p : points) {
            obs.add(p.x(), p.y());   // 权重默认 1.0（采样点无明显可信度差异）
        }
        // degree 次多项式
        PolynomialCurveFitter fitter = PolynomialCurveFitter.create(degree);
        return fitter.fit(obs.toList());
    }
}
