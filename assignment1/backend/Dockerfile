# ---- build ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY src src
# Tests run in CI before Render deploys, so skip compiling/running them in the image build
RUN ./mvnw -B -q -Dmaven.test.skip=true package

# ---- run ----
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 vms
COPY --from=build /app/target/vms-backend-*.jar app.jar
USER vms
# Keep the JVM inside Render's 512 MB free-tier limit
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k"
# Render sets PORT (default 10000); the app reads it (see application.yml)
EXPOSE 10000
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
