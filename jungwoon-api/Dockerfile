# 1단계: 빌드 (의존성 레이어를 분리해 캐시 적중률을 높인다)
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY gradle gradle
COPY gradlew settings.gradle build.gradle gradle.properties ./
COPY common/build.gradle common/
COPY domain/build.gradle domain/
COPY infra/build.gradle infra/
COPY api/build.gradle api/
RUN ./gradlew :api:dependencies --no-daemon > /dev/null 2>&1 || true

COPY common common
COPY domain domain
COPY infra infra
COPY api api
RUN ./gradlew :api:bootJar --no-daemon -x test

# 2단계: 실행
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

RUN groupadd --system app && useradd --system --gid app app
COPY --from=build /workspace/api/build/libs/app.jar app.jar
USER app

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Duser.timezone=Asia/Seoul"
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD ["sh", "-c", "curl -fsS http://localhost:8080/actuator/health/liveness || exit 1"]

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
