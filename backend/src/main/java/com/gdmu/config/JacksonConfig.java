package com.gdmu.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.TimeZone;

/**
 * Jackson 配置类，支持多种日期格式的解析
 */
@Configuration
public class JacksonConfig {

    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        
        // 设置时区
        mapper.setTimeZone(TimeZone.getTimeZone("GMT+8"));
        
        // 禁用将日期写为时间戳
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        // 忽略未知属性
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        
        // 设置默认日期格式
        mapper.setDateFormat(new SimpleDateFormat(DATE_TIME_PATTERN));
        
        // 注册Java 8日期时间模块
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);
        javaTimeModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(dateTimeFormatter));
        javaTimeModule.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(dateTimeFormatter));
        mapper.registerModule(javaTimeModule);
        
        // 注册自定义日期反序列化器
        SimpleModule module = new SimpleModule();
        module.addDeserializer(Date.class, new MultiFormatDateDeserializer());
        mapper.registerModule(module);
        
        return mapper;
    }

    /**
     * 支持多种日期格式的反序列化器
     */
    public static class MultiFormatDateDeserializer extends JsonDeserializer<Date> {
        
        private static final String[] DATE_FORMATS = {
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd",
            "yyyy/MM/dd HH:mm:ss",
            "yyyy/MM/dd"
        };

        @Override
        public Date deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            String dateStr = p.getText();
            if (dateStr == null || dateStr.trim().isEmpty()) {
                return null;
            }
            
            dateStr = dateStr.trim();
            
            // 尝试解析时间戳
            try {
                long timestamp = Long.parseLong(dateStr);
                return new Date(timestamp);
            } catch (NumberFormatException ignored) {
                // 不是时间戳，继续尝试其他格式
            }
            
            // 尝试各种日期格式
            for (String format : DATE_FORMATS) {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat(format);
                    // 对于带Z的UTC格式，设置UTC时区
                    if (format.contains("'Z'") || dateStr.endsWith("Z")) {
                        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                    } else {
                        sdf.setTimeZone(TimeZone.getTimeZone("GMT+8"));
                    }
                    sdf.setLenient(false);
                    return sdf.parse(dateStr);
                } catch (ParseException ignored) {
                    // 尝试下一个格式
                }
            }
            
            throw new IOException("无法解析日期: " + dateStr);
        }
    }
}
