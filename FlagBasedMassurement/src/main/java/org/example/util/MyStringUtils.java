package org.example.util;

public final class MyStringUtils { // final类防止继承
    private MyStringUtils() { // 私有构造器禁止实例化
        throw new AssertionError("工具类不可实例化");
    }

    public static boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }

    public static String reverse(String str) {
        return str == null ? null : new StringBuilder(str).reverse().toString();
    }

    /**
     * 移除字符串末尾的指定后缀
     * 如果字符串以指定后缀结尾，则移除该后缀并返回剩余部分；否则返回原字符串
     *
     * @param str    原始字符串
     * @param suffix 要移除的后缀
     * @return 移除后缀后的字符串，若不以后缀结尾则返回原字符串
     */
    public static String removeSuffix(String str, String suffix) {
        if (str == null || suffix == null) {
            return str;
        }

        // 检查字符串是否以指定后缀结尾
        if (str.endsWith(suffix)) {
            // 移除后缀并返回剩余部分
            return str.substring(0, str.length() - suffix.length());
        }

        // 不以后缀结尾，返回原字符串
        return str;
    }
}
