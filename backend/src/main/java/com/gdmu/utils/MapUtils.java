package com.gdmu.utils;

import java.util.HashMap;
import java.util.Map;

public class MapUtils {
    
    public static Map<String, Object> of(String key1, Object value1) {
        Map<String, Object> map = new HashMap<>();
        map.put(key1, value1);
        return map;
    }
    
    public static Map<String, Object> of(String key1, Object value1, String key2, Object value2) {
        Map<String, Object> map = new HashMap<>();
        map.put(key1, value1);
        map.put(key2, value2);
        return map;
    }
    
    public static Map<String, Object> of(String key1, Object value1, String key2, Object value2, String key3, Object value3) {
        Map<String, Object> map = new HashMap<>();
        map.put(key1, value1);
        map.put(key2, value2);
        map.put(key3, value3);
        return map;
    }
    
    public static Map<String, Object> of(String key1, Object value1, String key2, Object value2, String key3, Object value3, String key4, Object value4) {
        Map<String, Object> map = new HashMap<>();
        map.put(key1, value1);
        map.put(key2, value2);
        map.put(key3, value3);
        map.put(key4, value4);
        return map;
    }
    
    public static Map<String, Object> of(String key1, Object value1, String key2, Object value2, String key3, Object value3, String key4, Object value4, String key5, Object value5) {
        Map<String, Object> map = new HashMap<>();
        map.put(key1, value1);
        map.put(key2, value2);
        map.put(key3, value3);
        map.put(key4, value4);
        map.put(key5, value5);
        return map;
    }
    
    public static Map<String, Object> of(String key1, Object value1, String key2, Object value2, String key3, Object value3, String key4, Object value4, String key5, Object value5, String key6, Object value6) {
        Map<String, Object> map = new HashMap<>();
        map.put(key1, value1);
        map.put(key2, value2);
        map.put(key3, value3);
        map.put(key4, value4);
        map.put(key5, value5);
        map.put(key6, value6);
        return map;
    }
    
    public static Map<String, Object> of(String key1, Object value1, String key2, Object value2, String key3, Object value3, String key4, Object value4, String key5, Object value5, String key6, Object value6, String key7, Object value7) {
        Map<String, Object> map = new HashMap<>();
        map.put(key1, value1);
        map.put(key2, value2);
        map.put(key3, value3);
        map.put(key4, value4);
        map.put(key5, value5);
        map.put(key6, value6);
        map.put(key7, value7);
        return map;
    }
    
    public static Map<String, Object> of(String key1, Object value1, String key2, Object value2, String key3, Object value3, String key4, Object value4, String key5, Object value5, String key6, Object value6, String key7, Object value7, String key8, Object value8) {
        Map<String, Object> map = new HashMap<>();
        map.put(key1, value1);
        map.put(key2, value2);
        map.put(key3, value3);
        map.put(key4, value4);
        map.put(key5, value5);
        map.put(key6, value6);
        map.put(key7, value7);
        map.put(key8, value8);
        return map;
    }
    
    public static Map<String, Object> builder() {
        return new HashMap<>();
    }
    
    public static Map<String, Object> put(Map<String, Object> map, String key, Object value) {
        map.put(key, value);
        return map;
    }
    
    public static Map<String, Object> putAll(Map<String, Object> target, Map<String, Object> source) {
        if (target != null && source != null) {
            target.putAll(source);
        }
        return target;
    }
    
    public static Object get(Map<String, Object> map, String key) {
        return map != null ? map.get(key) : null;
    }
    
    public static String getString(Map<String, Object> map, String key) {
        Object value = get(map, key);
        return value != null ? value.toString() : null;
    }
    
    public static Integer getInteger(Map<String, Object> map, String key) {
        Object value = get(map, key);
        if (value == null) {
            return null;
        }
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Long) {
            return ((Long) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
    
    public static Long getLong(Map<String, Object> map, String key) {
        Object value = get(map, key);
        if (value == null) {
            return null;
        }
        if (value instanceof Long) {
            return (Long) value;
        }
        if (value instanceof Integer) {
            return ((Integer) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
    
    public static Double getDouble(Map<String, Object> map, String key) {
        Object value = get(map, key);
        if (value == null) {
            return null;
        }
        if (value instanceof Double) {
            return (Double) value;
        }
        if (value instanceof Integer) {
            return ((Integer) value).doubleValue();
        }
        if (value instanceof Long) {
            return ((Long) value).doubleValue();
        }
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
    
    public static Boolean getBoolean(Map<String, Object> map, String key) {
        Object value = get(map, key);
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return null;
    }
    
    public static Object getOrDefault(Map<String, Object> map, String key, Object defaultValue) {
        Object value = get(map, key);
        return value != null ? value : defaultValue;
    }
    
    public static String getOrDefaultString(Map<String, Object> map, String key, String defaultValue) {
        String value = getString(map, key);
        return value != null ? value : defaultValue;
    }
    
    public static Integer getOrDefaultInteger(Map<String, Object> map, String key, Integer defaultValue) {
        Integer value = getInteger(map, key);
        return value != null ? value : defaultValue;
    }
    
    public static Long getOrDefaultLong(Map<String, Object> map, String key, Long defaultValue) {
        Long value = getLong(map, key);
        return value != null ? value : defaultValue;
    }
    
    public static Double getOrDefaultDouble(Map<String, Object> map, String key, Double defaultValue) {
        Double value = getDouble(map, key);
        return value != null ? value : defaultValue;
    }
    
    public static Boolean getOrDefaultBoolean(Map<String, Object> map, String key, Boolean defaultValue) {
        Boolean value = getBoolean(map, key);
        return value != null ? value : defaultValue;
    }
    
    public static boolean containsKey(Map<String, Object> map, String key) {
        return map != null && map.containsKey(key);
    }
    
    public static boolean containsValue(Map<String, Object> map, Object value) {
        return map != null && map.containsValue(value);
    }
    
    public static boolean isEmpty(Map<String, Object> map) {
        return map == null || map.isEmpty();
    }
    
    public static boolean isNotEmpty(Map<String, Object> map) {
        return !isEmpty(map);
    }
    
    public static int size(Map<String, Object> map) {
        return map != null ? map.size() : 0;
    }
}
