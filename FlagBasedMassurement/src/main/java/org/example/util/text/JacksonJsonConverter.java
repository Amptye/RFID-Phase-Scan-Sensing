package org.example.util.text;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

public class JacksonJsonConverter {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static String convertToJson(Object data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JacksonException e) {
            throw new RuntimeException("JSON转换失败", e);
        }
    }

    // 测试用例
//    public static void main(String[] args) {
//        Map<String, Integer> testData = Map.of(
//                "temperature", 25,
//                "humidity", 60,
//                "pressure", 1013
//        );
//
//        System.out.println(convertToJson(testData));
//    }
}