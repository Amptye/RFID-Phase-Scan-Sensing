package org.example.reader;

import com.impinj.octane.*;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import org.example.util.text.MyStringUtils;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Objects;

public class ReaderOperations {
    public static ImpinjReader READER;
    public static String hostname = "";
    public static double PTX = 20.0;

    public static void setNewReader() {
        READER = new ImpinjReader();
        Config config = null;
        try {
            config = ConfigFactory.load("config.conf");
        } catch (ConfigException.IO ec){
            System.out.println("无法找到 config.conf 文件");
            return;
        }
        hostname = config.getString("hostname");
    }

    public static void configReader()
            throws OctaneSdkException, URISyntaxException, IOException, NoSuchMethodException, IllegalAccessException {
        String settings_path = Objects.requireNonNull(
                ReaderOperations.class.getClassLoader()
                        .getResource("settings.json")
        ).toURI().getPath();
        Settings settings = Settings.load(settings_path);
        System.out.println(settings.getFilters().getMode());
        System.out.println(settings.getFilters().getTagFilter1().getTagMask());
        System.out.println(settings.getFilters().getTagFilter1().getFilterOp());
        READER.applySettings(settings);
        System.out.println(READER.querySettings().getFilters().getMode());
        System.out.println(READER.querySettings().getFilters().getTagFilter1().getTagMask());
        System.out.println(READER.querySettings().getFilters().getTagFilter1().getFilterOp());
//        ArrayList<AntennaConfig> acList = settings.getAntennas().getAntennaConfigs();
//        for (AntennaConfig ac : acList) {
//            if (ac.isEnabled()) {
//                PTX = ac.getTxPowerinDbm();
//                break;
//            }
//        }
    }

    public static void connectReader() throws OctaneSdkException {
        System.out.println("Connecting to " + hostname);
        READER.connect(hostname + ".local");
    }
    public static void disconnectReader() {
        System.out.println("Disconnecting from " + MyStringUtils.removeSuffix(READER.getAddress(),".local"));
        READER.disconnect();
    }

    public static void startReader() throws OctaneSdkException {
        System.out.println("Starting  " + MyStringUtils.removeSuffix(READER.getAddress(),".local"));
        READER.start();
    }
    public static void stopReader() throws OctaneSdkException {
        System.out.println("Stopping  " + MyStringUtils.removeSuffix(READER.getAddress(),".local"));
        READER.stop();
    }
}
