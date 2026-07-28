FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /workspace

COPY gradlew gradlew
COPY gradle gradle
COPY settings.gradle build.gradle ./
COPY src src

RUN chmod +x gradlew \
    && ./gradlew --no-daemon clean bootJar

FROM eclipse-temurin:21-jre-jammy

RUN groupadd --system demo \
    && useradd --system --gid demo --home-dir /app --shell /usr/sbin/nologin demo

WORKDIR /app

COPY --from=builder --chown=demo:demo \
    /workspace/build/libs/demo-grpc-server.jar \
    /app/demo-grpc-server.jar

USER demo

EXPOSE 9090

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/demo-grpc-server.jar"]
