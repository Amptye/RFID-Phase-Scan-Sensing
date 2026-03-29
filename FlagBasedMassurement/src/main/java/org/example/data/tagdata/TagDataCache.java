package org.example.data.tagdata;

import java.time.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

//@Component
public class TagDataCache<T extends BasicTagData> {
    private final Map<String, T> tagId_Data = new ConcurrentHashMap<>();

    // 更新数据
    public void updateData(String tagId, T value) {
        tagId_Data.put(tagId, value);
    }

    // 获取所有数据
    public Map<String, T> getAllData() {
        return new HashMap<>(tagId_Data);
    }

    // 获取一定时间内的数据
    public Map<String, T> getInTimeData(Duration maxDuration){
        Instant now = Instant.now();
        return getAllData().entrySet().stream()
                .filter(entry -> {
                    Duration elapsed = Duration.between(entry.getValue().getReadTime(), now);
                    return elapsed.compareTo(maxDuration) <= 0;
                })
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue
                ));
    }

    // 获取单个标签数据
    public T getTagData(String tagId) {
        return tagId_Data.get(tagId);
    }
}
