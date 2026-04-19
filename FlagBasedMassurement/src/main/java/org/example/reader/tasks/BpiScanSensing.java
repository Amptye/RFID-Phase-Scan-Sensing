package org.example.reader.tasks;

import com.impinj.octane.*;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import org.example.reader.ReaderOperations;
import org.example.reader.listener.BpiScanListener;
import org.example.util.MathUtils;
import org.example.util.Point;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class BpiScanSensing {
    public static ImpinjReader READER = ReaderOperations.READER;
    public static double minPtx = 0.0; // 最小输出功率
    public static double maxPtx = 30.0; // 最大输出功率
    //    public static int targetAntenna = 1; // 目标天线端口
    public static CountDownLatch readLatch = null;
    public static BpiScanListener bpiScanListener = null;
    public static double noListenedRssi = Double.NEGATIVE_INFINITY; // -200;
    public static String BPI_Tag_EPC = "";
    public static String Tag_Feature = "";
    public static Map<String, List<Point>> References;
    public static double Interval_Start = 0;
    public static double Interval = 0.25;
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

            Path now_data_path = null;

//            if (Tag_Feature == null) {
//                LocalTime time = LocalTime.now(); // 例如: 14:30:45.123
//                // 1. 转换为 "HH-mm-ss" 格式
//                DateTimeFormatter formatter1 = DateTimeFormatter.ofPattern("HH-mm-ss");
//                String timeStr1 = time.format(formatter1);
//                // 示例: "14-30-45"
//                now_data_path = now_DATA_DIR.resolve(timeStr1 + ".json");
//            } else {
//                now_data_path = DATA_DIR.resolve("tag-features").resolve(Tag_Feature + ".json");
//            }

//            Point.savePointsToJsonFile(get_BPI_for_each_Ptx(), now_data_path);

            References = Point.loadPointsMapFromJsonFiles(DATA_DIR.resolve("tag-features"));
//            // 先将 References 中折线图中的负无穷点替换为折线图对应位置纵坐标（两边的直接删除）
//            References.replaceAll((key,value)-> replace_Infinity(value));
//            for(Map.Entry<String, List<Point>> entry : References.entrySet()){
//                interpolateMiddleInfinity(entry.getValue());
//            }

            List<Point> bpi_for_each_ptx = get_BPI_for_each_Ptx();
            // 不同点的偏差的方差
            Map<String, Double> variances = get_Map_Variance_of_Error(
                    bpi_for_each_ptx,
                    Interval_Start,
                    Interval);
            String tag_feature = "do not know!!!";
            double min_variance = Double.POSITIVE_INFINITY;
            for(Map.Entry<String, Double> entry : variances.entrySet()){
                if(entry.getValue() < min_variance){
                    min_variance = entry.getValue();
                    tag_feature = entry.getKey();
                }
            }

            System.out.println("=== 已扫描各频率BPI ===");
            System.out.println("=== 估计状态为：" + tag_feature + " ===");
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
     * 计算现实BPI序列和给定BPI参考集的偏差方差
     * @param points 折线图
     * @param start 将y轴等间距分割的起始位置
     * @param interval 将y轴等间距分割的间距
     */
    public static Map<String, Double> get_Map_Variance_of_Error(
            List<Point> points,
            double start,
            double interval){
        Map<String, Double> result = new HashMap<>(References.size());
        points = remove_Infinity(points);
        double pmaxY = points.stream().mapToDouble(Point::y).max().orElse(Double.NEGATIVE_INFINITY);
        double pminY = points.stream().mapToDouble(Point::y).min().orElse(Double.POSITIVE_INFINITY);
        Map<Double, List<Double>> points_interval = points_to_y_interval_xs(points, start, interval, false);
        for(Map.Entry<String, List<Point>> entry : References.entrySet()){
            List<Point> reference = remove_Infinity(entry.getValue());
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
                Map<Double, List<Double>> reference_interval = points_to_y_interval_xs(reference, start, interval, false);
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
     * 计算现实BPI序列和给定BPI序列的偏差方差
     * @param reference 参考图
     * @param points 折线图
     * @param start 将y轴等间距分割的起始位置
     * @param interval 将y轴等间距分割的间距
     * @param reference_hasInfinity reference点集中是否有无穷点
     * @param points_hasInfinity points点集中是否有无穷点
     */
    public static double get_Variance_of_Error(
            List<Point> reference,
            List<Point> points,
            double start,
            double interval,
            boolean reference_hasInfinity,
            boolean points_hasInfinity){
        if(reference_hasInfinity){
            reference = remove_Infinity(reference);
        }
        if(points_hasInfinity){
            points = remove_Infinity(points);
        }
        if(interval<0) interval = -interval;
        double maxY = Math.min(
                reference.stream().mapToDouble(Point::y).max().orElse(Double.NEGATIVE_INFINITY),
                points.stream().mapToDouble(Point::y).max().orElse(Double.NEGATIVE_INFINITY));
        double minY = Math.max(
                reference.stream().mapToDouble(Point::y).min().orElse(Double.POSITIVE_INFINITY),
                points.stream().mapToDouble(Point::y).min().orElse(Double.POSITIVE_INFINITY));
        if(maxY < minY) return Double.POSITIVE_INFINITY;
        Map<Double, List<Double>> reference_interval = points_to_y_interval_xs(reference, start, interval, false);
        Map<Double, List<Double>> points_interval = points_to_y_interval_xs(points, start, interval, false);
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

    /**
     * 去掉所有负无穷点，保留其他
     * @param points 原始点列表
     * @return 处理后的点列表
     */
    public static List<Point> remove_Infinity(List<Point> points) {
        return points.stream()
                .filter(p -> (!Double.isNaN(p.y())) && Double.isFinite(p.y()))
                .collect(Collectors.toList());
    }

    /**
     * 将不在两边的负无穷点替换为插值纵坐标
     * 假设点列表已按 x 升序排序。
     * 1. 保留两端的负无穷点
     * 2. 对中间的负无穷点进行线性插值
     * @param points 原始点列表
     * @return 处理后的点列表
     */
    public static List<Point> interpolate_Middle_Infinity(List<Point> points) {
        int length = points.size();
        List<Integer> finits = new ArrayList<>();
        List<Point> result = new ArrayList<>(points);
        for(int i=0; i<length; ++i){
            Point p = points.get(i);
            if((!Double.isNaN(p.y())) && Double.isFinite(p.y())){
               finits.add(i);
            }
        }
        int finits_num = finits.size();
        if(finits_num < 2) return result;

        for(int finits_index=0; finits_index<finits_num; ++finits_index){
            int current = finits.get(finits_index);
            int next = finits.get(finits_index+1);
            Point cp = result.get(current);
            Point np = result.get(next);
            int num = next - current;
            double interval = (np.y() - cp.y()) / num;
            double start = cp.y();
            for(int i=1; i<num; ++i){
                current++;
                double x = result.get(current).x();
                result.set(current, new Point(x, start+interval*i));
            }
        }
        return result;
    }

    /**
     * 将负无穷点替换为插值纵坐标
     * 假设点列表已按 x 升序排序。
     * 1. 两边的替换为最外侧有效值
     * 2. 中间的根据折线图插值
     * 假设点列表已按 x 升序排序。
     * @param points 原始点列表
     * @return 处理后的点列表
     */
    public static List<Point> interpolate_All_Infinity(List<Point> points) {
        int length = points.size();
        List<Integer> finits = new ArrayList<>();
        List<Point> result = new ArrayList<>(points);
        for(int i=0; i<length; ++i){
            Point p = points.get(i);
            if((!Double.isNaN(p.y())) && Double.isFinite(p.y())){
                finits.add(i);
            }
        }
        int finits_num = finits.size();
        if(finits_num < 1){
            return result.stream().map(p -> new Point(p.x(), 0)).collect(Collectors.toList());
        } else {
            int first = finits.getFirst();
            int last = finits.getLast();
            for(int i=0; i<first; ++i){
                result.set(i, new Point(points.get(i).x(), points.get(first).y()));
            }
            for(int i=length-1; i>last; --i){
                result.set(i, new Point(points.get(i).x(), points.get(last).y()));
            }
        }

        for(int finits_index=0; finits_index<finits_num; ++finits_index){
            int current = finits.get(finits_index);
            int next = finits.get(finits_index+1);
            Point cp = result.get(current);
            Point np = result.get(next);
            int num = next - current;
            double interval = (np.y() - cp.y()) / num;
            double start = cp.y();
            for(int i=1; i<num; ++i){
                current++;
                double x = result.get(current).x();
                result.set(current, new Point(x, start+interval*i));
            }
        }
        return result;
    }

    /**
     * 去掉所有负无穷点，折线中间的负无穷点用折线图对应位置的点替换
     * 假设点列表已按 x 升序排序。
     * 1. 去掉两端的负无穷点
     * 2. 对中间的负无穷点进行线性插值
     * @param points 原始点列表
     * @return 处理后的点列表
     */
    public static List<Point> replace_Infinity(List<Point> points) {
        int length = points.size();
        List<Integer> finits = new ArrayList<>();
        List<Point> result = new ArrayList<>();
        for(int i=0; i<length; ++i){
            Point p = points.get(i);
            if((!Double.isNaN(p.y())) && Double.isFinite(p.y())){
                finits.add(i);
            }
        }
        int finits_num = finits.size();
        if(finits_num < 1) return result;

        Point first = points.get(finits.getFirst());
        result.add(new Point(first.x(), first.y()));
        for(int finits_index=0; finits_index<finits_num; ++finits_index){
            int current = finits.get(finits_index);
            int next = finits.get(finits_index+1);
            Point cp = points.get(current);
            Point np = points.get(next);
            int num = next - current;
            double interval = (np.y() - cp.y()) / num;
            double start = cp.y();
            for(int i=1; i<num; ++i){
                current++;
                double x = points.get(current).x();
                result.add(new Point(x, start+interval*i));
            }
            result.add(new Point(np.x(), np.y()));
        }
        return result;
    }

    /**
     * 将折线图转换成y轴等间距的点集
     * @param points 折线图
     * @param start 将y轴等间距分割的起始位置
     * @param interval 将y轴等间距分割的间距
     * @param hasInfinity points点集中是否有无穷点
     * @return 处理后的点集
     */
    public static List<Point> points_to_y_interval_points(
            List<Point> points,
            double start,
            double interval,
            boolean hasInfinity){
        if(hasInfinity){
            points = replace_Infinity(points);
        }
        if(interval<0) interval = -interval;
        List<Point> result = new ArrayList<>();
        for(int i=0; i<points.size()-1; ++i){
            Point current = points.get(i);
            Point next = points.get(i+1);
            double cx = current.x();
            double cy = current.y();
            double nx = next.x();
            double ny = next.y();
            double dx = nx - cx;
            double dy = ny - cy;
            double intervaly = (cy<ny)?interval:(-interval);
            double intervalx = intervaly/dy*dx;
            double firsty = start + Math.ceil((cy-start)/interval)*interval;
            double firstx = cx + (firsty-cy)/dy*dx;
            while(firstx <= nx){
                result.add(new Point(firstx, firsty));
                firsty += intervaly;
                firstx += intervalx;
            }
        }
        return result;
    }

    /**
     * 折线图与y轴分隔线的交点集合
     * @param points 折线图
     * @param start 将y轴等间距分割的起始位置
     * @param interval 将y轴等间距分割的间距
     * @param hasInfinity points点集中是否有无穷点
     * @return 每个y值对应的x值集合
     */
    public static Map<Double, List<Double>> points_to_y_interval_xs(
            List<Point> points,
            double start,
            double interval,
            boolean hasInfinity){
        if(hasInfinity){
            points = replace_Infinity(points);
        }
        if(interval<0) interval = -interval;
        Map<Double, List<Double>> result = new HashMap<>();
        double maxy = points.stream().mapToDouble(Point::y).max().orElse(Double.NEGATIVE_INFINITY);
        double miny = points.stream().mapToDouble(Point::y).min().orElse(Double.POSITIVE_INFINITY);
        if(maxy < miny) return result;
        double miny_interval = start + Math.ceil((miny-start)/interval)*interval;
        for(double nowy=miny_interval; nowy<=maxy; nowy+=interval){
            result.put(nowy, new ArrayList<>());
        }
        for(int i=0; i<points.size()-1; ++i){
            Point current = points.get(i);
            Point next = points.get(i+1);
            double cx = current.x();
            double cy = current.y();
            double nx = next.x();
            double ny = next.y();
            double dx = nx - cx;
            double dy = ny - cy;
            double intervaly = (cy<ny)?interval:(-interval);
            double intervalx = intervaly/dy*dx;
            double firsty = start + Math.ceil((cy-start)/interval)*interval;
            double firstx = cx + (firsty-cy)/dy*dx;
            while(firstx < nx){
                result.get(firsty).add(firstx);
                firsty += intervaly;
                firstx += intervalx;
            }
        }
        Point last = points.getLast();
        if((last.y()-start)%interval==0){
            result.get(last.y()).add(last.x());
        }
        return result;
    }

    /**
     * 线性插值：在给定点列表折线图中，根据 x 值插值得到 y 值。
     * 假设点列表已按 x 升序排序。
     * 假设不包含无穷点
     * @param points 点列表
     * @param x 目标 x 坐标
     * @return 插值得到的 y 值
     */
    private static double interpolateY(List<Point> points, double x) {
        if (points == null || points.isEmpty()) {
            throw new IllegalArgumentException("点列表不能为空");
        }

        // 处理边界情况
        if (x <= points.getFirst().x()) {
            return points.getFirst().y();
        }
        if (x >= points.getLast().x()) {
            return points.getLast().y();
        }

        // 查找 x 所在的区间并进行线性插值
        for (int i = 0; i < points.size() - 1; i++) {
            Point p1 = points.get(i);
            Point p2 = points.get(i + 1);
            if (x >= p1.x() && x <= p2.x()) {
                double t = (x - p1.x()) / (p2.x() - p1.x());
                return p1.y() + t * (p2.y() - p1.y());
            }
        }

        // 理论上不会执行到这里
        return points.getLast().y();
    }
}
