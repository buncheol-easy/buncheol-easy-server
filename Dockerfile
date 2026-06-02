FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

# Gradle 빌드 JVM 힙 제한 (t3.small 2GB OOM 방지)
ENV GRADLE_OPTS="-Dorg.gradle.jvmargs=-Xmx768m"

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle

RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

COPY src ./src

# API 문서 생성을 위해 Docs 테스트는 실행 (H2 + Mock 사용, 외부 의존성 없음)
# 그 외 테스트는 CI/PR 단계에서 실행되므로 빌드 시간 절약 위해 스킵
RUN ./gradlew clean test --tests "*DocsTest" bootJar --no-daemon

FROM eclipse-temurin:21-jre

WORKDIR /app

ENV TZ=UTC

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]