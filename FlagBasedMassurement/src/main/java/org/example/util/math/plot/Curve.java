package org.example.util.math.plot;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.univariate.BrentOptimizer;
import org.apache.commons.math3.optim.univariate.SearchInterval;
import org.apache.commons.math3.optim.univariate.UnivariateObjectiveFunction;
import org.apache.commons.math3.optim.univariate.UnivariatePointValuePair;
import org.example.util.math.MathUtils;
import org.example.util.math.Point;

import java.util.*;
import java.util.stream.Collectors;

public abstract class Curve {
    public final List<Point> points;
    public double[] xArray;
    public double[] yArray;
    public UnivariateFunction function; // 插值后的连续函数

    public Curve(List<Point> points) {
        AssertCurve(points);
        this.points = new ArrayList<>(points);
        generateXYArray();
        this.function = null;
    }
    public Curve(Curve line) {
        this.points = new ArrayList<>(line.points);
        this.xArray = Arrays.copyOf(line.xArray, line.xArray.length);
        this.yArray = Arrays.copyOf(line.yArray, line.yArray.length);
        this.function = null;
    }
    public Curve(double[] xArray, double[] yArray) {
        AssertXYDoubles(xArray, yArray);
        int length = xArray.length;
        List<Point> points = new ArrayList<>(length);
        for(int i=0; i<length; ++i){
            points.add(new Point(xArray[i], yArray[i]));
        }
        AssertCurve(points);
        this.points = new ArrayList<>(points);
        this.xArray = Arrays.copyOf(xArray, xArray.length);
        this.yArray = Arrays.copyOf(yArray, yArray.length);
        this.function = null;
    }

    /**
     * 生成 xArray、yArray
     */
    public final void generateXYArray(){
        this.xArray = points.stream()
                .mapToDouble(Point::x)
                .toArray();
        this.yArray = points.stream()
                .mapToDouble(Point::y)
                .toArray();
    }

    /**
     * 生成曲线对应的函数
     */
    public abstract void generateFunction();

    /**
     * 拟合多项式
     * @param degree 多项式的最高次
     * @return 返回值: [c, b, a] -> y = a·x² + b·x + c
     */
    public double[] fitPolynomial(int degree) {
        return MathUtils.fitPolynomial(points, degree);
    }

    /**
     * 确保能构成曲线，否则丢出错误：
     * 1. 点列表不能为 null；
     * 2. 点列表长度不能小于 2；
     * 3. 所有点都不能为 null；
     * 4. 所有点的 x,y 都不能为 NaN；
     * 5. 所有点的 x 必须严格递增
     * @param points 原始点列表
     */
    public static void AssertCurve(List<Point> points){
        if(points == null){
            throw new IllegalArgumentException("点列表不能为 null");
        }
//        if(points == null) return;
        if(points.size() < 2){
            throw new IllegalArgumentException("至少要 2 个点，但只有 " + points.size() + " 个");
        }
        if(points.getFirst() == null){
            throw new IllegalArgumentException("第 0 个点为 null");
        }
        double lastX = points.getFirst().x();
        if(Double.isNaN(lastX)){
            throw new IllegalArgumentException("第 0 个点的 x 值为 NaN");
        }
        if(Double.isNaN(points.getFirst().y())){
            throw new IllegalArgumentException("第 0 个点的 y 值为 NaN");
        }
        Point nowP = null;
        double nowX = Double.NaN;
        for(int i=1; i<points.size(); ++i){
            nowP = points.get(i);
            if(nowP == null){
                throw new IllegalArgumentException("第 " + i + " 个点为 null");
            }
            nowX = nowP.x();
            if(Double.isNaN(nowX)){
                throw new IllegalArgumentException("第 " + i + " 个点的 x 值为 NaN");
            }
            if(Double.isNaN(nowP.y())){
                throw new IllegalArgumentException("第 " + i + " 个点的 y 值为 NaN");
            }
            if(nowX <= lastX){
                throw new IllegalArgumentException(
                        "所有点的 x 必须严格递增，但 x[" + (i-1) + "]=" + lastX
                                + " >= x[" + i + "]=" + nowX);
            }
        }
    }
    public final void AssertXYDoubles(double[] xArray, double[] yArray){
        if(xArray.length != yArray.length){
            throw new IllegalArgumentException("x 和 y 的数量不同，分别有 " + xArray.length + "、" + yArray.length + " 个");
        }
    }

    /**
     * 判断 x 是否在曲线范围内
     * @param x 接收判断的 x
     * @return 是否在范围内
     */
    public boolean isXinRange(double x){
        return x < points.getFirst().x() || x > points.getLast().x();
    }

    /**
     * 确保 x 在曲线范围内，否则丢出错误
     * @param x 接收判断的 x
     */
    public void AssertXinRange(double x){
        if (x < points.getFirst().x() || x > points.getLast().x()) {
            throw new IllegalArgumentException(
                    "x=" + x + " 超出了范围 [" + xMin() + ", " + xMax() + "]");
        }
    }

    /**
     * 根据 x 值插值得到 y 值，两端之外取端点的 y 值
     * @param x 目标 x 坐标
     * @return 插值得到的 y 值
     */
    public double getY(double x){
//        AssertXinRange(x);
        if(x < xMin()) return getStart().y();
        if(x > xMax()) return getEnd().y();
        return function.value(x);
    }


    /**
     * 等间隔采样，从最左端开始，包含最左端，不一定包含最右端（得看采样间隔是否整除曲线范围）
     * @param xInterval 采样间隔
     * @return 采样得到的 y 值列表
     */
    public List<Double> getYbyXInterval(double xInterval){
        double width = this.getWidth();
        int length = (int)(width / xInterval) + 1;
        List<Double> res = new ArrayList<>(length);
        double start = this.xMin(), end = this.xMax();
        for(double x=start; x<=end; x+=xInterval){
            res.add(this.getY(x));
        }
        return res;
    }

    /**
     * 复制图像
     */
    public abstract Curve copy();

    /**
     * 平移图像
     * @param deltaX x 轴平移量
     * @param deltaY y 轴平移量
     */
    public void move(double deltaX, double deltaY) {
        int length = points.size();
        for (int i=0; i<length; ++i) {
            Point oldPoint = points.get(i);
            points.set(i, new Point(oldPoint.x() + deltaX, oldPoint.y() + deltaY));
        }
        generateXYArray();
        generateFunction();
    }
    /**
     * 平移图像，返回新的图像
     * @param deltaX x 轴平移量
     * @param deltaY y 轴平移量
     */
    public Curve moveNew(double deltaX, double deltaY){
        Curve res = this.copy();
        res.move(deltaX, deltaY);
        return res;
    }

    /**
     * 所有 get 方法都只能得到 复制体
     * @return points 的深拷贝
     */
    public List<Point> getPoints() {
        return new ArrayList<>(points);
    }
    /**
     * 所有 get 方法都只能得到 复制体
     * @return xArray 的深拷贝
     */
    public double[] getxArray() {
        return Arrays.copyOf(xArray, xArray.length);
    }
    /**
     * 所有 get 方法都只能得到 复制体
     * @return yArray 的深拷贝
     */
    public double[] getyArray() {
        return Arrays.copyOf(yArray, yArray.length);
    }
    /**
     * 所有 get 方法都只能得到 复制体
     * @param index 点的索引
     * @return 索引位置的 Point 的深拷贝
     */
    public Point getPoint(int index){
        return points.get(index).copy();
    }
    /**
     * 所有 get 方法都只能得到 复制体
     * @return 第一个 Point 的深拷贝
     */
    public Point getStart(){
        return points.getFirst().copy();
    }
    /**
     * 所有 get 方法都只能得到 复制体
     * @return 最后一个 Point 的深拷贝
     */
    public Point getEnd(){
        return points.getLast().copy();
    }

    /**
     * 查看点列表的长度
     * @return 点列表的长度
     */
    public int getLength(){
        return points.size();
    }
    /**
     * 查看折线图的宽度
     * @return 折线图的宽度
     */
    public double getWidth(){
        return xMax() - xMin();
    }
    /**
     * 查看折线图的 最小 x
     * @return 折线图的 最小 x
     */
    public double xMin() { return xArray[0]; }
    /**
     * 查看折线图的 最大 x
     * @return 折线图的 最大 x
     */
    public double xMax() { return xArray[xArray.length - 1]; }

    public static double calculateWidth(List<Point> points){
        if(points.isEmpty()) return 0;
        return points.getLast().x() - points.getFirst().x();
    }

    /**
     * 去掉所有 y=NaN 点，保留其他
     * @param points 原始点列表
     * @return 处理后的点列表
     */
    public static List<Point> remove_Y_NaN(List<Point> points) {
        return points.stream()
                .filter(p -> (!Double.isNaN(p.y())))
                .collect(Collectors.toList());
    }

    /**
     * 将不在两边的 y=NaN 点替换为插值纵坐标。
     * 假设点列表已按 x 升序排序。
     * 1. 保留两端的 y=NaN 点；
     * 2. 对中间的 y=NaN 点进行线性插值
     * @param points 原始点列表
     * @return 处理后的点列表
     */
    public static List<Point> interpolate_Internal_Y_NaN(List<Point> points) {
        int length = points.size();
        List<Point> result = new ArrayList<>(points);


        // 找第一个和最后一个有效（非 NaN）y 的下标
        int firstValid = -1, lastValid = -1;
        for (int i = 0; i < length; ++i) {
            if (!Double.isNaN(points.get(i).y())) {
                if (firstValid == -1) firstValid = i;
                lastValid = i;
            }
        }
        // 少于2个有效点 → 无法插值，原样返回
        if (firstValid == -1 || firstValid == lastValid) {
            return result;
        }

        // 收集 [firstValid, lastValid] 区间内有效点为插值基准
//        double[] ix = points.stream()
//                .skip(firstValid).limit(lastValid - firstValid + 1)
//                .filter(p -> !Double.isNaN(p.y()))
//                .mapToDouble(Point::x)
//                .toArray();
//        double[] iy = points.stream()
//                .skip(firstValid).limit(lastValid - firstValid + 1)
//                .filter(p -> !Double.isNaN(p.y()))
//                .mapToDouble(Point::y)
//                .toArray();
        LineChart newLine = new LineChart(remove_Y_NaN(points));

        // 构建结果：外侧 NaN 保留，内部 NaN 插值
        for (int i = 0; i < length; ++i) {
            Point p = result.get(i);
            if (Double.isNaN(p.y())) {
                // 内部 NaN → 插值
                if (i > firstValid && i < lastValid) {
                    result.set(i, new Point(p.x(), newLine.getY(p.x())));
                }
            }
        }
        return result;
    }

    /**
     * 将 y=NaN 点替换为插值纵坐标。
     * 假设点列表已按 x 升序排序。
     * 1. 两边的替换为最外侧有效值；
     * 2. 中间的根据折线图插值
     * @param points 原始点列表
     * @return 处理后的点列表
     */
    public static List<Point> interpolate_All_Y_NaN(List<Point> points) {
        List<Point> result = interpolate_Internal_Y_NaN(points);
        int length = result.size();

        // 找第一个和最后一个有效（非 NaN）y 的下标
        int firstValid = -1, lastValid = -1;
        double firstY = Double.NaN, lastY = Double.NaN;
        for (int i = 0; i < length; ++i) {
            Point p = result.get(i);
            if (!Double.isNaN(p.y())) {
                firstValid = i;
                firstY = p.y();
                break;
            }
        }
        for (int i = length-1; i > 0; --i) {
            Point p = result.get(i);
            if (!Double.isNaN(p.y())) {
                lastValid = i;
                lastY = p.y();
                break;
            }
        }

        // 没找到有效点，直接原样返回
        if(firstValid == -1){
            throw new IllegalArgumentException("没有有效点，两边无法赋值");
        }

        for(int i=0; i<firstValid; ++i){
            result.set(i, new Point(result.get(i).x(), firstY));
        }
        for(int i=length-1; i>lastValid; --i){
            result.set(i, new Point(result.get(i).x(), lastY));
        }
        return result;
    }

    /**
     * 去掉所有 y=NaN 点，折线中间的 y=NaN 点用插值替换。
     * 假设点列表已按 x 升序排序。
     * 1. 去掉两端的 y=NaN 点；
     * 2. 对中间的 y=NaN 点进行线性插值
     * @param points 原始点列表
     * @return 处理后的点列表
     */
    public static List<Point> replace_Y_NaN(List<Point> points) {
        return remove_Y_NaN(interpolate_Internal_Y_NaN(points));
    }

    /**
     * 将折线图转换成y轴等间距的点集
     * @param points 折线图
     * @param start 将y轴等间距分割的起始位置
     * @param interval 将y轴等间距分割的间距
     * @param hasNaN points点集中是否有 y=NaN 点
     * @return 处理后的点集
     */
    public static List<Point> points_to_y_interval_points(
            List<Point> points,
            double start,
            double interval,
            boolean hasNaN){
        if(hasNaN){
            points = replace_Y_NaN(points);
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
     * @param hasNaN points点集中是否有 y=NaN 点
     * @return 每个y值对应的x值集合
     */
    public static Map<Double, List<Double>> points_to_y_interval_xs(
            List<Point> points,
            double start,
            double interval,
            boolean hasNaN){
        if(hasNaN){
            points = replace_Y_NaN(points);
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
     * 判断两条曲线沿 x 轴平移后的“最小均方误差”。
     * 沿 x 轴平移一条曲线，尝试所有重叠区间（无重叠 -> 全覆盖 -> 无重叠），计算均方误差，返回最小均方误差。
     * @param other 另一条曲线
     * @param deltaStep 平移的步长（一般设置得很小）
     * @param xInterval 用来计算均方误差的 x 的采样间隔是 deltaStep 的多少倍（整数）
     * @param minOverlapRatio 最小重叠占比（重叠占比 = 重叠区间 / this曲线的宽度）
     * @param base_this_or_short 计算重叠率时，分母为this曲线（true）还是较短曲线（false）
     * @return Pair{最小均方误差, 此时的重叠区间 x 轴宽度}
     */
    public Pair<Double, Double> xShiftMSE(Curve other, double deltaStep, int xInterval, double minOverlapRatio, boolean base_this_or_short) {
        List<Double> y1 = this.getYbyXInterval(deltaStep);
        List<Double> y2 = other.getYbyXInterval(deltaStep);
        int minLength = base_this_or_short ? y1.size() : Math.min(y1.size(), y2.size());
        if(minOverlapRatio > 1) minOverlapRatio = 1;
        int minOverlapNum = (int)(minOverlapRatio * (minLength - 1)) + 1;
        if(base_this_or_short && y2.size() < minOverlapNum) return Pair.of(Double.MAX_VALUE, 0.0);
        Pair<Double, Integer> mmse_and_overlapDeltaStepNum = MathUtils.calculateMinMSE(y1, y2, xInterval, minOverlapNum);
        return Pair.of(mmse_and_overlapDeltaStepNum.getLeft(), deltaStep * mmse_and_overlapDeltaStepNum.getRight());
    }

    /**
     * 判断曲线沿 x 轴平移后与参考集内所每个曲线的“最小均方误差”。
     * 沿 x 轴平移一条曲线，尝试所有重叠区间（无重叠 -> 全覆盖 -> 无重叠），计算均方误差，返回参考集内每个曲线的最小均方误差。
     * @param others 参考集
     * @param deltaStep 平移的步长（一般设置得很小）
     * @param xInterval 用来计算均方误差的 x 的采样间隔是 deltaStep 的多少倍（整数）
     * @param minOverlapRatio 最小重叠占比（重叠占比 = 重叠区间 / 较短曲线的宽度）
     * @param base_this_or_short 计算重叠率时，分母为this曲线（true）还是较短曲线（false）
     * @return 参考集中每条线的 Pair{最小均方误差, 此时的重叠区间 x 轴宽度}
     */
    public Map<String, Pair<Double, Double>> xShiftMSEMap(Map<String, Curve> others, double deltaStep, int xInterval, double minOverlapRatio, boolean base_this_or_short){
        Map<String, Pair<Double, Double>> result = new HashMap<>(others.size());
        for(Map.Entry<String, Curve> entry : others.entrySet()){
            result.put(entry.getKey(), xShiftMSE(entry.getValue(), deltaStep, xInterval, minOverlapRatio, base_this_or_short));
        }
        return result;
    }

    /**
     * 计算重叠 x 轴宽度
     * @param shift this 沿 x 轴平移的距离
     */
    public double calculateOverlapWidth(Curve other, double shift) {
        double overlapMin = Math.max(this.xMin() + shift, other.xMin());
        double overlapMax = Math.min(this.xMax() + shift, other.xMax());

        return Math.max(0, overlapMax - overlapMin);
    }
    /**
     * 计算重叠率（重叠部分宽度 / this曲线或较短曲线的值域宽度）
     * @param shift this 沿 x 轴平移的距离
     * @param base_this_or_short 计算重叠率时，分母为this曲线（true）还是较短曲线（false）
     */
    public double calculateOverlapRatio(Curve other, double shift, boolean base_this_or_short) {
        double overlapWidth = this.calculateOverlapWidth(other, shift);
        double minWidth = base_this_or_short
                ? this.getWidth()
                : Math.min(this.getWidth(), other.getWidth());

        return overlapWidth / minWidth;
    }

    /**
     * 平滑曲线数据（使用移动平均，即每个点的新 y 值设置为所处区间内的 y 值的平均值），平滑过程中取点建列表，不使用原列表的点
     * @param points 待平滑的点列表
     * @param windowWidth 计算平均值的区间长度，处于两端时截断区间
     * @param precision 计算平均值的精度，计算平均值时将平滑区间分割成几份，理论上应该用 (y值积分 / 区间宽度) 作为平均值
     * @param smoothness 平滑度，平滑过程中取点建列表的 x 值间隔，如果为非正数，则用原列表内点的 x 值建列表
     * @return 平滑后的点列表
     */
    public static List<Point> smoothCurve(List<Point> points,
                                          double windowWidth, int precision,
                                          double smoothness) {
        if (windowWidth <= 0) return new ArrayList<>(points);

        double halfWindow = windowWidth / 2;
        double step = windowWidth / precision;

        Curve curve = new LineChart(points);
        List<Point> result = new ArrayList<>(points.size());

        double nowX = 0;
        double sumY = 0;
        if(smoothness <= 0) {
            for (Point point : points) {
                nowX = point.x();
                sumY = 0;
                for (double j = nowX - halfWindow; j <= nowX + halfWindow; j += step) {
                    sumY += curve.getY(j);
                }
                result.add(new Point(nowX, sumY / (precision + 1)));
            }
        } else {
            nowX = points.getFirst().x();
            while (nowX <= points.getLast().x()){
                sumY = 0;
                for (double j = nowX - halfWindow; j <= nowX + halfWindow; j += step) {
                    sumY += curve.getY(j);
                }
                result.add(new Point(nowX, sumY / (precision + 1)));
                nowX += smoothness;
            }
        }
        return result;
    }

    /**
     * 平滑曲线数据（使用移动平均，即每个点的新 y 值设置为所处区间内所有点的 y 值的平均值）
     * @param points 待平滑的点列表
     * @param windowSize 计算平均值的区间内点的个数，曲线两端外侧取端点的 y 值
     * @return 平滑后的点列表
     */
    public static List<Point> smoothPointList(List<Point> points, int windowSize) {
        if (windowSize <= 1) return new ArrayList<>(points);

        int halfWindow = windowSize / 2;

        List<Point> result = new ArrayList<>(points.size());

        List<Point> leftTemp = new ArrayList<>(halfWindow);
        List<Point> rightTemp = new ArrayList<>(halfWindow);
        for(int i=0; i<halfWindow; ++i){
            leftTemp.add(points.getFirst().copy());
            rightTemp.add(points.getLast().copy());
        }
        List<Point> temp = new ArrayList<>(leftTemp);
        temp.addAll(points);
        temp.addAll(rightTemp);

        int count = 2*halfWindow + 1;
        double sum = (halfWindow+1)*points.getFirst().y();
        for(int i=halfWindow+1; i<count; ++i){
            sum += temp.get(i).y();
        }
        result.add(new Point(points.getFirst().x(), sum / count));

        int left = 0;
        int right = count;

        for (int i = 1; i < points.size(); ++i) {
            Point point = points.get(i);
            sum -= temp.get(left).y();
            sum += temp.get(right).y();
            result.add(new Point(point.x(), sum / count));
            left++;
            right++;
        }
        return result;
    }

    /**
     * 计算平移后的均方误差
     * @param shift this 沿 x 轴平移的距离
     * @param other 另一条曲线
     * @param xStep 计算均方误差的 x 的采样间隔
     * @param minOverlapRatio 两曲线的最小重叠率（重叠率 = 重叠区间 / this曲线的宽度），如果平移后重叠率过小，则返回 1e9
     * @param base_this_or_short 计算重叠率时，分母为this曲线（true）还是较短曲线（false）
     */
    public double calculateMSE(double shift, Curve other, double xStep, double minOverlapRatio, boolean base_this_or_short) {

        // 计算重叠区间
        double overlapMin = Math.max(this.xMin() + shift, other.xMin());
        double overlapMax = Math.min(this.xMax() + shift, other.xMax());

//        double overlapWidth = overlapMax - overlapMin;
//        double minOverlapWidth = minOverlapRatio * Math.min(this.getWidth(), other.getWidth());
        double overlapRatio = this.calculateOverlapRatio(other, shift, base_this_or_short);

        // 重叠不足时返回大值
        if (overlapRatio < minOverlapRatio) {
            return 1e9;
        }

        // 在重叠区间内采样
        double sumSquaredError = 0.0;
        int validPoints = 0;

        for (double xB = overlapMin; xB <= overlapMax; xB+=xStep) {
            double xA = xB - shift;

            double yA = this.getY(xA);
            double yB = other.getY(xB);

            double diff = yA - yB;
            sumSquaredError += diff * diff;
            validPoints++;
        }

        if (validPoints == 0) {
            return 1e9;
        }

        // 返回 MSE
        return sumSquaredError / validPoints;
    }

    /**
     * 匹配两条曲线，寻找最佳平移量
     * @param coarseStep 粗搜索步长
     * @param fineSearchRange 精细搜索范围，[x-fineSearchRange, x+fineSearchRange]
     * @param xStep 计算均方误差的 x 的采样间隔
     * @param minOverlapRatio 两曲线的最小重叠率（重叠率 = 重叠区间 / this曲线的宽度），如果平移后重叠率过小，则返回 1e9
     * @param base_this_or_short 计算重叠率时，分母为this曲线（true）还是较短曲线（false）
     * @return Pair{最小均方误差, 此时的 x 轴重叠率}
     */
    public Pair<Double, Double> matchTwoCurves(Curve other,
                                                double coarseStep,
                                                double fineSearchRange,
                                                double xStep,
                                                double minOverlapRatio,
                                                boolean base_this_or_short) {
        // 确定平移搜索范围
        double aMin = this.xMin();
        double aMax = this.xMax();
        double bMin = other.xMin();
        double bMax = other.xMax();

        double searchMin = bMin - aMax;
        double searchMax = bMax - aMin;

        // 粗搜索
        double bestShift = searchMin;
        double bestError = Double.MAX_VALUE;

        for (double shift = searchMin; shift <= searchMax;
             shift += coarseStep) {
            double error = calculateMSE(shift, other, xStep, minOverlapRatio, base_this_or_short);
            if (error < bestError) {
                bestError = error;
                bestShift = shift;
            }
        }

        // 精搜索（使用Brent优化算法）
        double fineSearchMin = Math.max(searchMin, bestShift - fineSearchRange);
        double fineSearchMax = Math.min(searchMax, bestShift + fineSearchRange);

        try {
            // 1e-8：相对精度 - 当连续两次迭代结果的相对变化小于这个值时停止
            //1e-10：绝对精度 - 当连续两次迭代结果的绝对变化小于这个值时停止
            BrentOptimizer optimizer = new BrentOptimizer(1e-8, 1e-10);

            UnivariatePointValuePair optimum = optimizer.optimize(
                    new MaxEval(100),
                    new UnivariateObjectiveFunction(shift ->
                            calculateMSE(shift, other, xStep, minOverlapRatio, base_this_or_short)),
                    new SearchInterval(fineSearchMin, fineSearchMax),
                    GoalType.MINIMIZE
            );

            bestShift = optimum.getPoint();
            bestError = optimum.getValue();
        } catch (Exception e) {
            // 精搜索失败时使用粗搜索结果
            System.err.println("精搜索失败，使用粗搜索结果: " + e.getMessage());
        }

        // 计算重叠率
        double overlapRatio = this.calculateOverlapRatio(other, bestShift, base_this_or_short);

        return Pair.of(bestError, overlapRatio);
    }

}
