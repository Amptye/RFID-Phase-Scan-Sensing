package org.example.util;

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
}
