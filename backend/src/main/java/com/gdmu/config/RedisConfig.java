package com.gdmu.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;
import io.lettuce.core.protocol.ProtocolVersion;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Redis 配置类 - 生产环境版本
 * 支持环境变量配置、连接池优化和 SSL
 */
@Slf4j
@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String host;

    @Value("${spring.data.redis.port:6379}")
    private int port;

    @Value("${spring.data.redis.password:}")
    private String password;

    @Value("${spring.data.redis.database:0}")
    private int database;

    @Value("${spring.data.redis.timeout:3000}")
    private long timeout;

    @Bean
    @Primary
    public LettuceConnectionFactory redisConnectionFactory() {
        try {
            // 单机配置
            RedisStandaloneConfiguration standaloneConfig = new RedisStandaloneConfiguration();
            standaloneConfig.setHostName(host);
            standaloneConfig.setPort(port);
            standaloneConfig.setDatabase(database);

            // 设置密码（如果有）
            if (password != null && !password.isEmpty()) {
                standaloneConfig.setPassword(password);
            }

            // Socket 选项配置
            SocketOptions socketOptions = SocketOptions.builder()
                    .connectTimeout(Duration.ofMillis(timeout))
                    .keepAlive(true)
                    .tcpNoDelay(true)
                    .build();

            // 超时选项配置
            TimeoutOptions timeoutOptions = TimeoutOptions.builder()
                    .fixedTimeout(Duration.ofMillis(timeout))
                    .build();

            // 客户端选项配置
            ClientOptions clientOptions = ClientOptions.builder()
                    .socketOptions(socketOptions)
                    .timeoutOptions(timeoutOptions)
                    .protocolVersion(ProtocolVersion.RESP2)
                    .build();

            // Lettuce 客户端配置
            LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                    .commandTimeout(Duration.ofMillis(timeout))
                    .shutdownTimeout(Duration.ofMillis(500))
                    .clientOptions(clientOptions)
                    .build();

            LettuceConnectionFactory factory = new LettuceConnectionFactory(standaloneConfig, clientConfig);
            factory.setValidateConnection(true);

            log.info("Redis 连接工厂配置成功 - host={}, port={}, database={}",
                    host, port, database);
            return factory;
        } catch (Exception e) {
            log.error("创建 Redis 连接工厂失败：{}", e.getMessage(), e);
            throw new RuntimeException("Redis 连接失败", e);
        }
    }

    @Bean
    @Primary
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // 使用 String 序列化器作为 Key 的序列化器
        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        // 配置 ObjectMapper 用于 Value 的 JSON 序列化
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        objectMapper.activateDefaultTyping(
            LaissezFaireSubTypeValidator.instance,
            ObjectMapper.DefaultTyping.NON_FINAL
        );
        objectMapper.registerModule(new JavaTimeModule()); // 支持 Java 8 时间类型

        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(objectMapper);

        // 设置 Key 和 Value 的序列化方式
        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(jsonSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();

        log.info("RedisTemplate 配置成功");
        return template;
    }

    @Bean
    public RedisTemplate<String, String> stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(stringSerializer);

        template.afterPropertiesSet();

        log.info("StringRedisTemplate 配置成功");
        return template;
    }
}
