package org.example.util.math.plot;

import org.apache.commons.math3.analysis.interpolation.LinearInterpolator;
import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;
import org.example.util.math.Point;

import java.util.Arrays;
import java.util.List;

public class PolynomialCurve extends Curve{

    public final int degree;
    public double[] parameter;

    public PolynomialCurve(List<Point> points, int degree) {
        AssertPolynomialCurve(points, degree);
        super(points);
        this.degree = degree;
        this.parameter = this.fitPolynomial(degree);
        generateFunction();
    }
    public PolynomialCurve(PolynomialCurve line) {
        super(line);
        this.degree = line.degree;
        this.parameter = Arrays.copyOf(line.parameter, line.parameter.length);
        generateFunction();
    }
    public PolynomialCurve(double[] xArray, double[] yArray, int degree) {
        super(xArray, yArray);
        AssertPolynomialCurve(points, degree);
        this.degree = degree;
        this.parameter = this.fitPolynomial(degree);
        generateFunction();
    }

    @Override
    public void generateFunction(){
        this.function = new PolynomialFunction(this.parameter);
    }

    /**
     * 确保能构成折线图，否则丢出错误。
     * 在 AssertCurve() 的之前进行
     * @param points 原始点列表
     */
    public static void AssertPolynomialCurve(List<Point> points, int degree){
        if(points != null && points.size() <= degree){
            throw new IllegalArgumentException(degree + " 次多项式至少要 " + (degree+1) + " 个点，但只有 " + points.size() + " 个");
        }
    }


    @Override
    public PolynomialCurve copy(){
        return new PolynomialCurve(this);
    }

    /**
     * 插值：根据 x 值插值得到 y 值，如果 x 超出范围则报错。
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
    }

    /**
     * 所有 get 方法都只能得到 复制体
     * @return parameter 的深拷贝
     */
    public double[] getParameter() {
        return Arrays.copyOf(parameter, parameter.length);
    }

    /**
     * 计算两个一次多项式的参数之间的偏差
     * @param other 另一个多项式曲线（最高次幂须和本曲线相同，且都要为 1）
     * @return 两个多项式的参数之间的偏差（顶点式：y=ax+b，只看 a）
     */
    public double xShift1DegreeParamError(PolynomialCurve other){
        return Math.abs(this.parameter[1]- other.parameter[1]);
    }

    /**
     * 计算两个二次多项式的参数之间的偏差
     * @param other 另一个多项式曲线（最高次幂须和本曲线相同，且都要为 2）
     * @return 两个多项式的参数之间的偏差（顶点式：y=a(x-h)^2+k，b=-2ah，c=ah^2+k，只看 a 和 k，即 a 和 c-(b^2/4a)）
     */
    public double xShift2DegreeParamError(PolynomialCurve other){
        return Math.abs(this.parameter[2]- other.parameter[2])
                + Math.abs((this.parameter[0]-(this.parameter[1]*this.parameter[1])/(4*this.parameter[2]))
                    - (other.parameter[0]-(other.parameter[1]*other.parameter[1])/(4*other.parameter[2])));
    }

}
