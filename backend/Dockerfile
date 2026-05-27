# syntax=docker/dockerfile:1.7
# Story 4.6 — Backend container image (multi-stage build).
# Builder uses the project's Gradle wrapper for version pinning; runtime uses
# JRE-only Eclipse Temurin to keep the production image lean.

# ---- Stage 1: builder (Gradle wrapper + JDK 21) ----
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /workspace

# Copy build config + wrapper first so dependency resolution caches independently of source changes.
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN chmod +x gradlew \
 && ./gradlew --no-daemon dependencies || true

# Now copy sources and produce the bootJar. Tests run in CI, not in the image build.
COPY src ./src
RUN ./gradlew --no-daemon bootJar -x test

# ---- Stage 2: runtime (JRE 21, non-root, KST) ----
FROM eclipse-temurin:21-jre-jammy

ENV TZ=Asia/Seoul \
    JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# curl is required for HEALTHCHECK; tzdata applies the TZ env reliably.
# Non-root user (uid 1000) — writable state lives in mounted volumes only.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl tzdata \
 && rm -rf /var/lib/apt/lists/* \
 && groupadd -r smcs --gid 1000 \
 && useradd -r --uid 1000 --gid 1000 --shell /usr/sbin/nologin smcs

WORKDIR /app
COPY --from=builder /workspace/build/libs/smcs-*.jar app.jar

# Used by docker compose + nginx upstream — AC5.
# show-details=never (Deviation #6) returns only {"status":"UP"} so grep is enough.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsS http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

USER smcs
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
