FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

# 빌드 JVM 메모리 제한 (배포 박스 2GB 시절 도입 — 지금은 GitHub 호스팅 러너에서 빌드하므로
# 여유가 있지만, 상한 명시는 러너에서도 무해해 유지한다).
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
# 전체 테스트는 test.yml(PR·dev push)이 돌린다. 이 빌드는 GitHub 호스팅 러너에서 실행된다
# — 실측 피크 RSS ~2GB 가 배포 박스를 굳혔던 사고(docs 35 §11) 이후 빌드를 박스 밖으로
# 이전했다(docs 35 §8-A). 박스는 완성된 이미지를 ECR 에서 pull 만 한다.
RUN ./gradlew clean test --tests "*DocsTest" bootJar --no-daemon \
      -PtestMaxHeap=384m -PtestMaxMetaspace=256m

FROM eclipse-temurin:21-jre

WORKDIR /app

ENV TZ=UTC

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080

# MaxRAMPercentage 는 "힙만" 계산한다 — compose 의 mem_limit: 1500m 과 짝으로 60% = 힙 900m,
# 비힙(메타스페이스·코드캐시·스레드 스택 등 ~300m+)에 600m 를 남긴다. 75% 였다면 힙이 상한까지
# 자란 순간 cgroup 을 넘겨 "활성 색 OOM-kill → restart 30초 = 502" — 블루-그린이 없애려는
# 바로 그 증상이 된다 (#92 리뷰 반영, 루트 docs/39).
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=60.0", "-jar", "app.jar"]