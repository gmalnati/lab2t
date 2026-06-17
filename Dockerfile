# Multi-stage build for the Spring Boot / Kotlin pricing service.
# Small, fast, secure — the pattern from Block 4 of the deck.
#
# Build & run locally:
#   docker build -t workshop-application:dev .
#   docker run --rm -p 8080:8080 workshop-application:dev
#
# In the workshop you'll tag it for the shared registry (Lab 3):
#   docker build -t registry.ff26.it/workshop-application:ec07-$(git rev-parse --short HEAD) .

# ---- Stage 1: build the executable jar with the Gradle wrapper ----
FROM eclipse-temurin:17-jdk AS build
WORKDIR /src

# Copy the build definition first so dependency resolution can cache.
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

# Now the source, then build only the Spring Boot fat jar (not the plain jar).
COPY src ./src
RUN ./gradlew --no-daemon clean bootJar

# ---- Stage 2: tiny JRE-only runtime ----
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /src/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
