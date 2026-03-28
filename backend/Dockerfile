FROM eclipse-temurin:25-jre-alpine

# 创建应用目录
WORKDIR /app

# 创建文件上传目录
RUN mkdir -p /app/uploads && chmod 755 /app/uploads

# 复制 JAR 文件
COPY target/*.jar app.jar

# JVM 参数（适配 Java 25 + Netty）
ENV JAVA_OPTS="-Xms512m -Xmx2g \
  --add-opens=java.base/java.nio=ALL-UNNAMED \
  --add-opens=java.base/jdk.internal.misc=ALL-UNNAMED \
  --add-opens=java.base/sun.nio.ch=ALL-UNNAMED \
  --add-opens=java.base/sun.security.util=ALL-UNNAMED \
  --add-opens=java.base/java.lang.reflect=ALL-UNNAMED \
  --add-opens=java.base/java.util.concurrent=ALL-UNNAMED \
  --add-opens=java.base/java.lang=ALL-UNNAMED \
  -Dio.netty.noUnsafe=true \
  -Dio.netty.transport.noNative=true \
  -Dio.netty.leakDetection.level=DISABLED \
  -Dio.netty.allocator.type=unpooled \
  -Dio.netty.recycler.maxCapacityPerThread=0"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
