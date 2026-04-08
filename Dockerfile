# ─── Stage 1: dependency cache ────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS deps

RUN apk add --no-cache maven

WORKDIR /app

# Copy only the POM first so this layer is cached until dependencies change
COPY pom.xml .
RUN mvn dependency:go-offline -B

# ─── Stage 2: build ───────────────────────────────────────────────────────────
FROM deps AS builder

# Copy source and build
COPY src ./src
RUN mvn clean package -DskipTests -B && \
    java -Djarmode=tools -jar target/iqscaffold-user-service-*.jar \
         extract --layers --destination target/layers

# ─── Stage 3: runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache curl tzdata && \
    rm -rf /var/cache/apk/* && \
    addgroup -g 1001 -S appuser && \
    adduser  -u 1001 -S appuser -G appuser -s /bin/false -h /app

WORKDIR /app
RUN mkdir -p logs tmp && chown -R appuser:appuser /app

# Layered copy — most-stable layers first for best cache reuse
COPY --from=builder --chown=appuser:appuser /app/target/layers/dependencies/          ./
COPY --from=builder --chown=appuser:appuser /app/target/layers/spring-boot-loader/    ./
COPY --from=builder --chown=appuser:appuser /app/target/layers/snapshot-dependencies/ ./
COPY --from=builder --chown=appuser:appuser /app/target/layers/application/           ./

USER appuser

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=90s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health/readiness || exit 1

ENV JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=/app/tmp"

ENV JAVA_OPTS="\
    -XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:+UseG1GC \
    -XX:+UseStringDeduplication \
    -Djava.security.egd=file:/dev/./urandom \
    -Dspring.backgroundpreinitializer.ignore=true \
    -Dlogging.config=classpath:logback-spring.xml"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
