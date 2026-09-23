# 1- Build stage
FROM eclipse-temurin:25-jdk-jammy AS builder
WORKDIR /app

# Cache dependencies
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline

# Build application
COPY src src
RUN ./mvnw package -DskipTests

# 2 - Run stage
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# The app only speaks gRPC; there is no HTTP endpoint to wget, so the container
# healthcheck goes through the grpc.health.v1.Health service instead.
ARG TARGETARCH=amd64
ARG GRPC_HEALTH_PROBE_VERSION=v0.4.39
ADD https://github.com/grpc-ecosystem/grpc-health-probe/releases/download/${GRPC_HEALTH_PROBE_VERSION}/grpc_health_probe-linux-${TARGETARCH} /bin/grpc_health_probe
RUN chmod +x /bin/grpc_health_probe

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 9090
# --enable-native-access silences the JDK 25 warning about Netty loading its
# native transport; without it the JVM blocks that call in a future release.
ENTRYPOINT ["java", "--enable-native-access=ALL-UNNAMED", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
