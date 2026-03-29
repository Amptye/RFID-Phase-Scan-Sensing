package org.example.util;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public record Point(double x, double y) {
    /**
     * 按x坐标排序
     * @return this.x - other.x
     */
    public int compareByX(Point other) {
        return Double.compare(this.x, other.x);
    }

    /**
     * 按y坐标排序
     * @return this.y - other.y
     */
    public int compareByY(Point other) {
        return Double.compare(this.y, other.y);
    }

    /**
     * 按模长（到原点的距离）排序
     * @return this - other
     */
    public int compareByModulus(Point other) {
        return Double.compare((this.x * this.x + this.y * this.y), (other.x * other.x + other.y * other.y));
    }

    /**
     * 将 List<Point> 存储为 Json 格式文件
     * @param  points 点列表
     * @param filePath 文件路径（Path 类型）
     */
    public static void savePointsToJsonFile(List<Point> points, Path filePath) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.isEnabled(SerializationFeature.INDENT_OUTPUT); // 美化输出

        // 确保父目录存在
        Path parentDir = filePath.getParent();
        if(parentDir != null){
            Files.createDirectories(parentDir);
        }

        mapper.writeValue(Files.newBufferedWriter(filePath), points);
    }

    /**
     * 从 Json 格式文件中读取 List<Point>
     * @param filePath 文件路径
     * @return 点列表
     */
    public static List<Point> loadPointsFromJsonFile(Path filePath) throws IOException {
        if(!Files.exists(filePath) || !Files.isRegularFile(filePath)){
            return new ArrayList<>();
        }
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(
                Files.newBufferedReader(filePath),
                new TypeReference<List<Point>>(){}
        );
    }

    /**
     * 追加点到现有文件
     * @param newPoints 新增的点
     * @param filePath 文件路径
     */
    public static void appendPointsToJsonFile(List<Point> newPoints, Path filePath) throws IOException {
        // 读取已有数据
        List<Point> existingPoints = loadPointsFromJsonFile(filePath);

        // 合并数据
        existingPoints.addAll(newPoints);
        savePointsToJsonFile(existingPoints, filePath);
    }
}
