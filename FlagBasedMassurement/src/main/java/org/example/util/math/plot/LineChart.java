package org.example.util.math.plot;

import org.apache.commons.math3.analysis.interpolation.LinearInterpolator;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;
import org.example.util.math.Point;

import java.util.*;
import java.util.stream.Collectors;

public class LineChart extends Curve {

    public LineChart(List<Point> points) {
        AssertLineChart(points);
        super(points);
        generateFunction();
    }
    public LineChart(LineChart line) {
        super(line);
        generateFunction();
    }
    public LineChart(double[] xArray, double[] yArray) {
        super(xArray, yArray);
        AssertLineChart(points);
        generateFunction();
    }

    @Override
    public void generateFunction(){
        this.function = new LinearInterpolator().interpolate(xArray, yArray);
    }

    /**
     * 确保能构成折线图，否则丢出错误。
     * 在 AssertCurve() 的之前进行
     * @param points 原始点列表
     */
    public static void AssertLineChart(List<Point> points){}


    @Override
    public LineChart copy(){
        return new LineChart(this);
    }

    /**
     * 线性插值：根据 x 值插值得到 y 值，如果 x 超出范围则报错。
     * 折线图不包含 y=NaN 点
     * @param x 目标 x 坐标
     * @return 插值得到的 y 值
     */
    @Override
    public double getY(double x) {
        // 处理边界情况
//        AssertXinRange(x);
        if(x < xMin()) return getStart().y();
        if(x > xMax()) return getEnd().y();

        return function.value(x);
//        if (points == null || points.isEmpty()) {
//            throw new IllegalArgumentException("点列表为空，无法查找 " + x + " 处的 y 值");
//        }
//
//        List<Point> linePoints = remove_Y_NaN(points);
//        if (linePoints == null || linePoints.isEmpty()) {
//            throw new IllegalArgumentException("点列表中 y 全为 NaN，无法查找 " + x + " 处的 y 值");
//        }
//
//        // 处理边界情况
//        if (x < linePoints.getFirst().x() || x > linePoints.getLast().x()) {
//            throw new IllegalArgumentException(
//                    "x=" + x + " 超出了折线图范围 [" + linePoints.getFirst().x() + ", " + linePoints.getLast().x() + "]");
//        }
//
//        // 如果只有一个有效点，上面排除了超出范围的情况，此时必定为该点
//        if (linePoints.size() == 1){
//            return linePoints.getFirst().y();
//        }
//
//        // 查找 x 所在的区间并进行线性插值
//        for (int i = 0; i < linePoints.size() - 1; i++) {
//            Point p1 = linePoints.get(i);
//            Point p2 = linePoints.get(i + 1);
//            if (x >= p1.x() && x <= p2.x()) {
//                double t = (x - p1.x()) / (p2.x() - p1.x());
//                return p1.y() + t * (p2.y() - p1.y());
//            }
//        }
//
//        // 理论上不会执行到这里
//        return linePoints.getLast().y();
    }

//    @Override
//    public LineChart moveNew(double deltaX, double deltaY) {
//        int length = points.size();
//        List<Point> newPoints = new ArrayList<>(length);
//        for (Point oldPoint : points) {
//            newPoints.add(new Point(oldPoint.x() + deltaX, oldPoint.y() + deltaY));
//        }
//        return new LineChart(newPoints);
//    }

}
