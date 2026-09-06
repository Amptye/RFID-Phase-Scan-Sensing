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

public class BpiReference {
    public static ImpinjReader READER = ReaderOperations.READER;
    public static String Target_EPC = null;
    public static boolean feature_lineSet = false;
    public static String Tag_Feature = "";
    public static Path DATA_DIR = Path.of("data");
    public static Path reference_DATA_DIR = DATA_DIR.resolve("BPI_Reference");
    public static String dateMsg ="";
    public static Path now_DATA_DIR = null;
    public static Path feature_DATA_DIR = null;
    public static Path distance_DIR = null;
    //    static ArrayList<String> received_data = new ArrayList<>();
//    public static TagDataCache dataCache = new TagDataCache();
    static {
        dateMsg = LocalDate.now().toString();
        now_DATA_DIR = reference_DATA_DIR.resolve("data-"+dateMsg);
//        feature_DATA_DIR = DATA_DIR.resolve("tag-features");
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
        System.out.println("=== 收集BPI参考值 ===");
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

            feature_lineSet = config.hasPath("feature_lineSet")
                    && config.getBoolean("feature_lineSet");

            Tag_Feature = config.hasPath("tag_feature")
                    ? config.getString("tag_feature")
                    : null;
            if (Tag_Feature == null) {
                LocalTime time = LocalTime.now(); // 例如: 14:30:45.123
                // 1. 转换为 "HH-mm-ss" 格式
                DateTimeFormatter formatter1 = DateTimeFormatter.ofPattern("HH-mm-ss");
                String timeStr1 = time.format(formatter1);
                // 示例: "14-30-45"
                now_data_path = now_DATA_DIR.resolve(timeStr1 + ".json");
            } else {
                if(!feature_lineSet) {
                    now_data_path = distance_DIR.resolve(Tag_Feature + ".json");
                } else {
                    LocalDateTime time = LocalDateTime.now();
                    DateTimeFormatter formatter1 = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");
                    String timeStr1 = time.format(formatter1);
                    now_data_path = distance_DIR.resolve(Tag_Feature).resolve(timeStr1 + ".json");
                }
            }
        } catch (ConfigException.IO ec){
            System.out.println("无法找到 config.conf 文件");
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
            ex.printStackTrace(System.out);
        }

//        BpiTest.Target_EPC = Target_EPC;
        BpiTest.run();

        System.out.println("=== 记录BPI参考值 ===");
        try {
            if(now_data_path != null)
                Point.savePointsToJsonFile(BpiTest.bpiList, now_data_path);
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
            ex.printStackTrace(System.out);
        }
    }

}
