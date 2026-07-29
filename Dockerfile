FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

# 빌드 JVM 메모리 제한 (배포 박스 t3.small 2GB 기준).
#
# ⚠️ org.gradle.jvmargs 는 Gradle 기본값 문자열을 통째로 대체한다. 기본값에 있던 MaxMetaspaceSize 가
# 사라지므로 여기서 다시 명시하지 않으면 메타스페이스가 무제한이 된다.
#
# ⚠️ GRADLE_OPTS 는 Gradle 자체 JVM 에만 적용된다. 테스트는 별도 JVM 으로 포크되며 그쪽 상한은
# build.gradle 의 maxHeapSize / MaxMetaspaceSize 가 정한다(-PtestMaxHeap 등으로 여기서 넘긴다).
ENV GRADLE_OPTS="-Dorg.gradle.jvmargs=-Xmx512m -XX:MaxMetaspaceSize=256m"

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle

RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

COPY src ./src

# API 문서(openapi3.yaml)는 Docs 테스트가 남기는 스니펫으로 생성되므로 여기서 실행해야 한다.
# 건너뛰면 jar 에 169바이트짜리 빈 스펙이 들어간다.
#
# ⚠️ 나머지 테스트는 현재 어디서도 자동 실행되지 않는다 — .github/workflows 에 테스트 워크플로가 없다
# (claude-code-review / deploy-staging / deploy-production 셋뿐). 별도 과제로 CI 추가가 필요하다.
#
# 이 단계의 실측 피크 RSS 는 약 2GB(힙 상한 합계의 2배 이상 — 메타스페이스·스레드 스택 등 비힙 포함)로,
# 배포 박스 여유(~580MB)를 크게 넘는다. JVM 상한 조정으로는 5% 남짓밖에 줄지 않으므로
# 근본 해결은 빌드를 박스 밖으로 옮기거나 인스턴스를 키우는 것이다.
RUN ./gradlew clean test --tests "*DocsTest" bootJar --no-daemon \
      -PtestMaxHeap=384m -PtestMaxMetaspace=256m

FROM eclipse-temurin:21-jre

WORKDIR /app

ENV TZ=UTC

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]