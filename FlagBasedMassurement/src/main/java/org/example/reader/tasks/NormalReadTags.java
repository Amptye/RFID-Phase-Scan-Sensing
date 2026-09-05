package org.example.reader.tasks;

import com.impinj.octane.*;
import com.typesafe.config.ConfigException;
import org.example.reader.ReaderOperations;
import org.example.reader.listener.TagOpCompleteListenerImplementation;
import org.example.reader.listener.TagReportListenerImplementation;

import java.util.ArrayList;
import java.util.Scanner;

public class NormalReadTags {
    public static ImpinjReader READER = ReaderOperations.READER;

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
        System.out.println("=== 普通Impinj读标签 ===");
        try {
            // 先停止阅读器
            ReaderOperations.stopReader();

            TagFilter filter = new TagFilter();
            filter.setMemoryBank(MemoryBank.User);
            filter.setBitPointer(0);
            filter.setBitCount(32);
            filter.setTagMask("00000001");
            filter.setFilterOp(TagFilterOp.Match);

            TagSelectFilter selectFilter = new TagSelectFilter();
            selectFilter.setMemoryBank(MemoryBank.User);
            selectFilter.setBitPointer(0);
            selectFilter.setBitCount(32);
            selectFilter.setTagMask(TagData.fromHexString("0000-0001").toHexString());
            selectFilter.setMatchingAction(TagFilterStateUnawareAction.Select);
            selectFilter.setNonMatchingAction(TagFilterStateUnawareAction.Unselect);

            Settings settings = READER.querySettings();
            FilterSettings filters = settings.getFilters();
            filters.setMode(TagFilterMode.None);
//            filters.setMode(TagFilterMode.OnlyFilter1);
//            filters.setTagFilter1(filter);
//            filters.setTagFilterVerificationMode(true);
            filters.setMode(TagFilterMode.UseTagSelectFilters);
            filters.getTagSelectFilterList().add(selectFilter);
            settings.setRfMode(1002);   // Auto Set Dense Reader Deep Scan
//            settings.setSearchMode(SearchMode.ReaderSelected);
            READER.applySettings(settings);


            READER.setTagReportListener(new TagReportListenerImplementation());
            READER.setTagOpCompleteListener(new TagOpCompleteListenerImplementation());

            TagOpSequence read_seq = new TagOpSequence();
            read_seq.setOps(new ArrayList<TagOp>());
            read_seq.setExecutionCount((short) 0); // 无限执行
            read_seq.setState(SequenceState.Disabled);
//            read_seq.setState(SequenceState.Active);
            read_seq.setId(1);
            read_seq.setTargetTag(null);
//            read_seq.setTargetTag(new TargetTag());
//            read_seq.getTargetTag().setBitPointer((short) 0);
//            read_seq.getTargetTag().setMemoryBank(MemoryBank.User);
//            read_seq.getTargetTag().setData("00000001");
//            read_seq.getTargetTag().setBitPointer(BitPointers.Epc);
//            read_seq.getTargetTag().setMemoryBank(MemoryBank.Epc);
//            read_seq.getTargetTag().setData("E2801191A50300624EE7673C");
            TagReadOp epc_read = new TagReadOp();
            epc_read.setMemoryBank(MemoryBank.User);
            epc_read.setWordCount((short) 2);
            epc_read.setWordPointer((short) 0);
            epc_read.setAccessPassword(TagData.fromHexString("0000-0001"));
            read_seq.getOps().add(epc_read);
            READER.addOpSequence(read_seq);

            TagOpSequence write_seq = new TagOpSequence();
            write_seq.setOps(new ArrayList<>());
            write_seq.setExecutionCount((short) 1);
            write_seq.setState(SequenceState.Disabled);
//            write_seq.setState(SequenceState.Active);
            write_seq.setId(2);
            write_seq.setTargetTag(null);
//            write_seq.setTargetTag(new TargetTag());
//            write_seq.getTargetTag().setBitPointer(BitPointers.Epc);
//            write_seq.getTargetTag().setMemoryBank(MemoryBank.Epc);
//            write_seq.getTargetTag().setData("E2801191A50300624EE7673C");
//            TagWriteOp pwd_write = new TagWriteOp();
//            pwd_write.setMemoryBank(MemoryBank.Reserved);
//            pwd_write.setWordPointer((short) 2);
//            pwd_write.setData(TagData.fromHexString("0000-0001"));
//            write_seq.getOps().add(pwd_write);
            TagWriteOp protect = new TagWriteOp();
            protect.setMemoryBank(MemoryBank.Reserved);
            protect.setWordPointer((short) 4);
            protect.setData(TagData.fromHexString("0000"));
            protect.setAccessPassword(TagData.fromHexString("0000-0001"));
            write_seq.getOps().add(protect);
            READER.addOpSequence(write_seq);

            // 启动阅读器
            ReaderOperations.startReader();

            // 按回车停止阅读器
            System.out.println("Press Enter to exit.");
            Scanner s = new Scanner(System.in);
            s.nextLine();

            // 停止阅读器
            ReaderOperations.stopReader();

            System.out.println("=== 已扫描各频率BPI ===");
        } catch (ConfigException.IO ec){
            System.out.println("无法找到 config.conf 文件");
        } catch (OctaneSdkException ex) {
            System.out.println(ex.getMessage());
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
            ex.printStackTrace(System.out);
        }
    }
}
