FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

# Gradle 빌드 JVM 힙 제한 (t3.small 2GB).
# 배포는 앱·프론트·모니터링 컨테이너(합계 ~900MB)가 떠 있는 상태에서 돌기 때문에 빌드가 쓸 수 있는 여유는 ~580MB 뿐이다.
# 여기에 더해 테스트는 별도 JVM 으로 포크되며 그 힙은 GRADLE_OPTS 가 아니라 build.gradle 의 maxHeapSize 가 정한다.
# 둘을 합친 요구량이 여유를 넘으면 스왑 스래싱으로 빌드가 사실상 멈춘다(2026-07-29 배포 2건 중단).
ENV GRADLE_OPTS="-Dorg.gradle.jvmargs=-Xmx512m"

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