package org.example.reader;

import com.impinj.octane.*;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Xingyu on 2021/5/13.
 * Generate settings.
 */

public class ReaderSettings {
    public static void main(String[] args) {
        ImpinjReader reader = new ImpinjReader();
        Config config = null;
        try {
            config = ConfigFactory.load("config.conf");
        } catch (ConfigException.IO ec){
            System.out.println("无法找到 config.conf 文件");
            return;
        }
        try {
            String hostname = config.getString("hostname");
            reader.connect(hostname+".local");
            Settings settings = reader.queryDefaultSettings();
            Config settings_conf = config.getConfig("settings");
            settings.setReaderMode(ReaderMode.valueOf(settings_conf.getString("ReaderMode")));
            settings.setSearchMode(SearchMode.valueOf(settings_conf.getString("SearchMode"))); // Query A
            settings.setSession(settings_conf.getInt("Session")); // Session 1
            settings.setTagPopulationEstimate(settings_conf.getInt("TagPopulationEstimate"));
            settings.getFilters().setMode(TagFilterMode.valueOf(settings_conf.getString("filters.Mode")));
            ArrayList<AntennaConfig> acList = settings.getAntennas().getAntennaConfigs();
            List<? extends  Config> antennas_conf = config.getConfigList("antennas");
            for (int i=0; i<acList.size() && i<antennas_conf.size(); ++i) {
                AntennaConfig ac = acList.get(i);
                Config ac_conf = antennas_conf.get(i);
                ac.setEnabled(ac_conf.getBoolean("Enabled"));
                ac.setIsMaxTxPower(ac_conf.getBoolean("IsMaxTxPower"));
                ac.setTxPowerinDbm(ac_conf.getInt("TxPowerinDbm")); //(20); (31.5);
                ac.setIsMaxRxSensitivity(ac_conf.getBoolean("IsMaxRxSensitivity"));
//                ac.setRxSensitivityinDbm();
            }
            ReportConfig r = settings.getReport();
            Config report_conf = settings_conf.getConfig("report");
            r.setIncludeAntennaPortNumber(report_conf.getBoolean("IncludeAntennaPortNumber"));
            r.setIncludeFirstSeenTime(report_conf.getBoolean("IncludeFirstSeenTime"));
            r.setIncludeLastSeenTime(report_conf.getBoolean("IncludeLastSeenTime"));
            r.setIncludeFastId(report_conf.getBoolean("IncludeFastId"));
            r.setIncludePeakRssi(report_conf.getBoolean("IncludePeakRssi"));
            r.setIncludePcBits(report_conf.getBoolean("IncludePcBits"));
            r.setIncludeChannel(report_conf.getBoolean("IncludeChannel"));
            r.setIncludeDopplerFrequency(report_conf.getBoolean("IncludeDopplerFrequency"));
            r.setIncludePhaseAngle(report_conf.getBoolean("IncludePhaseAngle"));
            r.setMode(ReportMode.valueOf(report_conf.getString("Mode")));
            settings.setReport(r);
            // Select a subset of tags in the environment.
            int filters_num = 2;
            List<? extends Config> filters_conf = config.getConfigList("filters");
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            for(int i=0; i<filters_num && i<filters_conf.size(); ++i) {
                MethodHandle mh = lookup.findVirtual(FilterSettings.class, "getTagFilter"+(i+1),
                        MethodType.methodType(TagFilter.class));
//                TagFilter tf = settings.getFilters().getTagFilter1();
                TagFilter tf = null;
                try {
                    tf = (TagFilter) mh.invokeExact(settings.getFilters());
                } catch (Throwable th){
                    System.out.println("Filter"+(i+1)+" is wrong");
                    th.printStackTrace();
                }
                Config tf_conf = filters_conf.get(i);
                tf.setBitPointer(tf_conf.getInt("BitPointer"));
                tf.setBitCount(tf_conf.getInt("BitCount"));
                tf.setMemoryBank(MemoryBank.valueOf(tf_conf.getString("MemoryBank")));
                tf.setFilterOp(TagFilterOp.valueOf(tf_conf.getString("FilterOp")));
                // If the first 32 bits of a tag's ID matches "20210414", it will reply to the reader.
                tf.setTagMask(tf_conf.getString("TagMask"));
            }
            try {
                String settings_path = ReaderSettings.class.getClassLoader().getResource("settings.json").toURI().getPath();
//            settings_path = URLDecoder.decode(settings_path, StandardCharsets.UTF_8.toString());
                settings.save(settings_path);
                System.out.println("阅读器设置已保存至 settings.json");
                System.out.println(settings_path);
            } catch (Exception e) {
                e.printStackTrace();
            }
            reader.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
