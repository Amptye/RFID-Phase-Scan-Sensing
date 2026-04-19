package org.example.reader;

import com.impinj.octane.OctaneSdkException;
import org.example.data.tagdata.MyTagData;
import org.example.data.tagdata.TagDataCache;
import org.example.reader.tasks.BpiReference;
import org.example.reader.tasks.BpiScanSensing;
import org.example.reader.tasks.PowerScan;

import java.util.Scanner;

public class ReaderMain {

    public static TagDataCache<MyTagData> dataCache = new TagDataCache<>();

    public static void main(String[] args){
        run();
    }

    public static void run() {
        ReaderOperations.setNewReader();
        try {
            // 连接阅读器
            ReaderOperations.connectReader();

            // 配置阅读器
            ReaderOperations.configReader();

            // 先扫频
//            BpiReference.run();
            BpiScanSensing.run();

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
}
