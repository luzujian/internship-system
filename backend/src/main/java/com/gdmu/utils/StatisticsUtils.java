package com.gdmu.utils;

import java.util.Map;

public class StatisticsUtils {
    
    public static double calculatePercentage(int numerator, int denominator) {
        if (denominator == 0) {
            return 0.0;
        }
        return (double) numerator / denominator * 100;
    }
    
    public static double calculatePercentage(long numerator, long denominator) {
        if (denominator == 0) {
            return 0.0;
        }
        return (double) numerator / denominator * 100;
    }
    
    public static double roundToOneDecimal(double value) {
        return Math.round(value * 10) / 10.0;
    }
    
    public static double roundToTwoDecimals(double value) {
        return Math.round(value * 100) / 100.0;
    }
    
    public static int calculateRate(int count, int total) {
        if (total == 0) {
            return 0;
        }
        return (int) Math.round(calculatePercentage(count, total));
    }
    
    public static int calculateRate(long count, long total) {
        if (total == 0) {
            return 0;
        }
        return (int) Math.round(calculatePercentage(count, total));
    }
    
    public static int sumArray(int[] array) {
        int sum = 0;
        for (int value : array) {
            sum += value;
        }
        return sum;
    }
    
    public static double average(int[] array) {
        if (array == null || array.length == 0) {
            return 0.0;
        }
        return (double) sumArray(array) / array.length;
    }
    
    public static double average(double[] array) {
        if (array == null || array.length == 0) {
            return 0.0;
        }
        double sum = 0;
        for (double value : array) {
            sum += value;
        }
        return sum / array.length;
    }
    
    public static int max(int[] array) {
        if (array == null || array.length == 0) {
            return 0;
        }
        int max = array[0];
        for (int value : array) {
            if (value > max) {
                max = value;
            }
        }
        return max;
    }
    
    public static int min(int[] array) {
        if (array == null || array.length == 0) {
            return 0;
        }
        int min = array[0];
        for (int value : array) {
            if (value < min) {
                min = value;
            }
        }
        return min;
    }
    
    public static int safeInt(Integer value) {
        return value != null ? value : 0;
    }
    
    public static long safeLong(Long value) {
        return value != null ? value : 0L;
    }
    
    public static double safeDouble(Double value) {
        return value != null ? value : 0.0;
    }
    
    public static boolean safeBoolean(Boolean value) {
        return Boolean.TRUE.equals(value);
    }
    
    public static int safeInt(Integer value, int defaultValue) {
        return value != null ? value : defaultValue;
    }
    
    public static long safeLong(Long value, long defaultValue) {
        return value != null ? value : defaultValue;
    }
    
    public static double safeDouble(Double value, double defaultValue) {
        return value != null ? value : defaultValue;
    }
    
    public static int increment(Map<String, Integer> map, String key) {
        int count = map.getOrDefault(key, 0);
        map.put(key, count + 1);
        return count + 1;
    }
    
    public static long incrementLong(Map<String, Long> map, String key) {
        long count = map.getOrDefault(key, 0L);
        map.put(key, count + 1);
        return count + 1;
    }
    
    public static int incrementInt(Map<Long, Integer> map, Long key) {
        int count = map.getOrDefault(key, 0);
        map.put(key, count + 1);
        return count + 1;
    }
    
    public static int getOrDefaultInt(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Long) {
            return ((Long) value).intValue();
        }
        if (value instanceof Double) {
            return ((Double) value).intValue();
        }
        return defaultValue;
    }
    
    public static String getOrDefaultString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }
}
