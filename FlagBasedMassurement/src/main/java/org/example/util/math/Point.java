package org.example.util.math;

import org.apache.commons.lang3.tuple.Pair;
import org.example.util.text.MyStringUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record Point(double x, double y) {
    /**
     * 按x坐标排序
     * @return this.x - other.x 的正负1或0
     */
    public int compareByX(Point other) {
        return Double.compare(this.x, other.x);
    }

    /**
     * 按y坐标排序
     * @return this.y - other.y 的正负1或0
     */
    public int compareByY(Point other) {
        return Double.compare(this.y, other.y);
    }

    /**
     * 按模长（到原点的距离）排序
     * @return this - other 的正负1或0
     */
    public int compareByModulus(Point other) {
        return Double.compare((this.x * this.x + this.y * this.y), (other.x * other.x + other.y * other.y));
    }

    /**
     * 深拷贝
     */
    public Point copy() {
        return new Point(x,y);
    }
    /**
     * 深拷贝
     */
    public static Point copy(Point p) {
        return p.copy();
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
     * 读取目录下所有JSON文件到Map
     * @param directory 目录路径
     * @return Map<文件名, List<Point>>
     */
    public static Map<String, List<Point>> loadPointsMapFromJsonFiles(Path directory) throws IOException {
        if (!Files.exists(directory) || !Files.isDirectory(directory)) {
            throw new IllegalArgumentException("目录不存在: " + directory);
        }

        Map<String, List<Point>> result = new HashMap<>();

        try (var stream = Files.list(directory)) {
                stream.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json"))
                    .forEach(filePath -> {
                        try {
                            // 读取JSON文件
                            List<Point> points = loadPointsFromJsonFile(filePath);

                            // 获取文件名（不含后缀）
                            String fileName = MyStringUtils.removeSuffix(filePath.getFileName().toString(), ".json");

                            // 放入Map
                            result.put(fileName, points);

                        } catch (Exception e) {
                            System.err.println("读取文件失败: " + filePath + ", 错误: " + e.getMessage());
                        }
                    });
        }

        return result;
    }

    /**
     * 读取目录下所有JSON文件到List<Pair>
     * @param directory 目录路径
     * @return List<Pair<文件名, List<Point>>>
     */
    public static List<Pair<String, List<Point>>> loadPointsPairsFromJsonFiles(Path directory) throws IOException {
        if (!Files.exists(directory) || !Files.isDirectory(directory)) {
            throw new IllegalArgumentException("目录不存在: " + directory);
        }

        List<Pair<String, List<Point>>> result = new ArrayList<>();

        try (var stream = Files.list(directory)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json"))
                    .forEach(filePath -> {
                        try {
                            // 读取JSON文件
                            List<Point> points = loadPointsFromJsonFile(filePath);

                            // 获取文件名（不含后缀）
                            String fileName = MyStringUtils.removeSuffix(filePath.getFileName().toString(), ".json");

                            // 放入List
                            result.add(Pair.of(fileName, points));

                        } catch (Exception e) {
                            System.err.println("读取文件失败: " + filePath + ", 错误: " + e.getMessage());
                        }
                    });
        }

        return result;
    }

    /**
     * 读取目录下所有JSON文件到List
     * @param directory 目录路径
     * @return List<List<Point>>
     */
    public static List<List<Point>> loadPointsListFromJsonFiles(Path directory) throws IOException {
        if (!Files.exists(directory) || !Files.isDirectory(directory)) {
            throw new IllegalArgumentException("目录不存在: " + directory);
        }

        List<List<Point>> result = new ArrayList<>();

        try (var stream = Files.list(directory)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json"))
                    .forEach(filePath -> {
                        try {
                            // 读取JSON文件
                            List<Point> points = loadPointsFromJsonFile(filePath);

                            // 获取文件名（不含后缀）
                            String fileName = MyStringUtils.removeSuffix(filePath.getFileName().toString(), ".json");

                            // 放入Map
                            result.add(points);

                        } catch (Exception e) {
                            System.err.println("读取文件失败: " + filePath + ", 错误: " + e.getMessage());
                        }
                    });
        }

        return result;
    }


    /**
     * 追加 Point 到现有文件
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
