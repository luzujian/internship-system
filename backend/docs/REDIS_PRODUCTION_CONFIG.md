# Redis 生产环境优化配置方案

## 目录

1. [Redis 服务器配置](#一 redis 服务器配置)
2. [Spring Boot 配置优化](#二 spring-boot 配置优化)
3. [Java 代码配置优化](#三 java 代码配置优化)
4. [Docker 部署配置](#四 docker-部署配置)
5. [监控和维护](#五监控和维护)

---

## 一、Redis 服务器配置

### redis.conf 生产环境配置

```conf
# ===========================================
# Redis 生产环境配置文件
# 适用于实习管理系统
# ===========================================

################################## 网络配置 ##################################
# 绑定地址（生产环境建议绑定内网 IP）
bind 127.0.0.1

# 保护模式（生产环境必须开启）
protected-mode yes

# Redis 端口
port 6379

# TCP 连接 backlog 大小
tcp-backlog 511

# Unix socket 配置（本地部署可启用）
# unixsocket /tmp/redis.sock
# unixsocketperm 700

# 客户端超时时间（0 表示永不超时）
timeout 0

# TCP Keepalive（检测死连接）
tcp-keepalive 300

################################## 安全配置 ##################################
# 设置访问密码（生产环境必须设置！）
requirepass YourStrongRedisPassword2024!@#

# 重命名危险命令
rename-command FLUSHDB ""
rename-command FLUSHALL ""
rename-command DEBUG ""
rename-command CONFIG ""
rename-command KEYS ""

################################## 内存管理 ##################################
# 最大内存限制（根据服务器配置调整）
maxmemory 2gb

# 内存淘汰策略（推荐使用 allkeys-lru）
maxmemory-policy allkeys-lru

# LRU 采样数量
maxmemory-samples 10

################################## 持久化配置 ##################################
# RDB 持久化配置
save 900 1
save 300 10
save 60 10000

# RDB 文件名
dbfilename dump.rdb

# 工作目录
dir /var/lib/redis

# RDB 压缩
rdbcompression yes

# RDB 校验和
rdbchecksum yes

# AOF 持久化配置
appendonly yes
appendfilename "appendonly.aof"

# AOF 写入策略（推荐 everysec）
appendfsync everysec

# AOF 重写触发配置
auto-aof-rewrite-percentage 100
auto-aof-rewrite-min-size 64mb

# 加载 AOF 文件时是否校验
aof-load-truncated yes

# 是否启用无 fync 的 AOF 重写
aof-rewrite-incremental-fsync yes

################################## 性能优化 ##################################
# 慢查询日志阈值（微秒）
slowlog-log-slower-than 10000

# 慢查询日志最大条数
slowlog-max-len 128

# 延迟监控阈值（毫秒）
latency-monitor-threshold 100

################################## 连接池配置 ##################################
# 最大客户端连接数
maxclients 10000

################################## 日志配置 ##################################
# 日志级别
loglevel notice

# 日志文件路径
logfile /var/log/redis/redis-server.log

################################## 高级配置 ##################################
# Hash 结构优化
hash-max-ziplist-entries 512
hash-max-ziplist-value 64

# List 结构优化
list-max-ziplist-size -2
list-compress-depth 0

# Set 结构优化
set-max-intset-entries 512

# Sorted Set 结构优化
zset-max-ziplist-entries 128
zset-max-ziplist-value 64

# HyperLogLog 优化
hll-sparse-max-bytes 3000

# 主动 rehash
activerehashing yes

# 输出缓冲区限制
client-output-buffer-limit normal 0 0 0
client-output-buffer-limit replica 256mb 64mb 60
client-output-buffer-limit pubsub 32mb 8mb 60

# 动态 hz 配置
hz 10
dynamic-hz yes

# AOF 重写期间是否 fork 子进程
aof-rewrite-incremental-fsync yes
```

---

## 二、Spring Boot 配置优化

### application-prod.yml Redis 配置

```yaml
spring:
  data:
    redis:
      # Redis 服务器地址
      host: ${REDIS_HOST:localhost}
      # Redis 端口
      port: ${REDIS_PORT:6379}
      # Redis 密码（生产环境必须设置）
      password: ${REDIS_PASSWORD:}
      # 数据库索引
      database: ${REDIS_DATABASE:0}
      # 连接超时时间
      timeout: ${REDIS_TIMEOUT:3000ms}

      # Lettuce 连接池配置
      lettuce:
        pool:
          # 最大连接数（负数表示无限制）
          max-active: ${REDIS_POOL_MAX_ACTIVE:20}
          # 最大空闲连接数
          max-idle: ${REDIS_POOL_MAX_IDLE:10}
          # 最小空闲连接数
          min-idle: ${REDIS_POOL_MIN_IDLE:5}
          # 获取连接最大等待时间（负数表示一直等待）
          max-wait: ${REDIS_POOL_MAX_WAIT:3000ms}
        # 集群刷新配置
        cluster:
          refresh:
            # 自适应刷新
            adaptive: true
            # 刷新间隔
            period: 60000
        # 关闭超时时间
        shutdown-timeout: 500ms

      # 使用 Lettuce 客户端（默认）
      client-type: lettuce

  # 缓存配置
  cache:
    type: redis
    redis:
      # 缓存默认过期时间（毫秒）
      time-to-live: ${REDIS_CACHE_TTL:300000}
      # 不缓存 null 值
      cache-null-values: false
      # 允许动态覆盖缓存
      use-key-prefix: true
```

---

## 三、Java 代码配置优化

### RedisConfig.java 生产环境版本

```java
package com.gdmu.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfigurationFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Slf4j
@Configuration
@EnableCaching
public class RedisConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String host;

    @Value("${spring.data.redis.port:6379}")
    private int port;

    @Value("${spring.data.redis.password:}")
    private String password;

    @Value("${spring.data.redis.database:0}")
    private int database;

    @Value("${spring.data.redis.timeout:3000ms}")
    private Duration timeout;

    @Value("${spring.data.redis.lettuce.pool.max-active:20}")
    private int maxActive;

    @Value("${spring.data.redis.lettuce.pool.max-idle:10}")
    private int maxIdle;

    @Value("${spring.data.redis.lettuce.pool.min-idle:5}")
    private int minIdle;

    @Value("${spring.data.redis.lettuce.pool.max-wait:3000ms}")
    private Duration maxWait;

    @Value("${spring.cache.redis.time-to-live:300000}")
    private long cacheTtl;

    @Bean
    @Primary
    public LettuceConnectionFactory redisConnectionFactory() {
        try {
            // 单机配置
            RedisStandaloneConfiguration standaloneConfig = new RedisStandaloneConfiguration();
            standaloneConfig.setHostName(host);
            standaloneConfig.setPort(port);
            standaloneConfig.setDatabase(database);

            // 设置密码
            if (password != null && !password.isEmpty()) {
                standaloneConfig.setPassword(password);
            }

            // 连接池配置
            LettucePoolingClientConfigurationFactory poolingFactory = new LettucePoolingClientConfigurationFactory();

            // 客户端配置
            LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                    .commandTimeout(timeout)
                    .shutdownTimeout(Duration.ofMillis(500))
                    .useSsl() // 生产环境建议使用 SSL
                    .build();

            LettuceConnectionFactory factory = new LettuceConnectionFactory(standaloneConfig, clientConfig);
            factory.setValidateConnection(true);

            log.info("Redis 连接工厂配置成功 - host={}, port={}, database={}", host, port, database);
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

    @Bean
    public RedisCacheConfiguration cacheConfiguration() {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMillis(cacheTtl)) // 设置默认过期时间
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues(); // 不缓存 null 值
    }
}
```

---

## 四、Docker 部署配置

### docker-compose.yml

```yaml
version: '3.8'

services:
  redis:
    image: redis:7.2-alpine
    container_name: internship-redis
    restart: always
    ports:
      - "6379:6379"
    volumes:
      # 挂载配置文件
      - ./redis.conf:/usr/local/etc/redis/redis.conf
      # 挂载数据目录
      - redis-data:/data
      # 挂载日志目录
      - redis-logs:/var/log/redis
    command: redis-server /usr/local/etc/redis/redis.conf
    environment:
      - TZ=Asia/Shanghai
    networks:
      - internship-network
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 40s

  # Redis Commander 可视化工具（可选）
  redis-commander:
    image: rediscommander/redis-commander:latest
    container_name: internship-redis-commander
    restart: always
    environment:
      - REDIS_HOSTS=local:redis:6379:0:YourStrongRedisPassword2024!@#
      - HTTP_PORT=8081
      - PORT=8081
    ports:
      - "8081:8081"
    depends_on:
      - redis
    networks:
      - internship-network

volumes:
  redis-data:
  redis-logs:

networks:
  internship-network:
    driver: bridge
```

### 启动脚本

```bash
#!/bin/bash
# start-redis.sh

echo "==================================="
echo "启动 Redis 生产环境"
echo "==================================="

# 创建必要目录
mkdir -p ./redis-data ./redis-logs

# 启动 Redis
docker-compose up -d redis

# 等待 Redis 启动
echo "等待 Redis 启动..."
sleep 5

# 检查 Redis 状态
docker exec internship-redis redis-cli ping

# 查看 Redis 信息
docker exec internship-redis redis-cli info server

echo "==================================="
echo "Redis 启动完成！"
echo "可视化界面：http://localhost:8081"
echo "==================================="
```

---

## 五、监控和维护

### 1. 健康检查脚本

```bash
#!/bin/bash
# redis-health-check.sh

REDIS_HOST="localhost"
REDIS_PORT="6379"
REDIS_PASSWORD="YourStrongRedisPassword2024!@#"

# 检查 Redis 是否可连接
ping_result=$(redis-cli -h $REDIS_HOST -p $REDIS_PORT -a $REDIS_PASSWORD ping 2>/dev/null)

if [ "$ping_result" == "PONG" ]; then
    echo "✓ Redis 连接正常"

    # 检查内存使用率
    used_memory=$(redis-cli -h $REDIS_HOST -p $REDIS_PORT -a $REDIS_PASSWORD info memory | grep used_memory_human | cut -d: -f2 | tr -d '\r')
    echo "  内存使用：$used_memory"

    # 检查连接数
    connected_clients=$(redis-cli -h $REDIS_HOST -p $REDIS_PORT -a $REDIS_PASSWORD info clients | grep connected_clients | cut -d: -f2 | tr -d '\r')
    echo "  连接数：$connected_clients"

    # 检查持久化状态
    rdb_status=$(redis-cli -h $REDIS_HOST -p $REDIS_PORT -a $REDIS_PASSWORD info persistence | grep rdb_last_bgsave_status | cut -d: -f2 | tr -d '\r')
    echo "  RDB 备份状态：$rdb_status"

    exit 0
else
    echo "✗ Redis 连接失败"
    exit 1
fi
```

### 2. 备份脚本

```bash
#!/bin/bash
# redis-backup.sh

REDIS_HOST="localhost"
REDIS_PORT="6379"
REDIS_PASSWORD="YourStrongRedisPassword2024!@#"
BACKUP_DIR="/data/backups/redis"
DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_FILE="$BACKUP_DIR/dump_$DATE.rdb"

# 创建备份目录
mkdir -p $BACKUP_DIR

# 触发 RDB 保存
redis-cli -h $REDIS_HOST -p $REDIS_PORT -a $REDIS_PASSWORD BGSAVE

# 等待备份完成
sleep 5

# 复制 RDB 文件
cp /var/lib/redis/dump.rdb $BACKUP_FILE

# 删除 7 天前的备份
find $BACKUP_DIR -name "dump_*.rdb" -mtime +7 -delete

echo "Redis 备份完成：$BACKUP_FILE"
```

### 3. 监控指标

| 指标 | 阈值 | 说明 |
|------|------|------|
| 内存使用率 | >80% | 需要增加内存或优化数据 |
| 连接数 | >maxclients*0.8 | 需要增加最大连接数 |
| 命中率 | <90% | 缓存策略需要优化 |
| 慢查询数 | >10/分钟 | 需要优化查询 |
| 持久化失败 | >0 | 需要检查磁盘空间 |

### 4. 常用运维命令

```bash
# 查看 Redis 信息
redis-cli info

# 查看内存使用
redis-cli info memory

# 查看连接数
redis-cli info clients

# 查看慢查询
redis-cli slowlog get 10

# 查看大 Key
redis-cli --bigkeys

# 测试连接
redis-cli ping

# 清空数据库（生产环境慎用！）
redis-cli FLUSHDB

# 查看 Key 的数量
redis-cli DBSIZE

# 实时监控
redis-cli --stat
```

---

## 六、环境变量配置

### .env 文件更新

```env
# Redis 配置（生产环境）
REDIS_HOST=127.0.0.1
REDIS_PORT=6379
REDIS_DATABASE=0
REDIS_PASSWORD=YourStrongRedisPassword2024!@#
REDIS_TIMEOUT=3000

# Redis 连接池配置
REDIS_POOL_MAX_ACTIVE=20
REDIS_POOL_MAX_IDLE=10
REDIS_POOL_MIN_IDLE=5
REDIS_POOL_MAX_WAIT=3000

# Redis 缓存配置
REDIS_CACHE_TTL=300000
```

---

## 七、安全检查清单

部署前请确认：

- [ ] Redis 密码已设置为强密码
- [ ] 已禁用或重命名危险命令
- [ ] 已开启 protected-mode
- [ ] 已绑定内网 IP（不暴露公网）
- [ ] 已配置持久化（RDB + AOF）
- [ ] 已设置内存限制和淘汰策略
- [ ] 已配置慢查询日志
- [ ] 已配置监控告警
- [ ] 已配置定期备份

---

## 总结

| 配置项 | 开发环境 | 生产环境 |
|--------|----------|----------|
| 密码 | 可选 | **必须** |
| 持久化 | 可选 | **必须** |
| 内存限制 | 可选 | **必须** |
| 连接池 | 默认 | **优化配置** |
| 监控 | 可选 | **必须** |
| 备份 | 可选 | **必须** |

按照此方案配置后，你的 Redis 将具备：
- ✅ 高安全性（密码保护、命令禁用）
- ✅ 高可靠性（持久化、备份）
- ✅ 高性能（连接池、内存优化）
- ✅ 易维护（监控、日志）
