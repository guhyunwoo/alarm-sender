# syntax=docker/dockerfile:1.7

############################
# 1) Builder
############################
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /workspace

# Gradle 래퍼와 빌드 스크립트만 먼저 복사해 의존성 해석 단계에서 캐시 적중률을 높인다.
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew

# 멀티 모듈 소스 복사
COPY bootstrap bootstrap
COPY core core
COPY usecase usecase
COPY infrastructure infrastructure
COPY platform platform

# 3개 부트스트랩 모듈의 실행 가능한 jar 를 한 번에 빌드한다.
# BuildKit 캐시 마운트로 Gradle 의존성/빌드 캐시를 재빌드 시 재활용한다.
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon \
        :bootstrap:notification:notification-api:bootJar \
        :bootstrap:notification:notification-worker:bootJar \
        :bootstrap:notification:notification-batch:bootJar \
        -x test

# *-SNAPSHOT.jar 패턴은 *-SNAPSHOT-plain.jar 를 매칭하지 않으므로
# bootJar 산출물(실행 가능 jar)만 안전하게 골라낼 수 있다.
RUN cp bootstrap/notification/notification-api/build/libs/*-SNAPSHOT.jar    /api.jar  \
 && cp bootstrap/notification/notification-worker/build/libs/*-SNAPSHOT.jar /worker.jar \
 && cp bootstrap/notification/notification-batch/build/libs/*-SNAPSHOT.jar  /batch.jar

############################
# 2) Runtime - notification-api
############################
FROM eclipse-temurin:21-jre-jammy AS api
WORKDIR /app
COPY --from=builder /api.jar /app/app.jar
EXPOSE 8080
# Healthcheck 는 docker-compose.yml 의 bash TCP 프로브가 담당한다 (jammy 기본 bash 사용).
ENTRYPOINT ["java","-XX:+UseContainerSupport","-jar","/app/app.jar"]

############################
# 3) Runtime - notification-worker
############################
FROM eclipse-temurin:21-jre-jammy AS worker
WORKDIR /app
COPY --from=builder /worker.jar /app/app.jar
ENTRYPOINT ["java","-XX:+UseContainerSupport","-jar","/app/app.jar"]

############################
# 4) Runtime - notification-batch
############################
FROM eclipse-temurin:21-jre-jammy AS batch
WORKDIR /app
COPY --from=builder /batch.jar /app/app.jar
ENTRYPOINT ["java","-XX:+UseContainerSupport","-jar","/app/app.jar"]
