# EventMesh A2A Agent Client Dockerfile
FROM openjdk:11-jre-slim

# 设置工作目录
WORKDIR /app

# 复制可执行JAR文件
COPY build/libs/a2a-agent-client-1.0.0-executable.jar app.jar

# 创建日志目录
RUN mkdir -p /app/logs

# 设置环境变量
ENV JAVA_OPTS="-Xmx512m -Xms256m"
ENV EVENTMESH_A2A_ENABLED=true
ENV EVENTMESH_SERVER_HOST=localhost
ENV EVENTMESH_SERVER_PORT=10105

# 暴露端口（如果需要）
EXPOSE 8080

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/health || exit 1

# 启动命令
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
