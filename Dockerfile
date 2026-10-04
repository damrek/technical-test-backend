# syntax=docker/dockerfile:1

# ---- build stage ---------------------------------------------------------------------------------
# The JDK exists only to produce the jar. Nothing from the toolchain reaches the final image.
FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace

# The wrapper and the build scripts land before the sources on purpose: these files change rarely,
# so resolving dependencies stays inside a cached layer while application code changes every commit.
COPY gradlew ./
COPY gradle ./gradle
COPY settings.gradle build.gradle ./

# Warm the dependency cache as its own layer. Best-effort by design: a failure here must not fail
# the image, because resolution happens again for real during bootJar below.
RUN chmod +x gradlew \
 && ./gradlew --no-daemon --console=plain dependencies --configuration runtimeClasspath \
      > /dev/null 2>&1 || true

COPY src ./src

# Tests are deliberately skipped: they exercise the mock product API on port 3001, which does not
# exist while this image is being built. The suite runs in CI and locally, not inside the image.
#
# bootJar emits TWO jars: an executable ~25MB one and a thin -plain.jar. `COPY build/libs/*.jar`
# would match both, and Docker refuses to copy several sources into a single file destination, so
# the executable jar is selected by name and renamed to something stable.
RUN ./gradlew --no-daemon --console=plain clean bootJar -x test \
 && find build/libs -maxdepth 1 -name '*.jar' ! -name '*-plain.jar' \
      -exec mv {} /workspace/app.jar \;

# ---- runtime stage -------------------------------------------------------------------------------
FROM eclipse-temurin:21-jre AS runtime

WORKDIR /app
COPY --from=build /workspace/app.jar /app/app.jar

# Matches server.port in application.yml.
EXPOSE 5000

ENTRYPOINT ["java", "-jar", "/app/app.jar"]