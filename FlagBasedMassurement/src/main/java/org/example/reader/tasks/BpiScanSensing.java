package org.example.reader.tasks;

import com.impinj.octane.*;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import org.apache.commons.lang3.tuple.Pair;
import org.example.reader.ReaderOperations;
import org.example.util.math.MathUtils;
import org.example.util.math.Point;

import org.example.util.math.plot.Curve;
import org.example.util.math.plot.LineChart;
import org.example.util.math.plot.PolynomialCurve;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BpiScanSensing {
    public static ImpinjReader READER = ReaderOperations.READER;
    public static String Target_EPC = null;
    public static double minPtx = 0.0; // 最小输出功率
    public static double maxPtx = 30.0; // 最大输出功率
    public static int numPtx = 91;
    public static double minOverlapRatio = 1;
    public static double minOverlapWidth = 0;
    public static Map<String, List<Point>> References;
    public static boolean Record_Scan_Data = false;
    public static String Tag_Feature = "";
    public static Path DATA_DIR = Path.of("data");
    public static Path reference_DATA_DIR = DATA_DIR.resolve("BPI_Reference");
    public static Path scan_DATA_DIR = DATA_DIR.resolve("BPI_Scan");
//    static String dateMsg ="";
//    static Path now_DATA_DIR = null;
    static Path feature_DATA_DIR = null;
    public static Path distance_DIR = null;
    //    static ArrayList<String> received_data = new ArrayList<>();
//    public static TagDataCache dataCache = new TagDataCache();
//    static {
//        dateMsg = LocalDate.now().toString();
//        now_DATA_DIR = scan_DATA_DIR.resolve("data-"+dateMsg);
////        feature_DATA_DIR = reference_DATA_DIR.resolve("tag-features");
//    }

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
        System.out.println("=== 根据BPI检测环境参数 ===");
        Path now_data_path = null;
        try {
            Config config = null;
            config = ConfigFactory.load("config.conf");
//            Target_EPC = config.hasPath("target_epc")
//                    ? config.getString("target_epc")
//                    : null;
            String feature_DIR = config.hasPath("feature_dir")
                    ? config.getString("feature_dir")
                    : "tag-features";
            feature_DATA_DIR = reference_DATA_DIR.resolve(feature_DIR);

            String distance = config.hasPath("reference_distance")
                    ? config.getString("reference_distance")
                    : "unknown_distance";
            distance_DIR = feature_DATA_DIR.resolve(distance);

            Record_Scan_Data = config.hasPath("record_scan_data")
                    && config.getBoolean("record_scan_data");

            Tag_Feature = config.hasPath("tag_feature")
                    ? config.getString("tag_feature")
                    : null;
            LocalDateTime time = LocalDateTime.now();
            DateTimeFormatter formatter1 = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");
            String timeStr1 = time.format(formatter1);
            if (Tag_Feature == null) {
                now_data_path = scan_DATA_DIR.resolve(feature_DIR).resolve(timeStr1 + ".json");
            } else {
                now_data_path = scan_DATA_DIR.resolve(feature_DIR).resolve(Tag_Feature).resolve(timeStr1 + ".json");
            }

        } catch (ConfigException.IO ec){
            System.out.println("无法找到 config.conf 文件");
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
            ex.printStackTrace(System.out);
        }

//        BpiTest.Target_EPC = Target_EPC;
        BpiTest.run();

        if(Record_Scan_Data && now_data_path != null){
            System.out.println("=== 记录BPI扫描值 ===");
            try {
                Point.savePointsToJsonFile(BpiTest.bpiList, now_data_path);
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
                ex.printStackTrace(System.out);
            }
        }

        System.out.println("=== 根据Ptx-BPI图像形状判断环境参数 ===");
        try {
            minPtx = BpiTest.minPtx;
            maxPtx = BpiTest.maxPtx;
            numPtx = BpiTest.numPtx;

            References = Point.loadPointsMapFromJsonFiles(distance_DIR);
            References.replaceAll((key,value)->Curve.remove_Y_NaN(value));
            List<Point> bpi_for_each_ptx = Curve.remove_Y_NaN(BpiTest.bpiList);
            minOverlapRatio = 0.95;
//            minOverlapWidth = minOverlapRatio * (bpi_for_each_ptx.getLast().x() - bpi_for_each_ptx.getFirst().x());

            System.out.println("========================================================");
            System.out.println("使用原始曲线：");
            judgeTask(bpi_for_each_ptx);

            System.out.println("========================================================");
            System.out.println("使用平滑后的曲线，平滑窗口长度为 5：");
            References.replaceAll((key,value)->Curve.smoothCurve(value,5));
            bpi_for_each_ptx = Curve.smoothCurve(bpi_for_each_ptx,5);
            judgeTask(bpi_for_each_ptx);

//            // 不同点的偏差的方差
//            Map<String, Double> variances = get_Map_Variance_of_Error(
//                    bpi_for_each_ptx,
//                    Interval_Start,
//                    Interval);
//            String tag_feature = "do not know!!!";
//            double min_variance = Double.POSITIVE_INFINITY;
//            for(Map.Entry<String, Double> entry : variances.entrySet()){
//                if(entry.getValue() < min_variance){
//                    min_variance = entry.getValue();
//                    tag_feature = entry.getKey();
//                }
//            }

//            System.out.println("=== 估计状态为：" + tag_feature + " ===");
        } catch (ConfigException.IO ec){
            System.out.println("无法找到 config.conf 文件");
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
            ex.printStackTrace(System.out);
        }
    }

    public static void judgeTask(List<Point> bpi_for_each_ptx){
        // 折线图最小均方误差
        Pair<String, Double> linechart_mse_tag_feature1 = get_LineChart_Min_MSE(bpi_for_each_ptx, 1);
        Pair<String, Double> linechart_mse_tag_feature2 = get_LineChart_Min_MSE(bpi_for_each_ptx, 2);
        Pair<String, Double> linechart_mse_tag_feature3 = get_LineChart_Min_MSE(bpi_for_each_ptx, 3);
        // 折线图最小均方误差（粗搜索 + 精搜索）
        Pair<String, Double> linechart_coarse_fine_mse_tag_feature1 = get_LineChart_Coarse_Fine_Min_MSE(bpi_for_each_ptx, 1);
        Pair<String, Double> linechart_coarse_fine_mse_tag_feature2 = get_LineChart_Coarse_Fine_Min_MSE(bpi_for_each_ptx, 2);
        Pair<String, Double> linechart_coarse_fine_mse_tag_feature3 = get_LineChart_Coarse_Fine_Min_MSE(bpi_for_each_ptx, 3);
        // 拟合一次多项式的最小均方误差
        Pair<String, Double> polynomial1_mse_tag_feature1 = get_PolynomialCurve_Min_MSE(bpi_for_each_ptx, 1, 1);
        Pair<String, Double> polynomial1_mse_tag_feature2 = get_PolynomialCurve_Min_MSE(bpi_for_each_ptx, 1, 2);
        Pair<String, Double> polynomial1_mse_tag_feature3 = get_PolynomialCurve_Min_MSE(bpi_for_each_ptx, 1, 3);
        // 拟合二次多项式的最小均方误差
        Pair<String, Double> polynomial2_mse_tag_feature1 = get_PolynomialCurve_Min_MSE(bpi_for_each_ptx, 2, 1);
        Pair<String, Double> polynomial2_mse_tag_feature2 = get_PolynomialCurve_Min_MSE(bpi_for_each_ptx, 2, 2);
        Pair<String, Double> polynomial2_mse_tag_feature3 = get_PolynomialCurve_Min_MSE(bpi_for_each_ptx, 2, 3);
        // 拟合三次多项式的最小均方误差
        Pair<String, Double> polynomial3_mse_tag_feature1 = get_PolynomialCurve_Min_MSE(bpi_for_each_ptx, 3, 1);
        Pair<String, Double> polynomial3_mse_tag_feature2 = get_PolynomialCurve_Min_MSE(bpi_for_each_ptx, 3, 2);
        Pair<String, Double> polynomial3_mse_tag_feature3 = get_PolynomialCurve_Min_MSE(bpi_for_each_ptx, 3, 3);
        // 一次多项式的拟合参数的偏差
        String polynomial1_param_tag_feature = get_1Degree_PolynomialCurve_Min_Parameter_Error(bpi_for_each_ptx);
        // 二次多项式的拟合参数的偏差
        String polynomial2_param_tag_feature = get_2Degree_PolynomialCurve_Min_Parameter_Error(bpi_for_each_ptx);

        System.out.println("=== 折线图最小均方误差，每 1 个点采一次样，估计状态为：" + linechart_mse_tag_feature1.getLeft() + "，此时重叠宽度为：" + linechart_mse_tag_feature1.getRight() + " ===");
        System.out.println("=== 折线图最小均方误差，每 2 个点采一次样，估计状态为：" + linechart_mse_tag_feature2.getLeft() + "，此时重叠宽度为：" + linechart_mse_tag_feature2.getRight() + " ===");
        System.out.println("=== 折线图最小均方误差，每 3 个点采一次样，估计状态为：" + linechart_mse_tag_feature3.getLeft() + "，此时重叠宽度为：" + linechart_mse_tag_feature3.getRight() + " ===");
        System.out.println("=== 折线图最小均方误差（粗搜索 + 精搜索），每 1 个点采一次样，估计状态为：" + linechart_coarse_fine_mse_tag_feature1.getLeft() + "，此时重叠率为：" + linechart_coarse_fine_mse_tag_feature1.getRight() + " ===");
        System.out.println("=== 折线图最小均方误差（粗搜索 + 精搜索），每 2 个点采一次样，估计状态为：" + linechart_coarse_fine_mse_tag_feature2.getLeft() + "，此时重叠率为：" + linechart_coarse_fine_mse_tag_feature2.getRight() + " ===");
        System.out.println("=== 折线图最小均方误差（粗搜索 + 精搜索），每 3 个点采一次样，估计状态为：" + linechart_coarse_fine_mse_tag_feature3.getLeft() + "，此时重叠率为：" + linechart_coarse_fine_mse_tag_feature3.getRight() + " ===");
        System.out.println("=== 拟合一次多项式最小均方误差，每 1 个点采一次样，估计状态为：" + polynomial1_mse_tag_feature1.getLeft() + "，此时重叠宽度为：" + linechart_mse_tag_feature1.getRight() + " ===");
        System.out.println("=== 拟合一次多项式最小均方误差，每 2 个点采一次样，估计状态为：" + polynomial1_mse_tag_feature2.getLeft() + "，此时重叠宽度为：" + linechart_mse_tag_feature2.getRight() + " ===");
        System.out.println("=== 拟合一次多项式最小均方误差，每 3 个点采一次样，估计状态为：" + polynomial1_mse_tag_feature3.getLeft() + "，此时重叠宽度为：" + linechart_mse_tag_feature3.getRight() + " ===");
        System.out.println("=== 拟合二次多项式最小均方误差，每 1 个点采一次样，估计状态为：" + polynomial2_mse_tag_feature1.getLeft() + "，此时重叠宽度为：" + linechart_mse_tag_feature1.getRight() + " ===");
        System.out.println("=== 拟合二次多项式最小均方误差，每 2 个点采一次样，估计状态为：" + polynomial2_mse_tag_feature2.getLeft() + "，此时重叠宽度为：" + linechart_mse_tag_feature2.getRight() + " ===");
        System.out.println("=== 拟合二次多项式最小均方误差，每 3 个点采一次样，估计状态为：" + polynomial2_mse_tag_feature3.getLeft() + "，此时重叠宽度为：" + linechart_mse_tag_feature3.getRight() + " ===");
        System.out.println("=== 拟合三次多项式最小均方误差，每 1 个点采一次样，估计状态为：" + polynomial3_mse_tag_feature1.getLeft() + "，此时重叠宽度为：" + linechart_mse_tag_feature1.getRight() + " ===");
        System.out.println("=== 拟合三次多项式最小均方误差，每 2 个点采一次样，估计状态为：" + polynomial3_mse_tag_feature2.getLeft() + "，此时重叠宽度为：" + linechart_mse_tag_feature2.getRight() + " ===");
        System.out.println("=== 拟合三次多项式最小均方误差，每 3 个点采一次样，估计状态为：" + polynomial3_mse_tag_feature3.getLeft() + "，此时重叠宽度为：" + linechart_mse_tag_feature3.getRight() + " ===");
        System.out.println("=== 一次多项式的拟合参数偏差 估计状态为：" + polynomial1_param_tag_feature + " ===");
        System.out.println("=== 二次多项式的拟合参数偏差 估计状态为：" + polynomial2_param_tag_feature + " ===");

    }

    /**
     * 计算现实 BPI 序列和给定 BPI 参考集中每个序列的拟合 一次多项式 的拟合参数的偏差
     * 顶点式：y=ax+b，只看 a
     * @param points 现实 BPI 序列
     * @return 偏差最小的参考序列在 References 中的 key
     */
    public static String get_1Degree_PolynomialCurve_Min_Parameter_Error(List<Point> points){
        String result = "do not know!!!";
        double min_error = Double.POSITIVE_INFINITY;
        PolynomialCurve bpi_for_each_ptx = new PolynomialCurve(points, 1);
//        Map<String, Double> result = new HashMap<>(References.size());
        for(Map.Entry<String,List<Point>> entry : References.entrySet()){
            PolynomialCurve reference = new PolynomialCurve(entry.getValue(), 1);
            double now_error = bpi_for_each_ptx.xShift1DegreeParamError(reference);
            if(now_error < min_error){
                min_error = now_error;
                result = entry.getKey();
            }
//            result.put(entry.getKey(), now_error);
        }
        return result;
    }

    /**
     * 计算现实 BPI 序列和给定 BPI 参考集中每个序列的拟合 二次多项式 的拟合参数的偏差
     * 顶点式：y=a(x-h)^2+k，b=-2ah，c=ah^2+k，只看 a 和 k，即 a 和 c-(b^2/4a)
     * @param points 现实 BPI 序列
     * @return 偏差最小的参考序列在 References 中的 key
     */
    public static String get_2Degree_PolynomialCurve_Min_Parameter_Error(List<Point> points){
        String result = "do not know!!!";
        double min_error = Double.POSITIVE_INFINITY;
        PolynomialCurve bpi_for_each_ptx = new PolynomialCurve(points, 2);
//        Map<String, Double> result = new HashMap<>(References.size());
        for(Map.Entry<String,List<Point>> entry : References.entrySet()){
            PolynomialCurve reference = new PolynomialCurve(entry.getValue(), 2);
            double now_error = bpi_for_each_ptx.xShift2DegreeParamError(reference);
            if(now_error < min_error){
                min_error = now_error;
                result = entry.getKey();
            }
//            result.put(entry.getKey(), now_error);
        }
        return result;
    }

    /**
     * 计算现实 BPI 序列和给定 BPI 参考集中每个序列的拟合多项式的最小均方误差（对于每个序列，多次平移计算均方误差并取最小值）
     * @param points 现实 BPI 序列
     * @param sampleRatio 采样间隔是点间隔的几倍
     * @return 最小均方误差最小的参考序列在 References 中的 key
     */
    public static Pair<String, Double> get_PolynomialCurve_Min_MSE(List<Point> points, int degree, int sampleRatio){
        String result = "do not know!!!";
        double min_mse = Double.POSITIVE_INFINITY;
        double min_mse_overlap_length = 0;
        PolynomialCurve bpi_for_each_ptx = new PolynomialCurve(points, degree);
//        Map<String, Double> result = new HashMap<>(References.size());
        int deltaStepRatio = 5;
        double deltaStep = (maxPtx - minPtx) / ((numPtx-1) * deltaStepRatio);
        int xInterval = sampleRatio * deltaStepRatio; // 每两个点采一次样计算 MSE
        for(Map.Entry<String,List<Point>> entry : References.entrySet()){
            Pair<Double, Double> now_pair = bpi_for_each_ptx
                    .xShiftMSE(
                            new PolynomialCurve(Curve.remove_Y_NaN(entry.getValue()), degree),
                            deltaStep,
                            xInterval,
                            minOverlapRatio
                    );
            double now_mse = now_pair.getLeft();
            double now_mse_overlap_length = now_pair.getRight();
            if((now_mse < min_mse) || (now_mse == min_mse && now_mse_overlap_length > min_mse_overlap_length)){
                min_mse = now_mse;
                min_mse_overlap_length = now_mse_overlap_length;
                result = entry.getKey();
            }
//            result.put(entry.getKey(), now_mse);
        }
        return Pair.of(result, min_mse_overlap_length);
    }

    /**
     * 计算现实 BPI 序列和给定 BPI 参考集中每个序列的折线图的最小均方误差（对于每个序列，多次平移计算均方误差并取最小值）
     * @param points 现实 BPI 序列
     * @param sampleRatio 采样间隔是点间隔的几倍
     * @return 最小均方误差最小的参考序列在 References 中的 key，以及此时的 x 轴重叠宽度
     */
    public static Pair<String, Double> get_LineChart_Min_MSE(List<Point> points, int sampleRatio){
        String result = "do not know!!!";
        double min_mse = Double.POSITIVE_INFINITY;
        double min_mse_overlap_length = 0;
        LineChart bpi_for_each_ptx = new LineChart(points);
//        Map<String, Double> result = new HashMap<>(References.size());
        int deltaStepRatio = 5;
        double deltaStep = (maxPtx - minPtx) / ((numPtx-1) * deltaStepRatio);
        int xInterval = sampleRatio * deltaStepRatio; // 每两个点采一次样计算 MSE
        for(Map.Entry<String,List<Point>> entry : References.entrySet()){
            Pair<Double, Double> now_pair = bpi_for_each_ptx
                    .xShiftMSE(
                            new LineChart(Curve.remove_Y_NaN(entry.getValue())),
                            deltaStep,
                            xInterval,
                            minOverlapRatio
                    );
            double now_mse = now_pair.getLeft();
            double now_mse_overlap_length = now_pair.getRight();
            if((now_mse < min_mse) || (now_mse == min_mse && now_mse_overlap_length > min_mse_overlap_length)){
                min_mse = now_mse;
                min_mse_overlap_length = now_mse_overlap_length;
                result = entry.getKey();
            }
//            result.put(entry.getKey(), now_mse);
        }
        return Pair.of(result, min_mse_overlap_length);
    }
    /**
     * 计算现实 BPI 序列和给定 BPI 参考集中每个序列的折线图的最小均方误差（对于每个序列，多次平移计算均方误差并取最小值）
     * @param points 现实 BPI 序列
     * @param sampleRatio 采样间隔是点间隔的几倍
     * @return 最小均方误差最小的参考序列在 References 中的 key，以及此时的 x 轴重叠率
     */
    public static Pair<String, Double> get_LineChart_Coarse_Fine_Min_MSE(List<Point> points, int sampleRatio){
        String result = "do not know!!!";
        double min_mse = Double.POSITIVE_INFINITY;
        double min_mse_overlap_ratio = 0;
        LineChart bpi_for_each_ptx = new LineChart(points);
//        Map<String, Double> result = new HashMap<>(References.size());
        double coarseStep = (maxPtx - minPtx) / (numPtx-1) * 2;
        double fineSearchRange = coarseStep / 2;
        double xStep = (maxPtx - minPtx) / (numPtx-1) * sampleRatio;  // 每两个点采一次样计算 MSE
        for(Map.Entry<String,List<Point>> entry : References.entrySet()){
            Pair<Double, Double> now_pair = bpi_for_each_ptx
                    .matchTwoCurves(
                            new LineChart(Curve.remove_Y_NaN(entry.getValue())),
                            coarseStep,
                            fineSearchRange,
                            xStep,
                            minOverlapRatio
                    );
            double now_mse = now_pair.getLeft();
            double now_mse_overlap_ratio = now_pair.getRight();
            if((now_mse < min_mse) || (now_mse == min_mse && now_mse_overlap_ratio > min_mse_overlap_ratio)){
                min_mse = now_mse;
                min_mse_overlap_ratio = now_mse_overlap_ratio;
                result = entry.getKey();
            }
//            result.put(entry.getKey(), now_mse);
        }
        return Pair.of(result, min_mse_overlap_ratio);
    }

    /**
     * 计算现实BPI序列和给定BPI参考集的偏差的方差
     * @param points 折线图
     * @param start 将y轴等间距分割的起始位置
     * @param interval 将y轴等间距分割的间距
     */
    public static Map<String, Double> get_Map_Variance_of_Error(
            List<Point> points,
            double start,
            double interval){
        Map<String, Double> result = new HashMap<>(References.size());
        points = Curve.remove_Y_NaN(points);
        double pmaxY = points.stream().mapToDouble(Point::y).max().orElse(Double.NEGATIVE_INFINITY);
        double pminY = points.stream().mapToDouble(Point::y).min().orElse(Double.POSITIVE_INFINITY);
        Map<Double, List<Double>> points_interval = Curve.points_to_y_interval_xs(points, start, interval, false);
        for(Map.Entry<String, List<Point>> entry : References.entrySet()){
            List<Point> reference = Curve.remove_Y_NaN(entry.getValue());
            double maxY = Math.min(
                    reference.stream().mapToDouble(Point::y).max().orElse(Double.NEGATIVE_INFINITY),
                    pmaxY);
            double minY = Math.max(
                    reference.stream().mapToDouble(Point::y).min().orElse(Double.POSITIVE_INFINITY),
                    pminY);
            double variance = 0.0;
            if(maxY < minY){
                variance = Double.POSITIVE_INFINITY;
            } else {
                Map<Double, List<Double>> reference_interval = Curve.points_to_y_interval_xs(reference, start, interval, false);
                List<Double> errors = new ArrayList<>();
                double minY_interval = start + Math.ceil((minY - start) / interval) * interval;
                for (double nowy = minY_interval; nowy <= maxY; nowy += interval) {
                    List<Double> rxs = reference_interval.get(nowy);
                    List<Double> pxs = points_interval.get(nowy);
                    int i = 0;
                    while (i < rxs.size() && i < pxs.size()) {
                        errors.add(rxs.get(i) - pxs.get(i));
                        i++;
                    }
                    while (i < rxs.size()){
                        errors.add(rxs.get(i) - pxs.getLast());
                        i++;
                    }
                    while (i < pxs.size()){
                        errors.add(rxs.getLast() - pxs.get(i));
                        i++;
                    }
                }
                variance = MathUtils.calculateVariance(errors);
            }
            result.put(entry.getKey(), variance);
        }
        return result;
    }

    /**
     * 计算现实BPI序列和给定BPI序列的偏差的方差
     * @param reference 参考图
     * @param points 折线图
     * @param start 将y轴等间距分割的起始位置
     * @param interval 将y轴等间距分割的间距
     * @param reference_hasNaN reference点集中是否有 y=NaN 点
     * @param points_hasNaN points点集中是否有 y=NaN 点
     * @return 现实 BPI 序列和给定 BPI 序列的 x 轴偏差的方差
     */
    public static double get_Variance_of_Error(
            List<Point> reference,
            List<Point> points,
            double start,
            double interval,
            boolean reference_hasNaN,
            boolean points_hasNaN){
        if(reference_hasNaN){
            reference = Curve.remove_Y_NaN(reference);
        }
        if(points_hasNaN){
            points = Curve.remove_Y_NaN(points);
        }
        if(interval<0) interval = -interval;
        double maxY = Math.min(
                reference.stream().mapToDouble(Point::y).max().orElse(Double.NEGATIVE_INFINITY),
                points.stream().mapToDouble(Point::y).max().orElse(Double.NEGATIVE_INFINITY));
        double minY = Math.max(
                reference.stream().mapToDouble(Point::y).min().orElse(Double.POSITIVE_INFINITY),
                points.stream().mapToDouble(Point::y).min().orElse(Double.POSITIVE_INFINITY));
        if(maxY < minY) return Double.POSITIVE_INFINITY;
        Map<Double, List<Double>> reference_interval = Curve.points_to_y_interval_xs(reference, start, interval, false);
        Map<Double, List<Double>> points_interval = Curve.points_to_y_interval_xs(points, start, interval, false);
        List<Double> errors = new ArrayList<>();
        double minY_interval = start + Math.ceil((minY-start)/interval)*interval;
        for(double nowy=minY_interval; nowy<=maxY; nowy+=interval){
            List<Double> rxs = reference_interval.get(nowy);
            List<Double> pxs = points_interval.get(nowy);
            int i=0;
            while (i<rxs.size() && i<pxs.size()){
                errors.add(rxs.get(i) - pxs.get(i));
                i++;
            }
        }
        return MathUtils.calculateVariance(errors);
    }

}
