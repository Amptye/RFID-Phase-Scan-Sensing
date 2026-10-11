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
import org.example.util.text.MyStringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class PhaseScanSensing {
    public static ImpinjReader READER = ReaderOperations.READER;
    public static double detect_frequency = 0;
    public static double minPtx = 0.0; // 最小输出功率
    public static double maxPtx = 30.0; // 最大输出功率
    public static int numPtx = 91;
    public static double stepPtx = 0.25;
    public static boolean overlapBase = false;
    public static double minOverlapRatio = 0.95;
    public static double minOverlapWidth = 0;
    public static List<Pair<String, List<Point>>> References;
    public static List<Point> phase_for_each_ptx;
    public static boolean feature_lineSet = false;
    public static boolean Record_Scan_Data = false;
    public static boolean Overwrite_Reference = false;
    public static String Tag_Feature = "";
    public static Path DATA_DIR = Path.of("data");
    public static Path scan_DATA_DIR = DATA_DIR.resolve("Phase_Scan");
    public static String dateMsg = LocalDate.now().toString();

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
        System.out.println("=== 根据Phase检测环境参数 ===");
        Path scan_data_frequency_Parent_Dir = null;
        Path scan_data_file_self = null;
        Path reference_data_file_self = null;
        Path reference_feature_parent_path = null;
        try {
            Config config = null;
            config = ConfigFactory.load("config.conf");

            detect_frequency = config.hasPath("detect_frequency")
                    ? config.getDouble("detect_frequency")
                    : 902.75;

            overlapBase = config.hasPath("base_this_or_short")
                    && config.getBoolean("base_this_or_short");

            minOverlapRatio = config.hasPath("min_overlap_ratio")
                    ? config.getDouble("min_overlap_ratio")
                    : 0.95;

            feature_lineSet = config.hasPath("feature_lineSet")
                    && config.getBoolean("feature_lineSet");

            Record_Scan_Data = config.hasPath("record_scan_data")
                    && config.getBoolean("record_scan_data");

            Overwrite_Reference = config.hasPath("overwrite_old_reference")
                    && config.getBoolean("overwrite_old_reference");

            Tag_Feature = config.hasPath("tag_feature")
                    ? config.getString("tag_feature")
                    : null;

            scan_data_frequency_Parent_Dir = getScan_Data_File_Dir(config);
            scan_data_file_self = getScan_Data_File_Self(config);
            reference_data_file_self = PhaseReference.getReference_Data_File_Self(config);
            reference_feature_parent_path = PhaseReference.getReference_Data_File_Dir(config);

        } catch (ConfigException.IO ec){
            System.out.println("无法找到 config.conf 文件");
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
            ex.printStackTrace(System.out);
        }

//        PhaseTest.Target_EPC = Target_EPC;
        PhaseTest.run();
        if(!PhaseTest.read_at_maxPtx){
            System.out.println("=== 在最大功率下未检测到标签！未获取到Phase扫描值！无法判断环境参数！ ===");
            return;
        }

        if(Record_Scan_Data && scan_data_frequency_Parent_Dir != null && scan_data_file_self != null){
            System.out.println("=== 记录Phase扫描值 ===");
            try {
                for (Map.Entry<Double, List<Point>> entry : PhaseTest.freq_PhaseList.entrySet()) {
                    double freq = entry.getKey();
                    List<Point> deltaPhaseList = entry.getValue();
                    Point.savePointsToJsonFile(deltaPhaseList,
                            scan_data_frequency_Parent_Dir.resolve(String.valueOf(freq)).resolve(scan_data_file_self));
                }
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
                ex.printStackTrace(System.out);
            }
        }

        if(Overwrite_Reference && reference_feature_parent_path != null && reference_data_file_self != null){
            Path overwrite_reference_path = null;
            boolean overwrite = false;
            for (Map.Entry<Double, List<Point>> entry : PhaseTest.freq_PhaseList.entrySet()) {
                double freq = entry.getKey();
                List<Point> deltaPhaseList = entry.getValue();
                if (!feature_lineSet) {
                    overwrite_reference_path = reference_feature_parent_path.resolve(String.valueOf(freq)).resolve(reference_data_file_self);
                    try {
                        if (Curve.calculateWidth(deltaPhaseList)
                                > Curve.calculateWidth(Point.loadPointsFromJsonFile(overwrite_reference_path))) {
                            overwrite = true;
                        }
                    } catch (IOException e) {
                        System.out.println(e.getMessage());
                    }
                    System.out.println("=== 覆盖Phase参考值：" + Tag_Feature + " ===");
                } else {
                    overwrite_reference_path = reference_feature_parent_path.resolve(String.valueOf(freq)).resolve(reference_data_file_self);
                    System.out.println("=== 添加Phase参考值：" + Tag_Feature + " ===");
                }
                try {
                    if (overwrite)
                        Point.savePointsToJsonFile(deltaPhaseList, overwrite_reference_path);
                    return;
                } catch (Exception ex) {
                    System.out.println(ex.getMessage());
                    ex.printStackTrace(System.out);
                }
            }
        }

        System.out.println("=== 根据Ptx-Phase图像形状判断环境参数 ===");
        if(reference_feature_parent_path == null){
            System.out.println("标签数据父目录 reference_feature_parent_path 为空！！！");
            return;
        }
        try {
            minPtx = PhaseTest.minPtx;
            maxPtx = PhaseTest.maxPtx;
            numPtx = PhaseTest.numPtx;
            stepPtx = (maxPtx - minPtx) / (numPtx - 1);

            if(!feature_lineSet) {
                References = Point.loadPointsPairsFromJsonFiles(reference_feature_parent_path);
            } else {
                References = new ArrayList<>();
                try (var stream = Files.list(reference_feature_parent_path)) {
                    stream.filter(Files::isDirectory)
                            .forEach(feature -> {
                                try {
                                    // 读取该 feature 目录中所有JSON文件
                                    List<List<Point>> curves = Point.loadPointsListFromJsonFiles(feature);

                                    // 放入 References
                                    curves.forEach(line ->
                                            References.add(Pair.of(feature.getFileName().toString(), line)));

                                } catch (Exception e) {
                                    System.err.println("读取文件失败: " + feature + ", 错误: " + e.getMessage());
                                }
                            });
                }
            }
            References.replaceAll(value ->
                    Pair.of(value.getLeft(), Curve.remove_Y_NaN(value.getRight())));
            phase_for_each_ptx = Curve.remove_Y_NaN(PhaseTest.freq_PhaseList.get(detect_frequency));
            System.out.println("扫描值宽度：" + Curve.calculateWidth(phase_for_each_ptx));
//            minOverlapWidth = minOverlapRatio * Curve.calculateWidth(phase_for_each_ptx);

            Pair<Double, Integer> one_result = null;
            double sum_feature = 0;
            int count = 0;
            System.out.println("=======================================================================================================");
            System.out.println("使用原始曲线：");
            one_result = judgeTask();
            sum_feature += one_result.getKey() * one_result.getValue();
            count += one_result.getValue();

//            int windowSize = 5;
            double windowWidth = stepPtx * 2;
            int precision = 10;
            double smoothness = 0;

            for(int ss = 1; ss <= 5; ++ss) {
                for (int ww = 1; ww <= 4; ++ww) {
                    windowWidth = stepPtx * ww;
                    smoothness = stepPtx / ss;
                    System.out.println("=======================================================================================================");
                    System.out.println("使用平滑后的曲线，平滑窗口长度为 " + windowWidth + "，平滑度为 " + smoothness + "：");
                    smoothCurves(windowWidth, precision, smoothness);
                    one_result = judgeTask();
                    sum_feature += one_result.getKey() * one_result.getValue();
                    count += one_result.getValue();
                }
            }
            double mean_feature = 0;
            if(count != 0) mean_feature = sum_feature / count;
            System.out.println("====== 综合所有后，平均状态为（不包含参数偏差的估计）：" + mean_feature + "ml ======");


//            // 不同点的偏差的方差
//            Map<String, Double> variances = get_Map_Variance_of_Error(
//                    phase_for_each_ptx,
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

    /**
     * 使用原曲线，或不同的平滑窗口和平滑度，使用各种算法判断环境状态
     * @return Pair{平均状态, 合法状态的数量}
     */
    public static Pair<Double, Integer> judgeTask(){
        Curve now_phase = null;
        List<Pair<String, Curve>> references = new ArrayList<>(References.size());
        int maxSmapleRatio = 3;
        Pair<String, Double> feature = null;
        double sum_feature = 0;
        int count = 0;
        List<Double> nums = new ArrayList<>();
        // 折线图最小均方误差
        try {
            now_phase = new LineChart(phase_for_each_ptx);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            System.out.println("Phase扫描值序列出了问题！");
        }
        for(Map.Entry<String,List<Point>> entry : References){
            try {
                references.add(Pair.of(entry.getKey(), new LineChart(entry.getValue())));
            } catch (Exception e) {
                System.out.println(e.getMessage());
                System.out.println(entry.getKey() + "的参考值序列出了问题！");
            }
        }
        if(now_phase != null) {
            // 粗搜索
            for (int sampleRatio = 1; sampleRatio <= maxSmapleRatio; sampleRatio++) {
                feature = get_Curve_Min_MSE(now_phase, references, sampleRatio, false);
                nums = MyStringUtils.getNum_from_String(feature.getKey());
                if(!nums.isEmpty()){
                    sum_feature += nums.getFirst();
                    count++;
                }
                System.out.println("=== 折线图最小均方误差，每 " + sampleRatio + " 个点采一次样，估计状态为：" + feature.getLeft() + "，此时重叠宽度为：" + feature.getRight() + " ===");
            }
            // 粗搜索 + 精搜索
            for (int sampleRatio = 1; sampleRatio <= maxSmapleRatio; sampleRatio++) {
                feature = get_Curve_Min_MSE(now_phase, references, sampleRatio, true);
                nums = MyStringUtils.getNum_from_String(feature.getKey());
                if(!nums.isEmpty()){
                    sum_feature += nums.getFirst();
                    count++;
                }
                System.out.println("=== 折线图最小均方误差（粗搜索 + 精搜索），每 " + sampleRatio + " 个点采一次样，估计状态为：" + feature.getLeft() + "，此时重叠率为：" + feature.getRight() + " ===");
            }
        }

        // 拟合多项式的最小均方误差
        for(int degree = 1; degree <=3; degree++){
            now_phase = null;
            references = new ArrayList<>(References.size());
            try {
                now_phase = new PolynomialCurve(phase_for_each_ptx, degree);
            } catch (Exception e) {
                System.out.println(e.getMessage());
                System.out.println("Phase扫描值序列出了问题！");
            }
            for(Map.Entry<String,List<Point>> entry : References){
                try {
                    references.add(Pair.of(entry.getKey(), new PolynomialCurve(entry.getValue(), degree)));
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                    System.out.println(entry.getKey() + "的参考值序列出了问题！");
                }
            }
            if(now_phase != null) {
                // 粗搜索
                for (int sampleRatio = 1; sampleRatio <= maxSmapleRatio; sampleRatio++) {
                    feature = get_Curve_Min_MSE(now_phase, references, sampleRatio, false);
                    nums = MyStringUtils.getNum_from_String(feature.getKey());
                    if(!nums.isEmpty()){
                        sum_feature += nums.getFirst();
                        count++;
                    }
                    System.out.println("=== 拟合 " + degree + " 次多项式最小均方误差，每 " + sampleRatio + " 个点采一次样，估计状态为：" + feature.getLeft() + "，此时重叠宽度为：" + feature.getRight() + " ===");
                }
                // 粗搜索 + 精搜索
                for (int sampleRatio = 1; sampleRatio <= maxSmapleRatio; sampleRatio++) {
                    feature = get_Curve_Min_MSE(now_phase, references, sampleRatio, true);
                    nums = MyStringUtils.getNum_from_String(feature.getKey());
                    if(!nums.isEmpty()){
                        sum_feature += nums.getFirst();
                        count++;
                    }
                    System.out.println("=== 拟合 " + degree + " 次多项式最小均方误差（粗搜索 + 精搜索），每 " + sampleRatio + " 个点采一次样，估计状态为：" + feature.getLeft() + "，此时重叠率为：" + feature.getRight() + " ===");
                }
            }
        }
        // 一次多项式的拟合参数的偏差
        String polynomial1_param_tag_feature = get_1Degree_PolynomialCurve_Min_Parameter_Error(phase_for_each_ptx);
        System.out.println("=== 一次多项式的拟合参数偏差 估计状态为：" + polynomial1_param_tag_feature + " ===");
        // 二次多项式的拟合参数的偏差
        String polynomial2_param_tag_feature = get_2Degree_PolynomialCurve_Min_Parameter_Error(phase_for_each_ptx);
        System.out.println("=== 二次多项式的拟合参数偏差 估计状态为：" + polynomial2_param_tag_feature + " ===");
        double mean_feature = 0;
        if(count != 0) mean_feature = sum_feature / count;
        System.out.println("====== 平均状态为（不包含参数偏差的估计）：" + mean_feature + "ml ======");
        return Pair.of(mean_feature, count);
    }

    /**
     * 平滑曲线数据（使用移动平均，即每个点的新 y 值设置为所处区间内的 y 值的平均值），平滑过程中取点建列表，不使用原列表的点
     * @param windowWidth 计算平均值的区间长度，处于两端时截断区间
     * @param precision 计算平均值的精度，计算平均值时将平滑区间分割成几份，理论上应该用 (y值积分 / 区间宽度) 作为平均值
     * @param smoothness 平滑度，平滑过程中取点建列表的 x 值间隔，如果为非正数，则用原列表内点的 x 值建列表
     */
    public static void smoothCurves(double windowWidth, int precision, double smoothness){
        References.replaceAll(value ->
                Pair.of(value.getLeft(), Curve.smoothCurve(value.getRight(), windowWidth, precision, smoothness)));
        phase_for_each_ptx = Curve.smoothCurve(phase_for_each_ptx, windowWidth, precision, smoothness);
    }

    /**
     * 计算现实 Phase 序列和给定 Phase 参考集中每个序列的拟合 一次多项式 的拟合参数的偏差
     * 顶点式：y=ax+b，只看 a
     * @param points 现实 Phase 序列
     * @return 偏差最小的参考序列在 References 中的 key
     */
    public static String get_1Degree_PolynomialCurve_Min_Parameter_Error(List<Point> points){
        String result = "do not know!!!";
        double min_error = Double.POSITIVE_INFINITY;
        PolynomialCurve phase_for_each_ptx = null;
        try {
            phase_for_each_ptx = new PolynomialCurve(points, 1);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            result = "Phase扫描值序列出了问题！";
            return result;
        }
        for (Map.Entry<String, List<Point>> entry : References) {
            try {
                PolynomialCurve reference = new PolynomialCurve(entry.getValue(), 1);
                double now_error = phase_for_each_ptx.xShift1DegreeParamError(reference);
                if (now_error < min_error) {
                    min_error = now_error;
                    result = entry.getKey();
                }
            } catch (Exception e) {
                System.out.println(e.getMessage());
                System.out.println(entry.getKey() + "的参考值序列出了问题！");
            }
        }
        return result;
    }

    /**
     * 计算现实 Phase 序列和给定 Phase 参考集中每个序列的拟合 二次多项式 的拟合参数的偏差
     * 顶点式：y=a(x-h)^2+k，b=-2ah，c=ah^2+k，只看 a 和 k，即 a 和 c-(b^2/4a)
     * @param points 现实 Phase 序列
     * @return 偏差最小的参考序列在 References 中的 key
     */
    public static String get_2Degree_PolynomialCurve_Min_Parameter_Error(List<Point> points){
        String result = "do not know!!!";
        double min_error = Double.POSITIVE_INFINITY;
        PolynomialCurve phase_for_each_ptx = null;
        try {
            phase_for_each_ptx = new PolynomialCurve(points, 2);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            result = "Phase扫描值序列出了问题！";
            return result;
        }
        for (Map.Entry<String, List<Point>> entry : References) {
            try {
                PolynomialCurve reference = new PolynomialCurve(entry.getValue(), 2);
                double now_error = phase_for_each_ptx.xShift2DegreeParamError(reference);
                if (now_error < min_error) {
                    min_error = now_error;
                    result = entry.getKey();
                }
            } catch (Exception e) {
                System.out.println(e.getMessage());
                System.out.println(entry.getKey() + "的参考值序列出了问题！");
            }
        }
        return result;
    }

    /**
     * 计算现实 Phase 序列和给定 Phase 参考集中每个序列的曲线的最小均方误差（对于每个序列，多次平移计算均方误差并取最小值）
     * @param now_Phase 现实 Phase 序列曲线
     * @param references 参考 Phase 序列曲线
     * @param sampleRatio 采样间隔是点间隔的几倍
     * @param coarse_fine 是否分步搜索，true 为先粗搜索后精搜索，false 为粗搜索
     * @return 最小均方误差最小的参考序列在 References 中的 key，以及此时的 x 轴重叠率（coarse_fine=true）或重叠宽度（coarse_fine=false）
     */
    public static Pair<String, Double> get_Curve_Min_MSE(Curve now_Phase,
                                                         List<Pair<String, Curve>> references,
                                                         int sampleRatio,
                                                         boolean coarse_fine){
        String result = "do not know!!!";
        double min_mse = Double.POSITIVE_INFINITY;
        double min_mse_overlap = 0;
        int deltaStepRatio = 5;
//        Map<String, Double> result = new HashMap<>(References.size());
        for(Map.Entry<String,Curve> entry : references){
            Pair<Double, Double> now_pair =
                    coarse_fine
                    ? now_Phase.matchTwoCurves(
                            entry.getValue(),
                            (maxPtx - minPtx) / (numPtx-1) * 2,
                            (maxPtx - minPtx) / (numPtx-1),
                            (maxPtx - minPtx) / (numPtx-1) * sampleRatio, // 每 sampleRatio 个点采一次样计算 MSE
                            minOverlapRatio,
                            overlapBase
                    )
                    : now_Phase.xShiftMSE(
                            entry.getValue(),
                            (maxPtx - minPtx) / ((numPtx-1) * deltaStepRatio),
                            sampleRatio * deltaStepRatio, // 每 sampleRatio 个点采一次样计算 MSE
                            minOverlapRatio,
                            overlapBase
                    );
            double now_mse = now_pair.getLeft();
            double now_mse_overlap_width = now_pair.getRight();
            if((now_mse < min_mse) || (now_mse == min_mse && now_mse_overlap_width > min_mse_overlap)){
                min_mse = now_mse;
                min_mse_overlap = now_mse_overlap_width;
                result = entry.getKey();
            }
        }
        return Pair.of(result, min_mse_overlap);
    }

    /**
     * 计算现实Phase序列和给定Phase参考集的偏差的方差
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
        for(Map.Entry<String, List<Point>> entry : References){
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
     * 计算现实Phase序列和给定Phase序列的偏差的方差
     * @param reference 参考图
     * @param points 折线图
     * @param start 将y轴等间距分割的起始位置
     * @param interval 将y轴等间距分割的间距
     * @param reference_hasNaN reference点集中是否有 y=NaN 点
     * @param points_hasNaN points点集中是否有 y=NaN 点
     * @return 现实 Phase 序列和给定 Phase 序列的 x 轴偏差的方差
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


    public static Path getScan_Data_File_Dir(Config config){
        String target_epc = config.hasPath("target_epc")
                ? config.getString("target_epc")
                : "unknown_epc";
        String feature_DIR = config.hasPath("feature_dir")
                ? config.getString("feature_dir")
                : "tag-features";

        return scan_DATA_DIR
                .resolve(target_epc)
                .resolve(feature_DIR);
    }

    public static Path getScan_Data_File_Self(Config config){
        Path file_path = null;

        String tag_feature = config.hasPath("tag_feature")
                ? config.getString("tag_feature")
                : null;

        LocalTime time = LocalTime.now(); // 例如: 14:30:45.123
        // 1. 转换为 "HH-mm-ss" 格式
        DateTimeFormatter formatter1 = DateTimeFormatter.ofPattern("HH-mm-ss");
        String timeStr1 = time.format(formatter1);
        // 示例: "14-30-45"
        if (tag_feature == null) {
            file_path = Path.of(
                    "data-"+dateMsg,
                    timeStr1 + ".json");
        } else {
            file_path = Path.of(
                    tag_feature,
                    "data-"+dateMsg,
                    timeStr1 + ".json");
        }
        return file_path;
    }

}
