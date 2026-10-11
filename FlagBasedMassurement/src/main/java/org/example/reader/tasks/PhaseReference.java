package org.example.reader.tasks;

import com.impinj.octane.*;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import org.example.reader.ReaderOperations;
import org.example.util.math.Point;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public class PhaseReference {
    public static ImpinjReader READER = ReaderOperations.READER;
    public static Path DATA_DIR = Path.of("data");
    public static Path reference_DATA_DIR = DATA_DIR.resolve("Phase_Reference");
    public static String dateMsg =LocalDate.now().toString();

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
        System.out.println("=== 收集Phase参考值 ===");
        Path frequency_Parent_Dir = null;
        Path reference_file_self = null;
        try {
            Config config = null;
            config = ConfigFactory.load("config.conf");

            frequency_Parent_Dir = getReference_Data_File_Dir(config);
            reference_file_self = getReference_Data_File_Self(config);

        } catch (ConfigException.IO ec){
            System.out.println("无法找到 config.conf 文件");
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
            ex.printStackTrace(System.out);
        }

//        PhaseTest.Target_EPC = Target_EPC;
        PhaseTest.run();
        if(!PhaseTest.read_at_maxPtx){
            System.out.println("=== 在最大功率下未检测到标签！无法记录Phase参考值！ ===");
            return;
        }

        System.out.println("=== 记录Phase参考值 ===");
        if(frequency_Parent_Dir != null) {
            try {
                for (Map.Entry<Double, List<Point>> entry : PhaseTest.freq_PhaseList.entrySet()) {
                    double freq = entry.getKey();
                    List<Point> deltaPhaseList = entry.getValue();
                    Point.savePointsToJsonFile(deltaPhaseList, frequency_Parent_Dir.resolve(String.valueOf(freq)).resolve(reference_file_self));
                }
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
                ex.printStackTrace(System.out);
            }
        }
    }

    public static Path getReference_Data_File_Dir(Config config){
        String target_epc = config.hasPath("target_epc")
                ? config.getString("target_epc")
                : "unknown_epc";
        String feature_DIR = config.hasPath("feature_dir")
                ? config.getString("feature_dir")
                : "tag-features";
        String distance = config.hasPath("reference_distance")
                ? config.getString("reference_distance")
                : "unknown_distance";

        return reference_DATA_DIR
                .resolve(target_epc)
                .resolve(feature_DIR)
                .resolve(distance);
    }

    public static Path getReference_Data_File_Self(Config config){
        Path file_path = null;

        boolean feature_lineSet = config.hasPath("feature_lineSet")
                && config.getBoolean("feature_lineSet");
        String tag_feature = config.hasPath("tag_feature")
                ? config.getString("tag_feature")
                : null;

        if (tag_feature == null) {
            LocalTime time = LocalTime.now(); // 例如: 14:30:45.123
            // 1. 转换为 "HH-mm-ss" 格式
            DateTimeFormatter formatter1 = DateTimeFormatter.ofPattern("HH-mm-ss");
            String timeStr1 = time.format(formatter1);
            // 示例: "14-30-45"
            file_path = Path.of(
                    "data-"+dateMsg,
                    timeStr1 + ".json");
        } else {
            if(!feature_lineSet) {
                file_path = Path.of(
                        tag_feature + ".json");
            } else {
                LocalDateTime time = LocalDateTime.now();
                DateTimeFormatter formatter1 = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");
                String timeStr1 = time.format(formatter1);
                file_path = Path.of(
                        tag_feature,
                        timeStr1 + ".json");
            }
        }
        return file_path;
    }

}
