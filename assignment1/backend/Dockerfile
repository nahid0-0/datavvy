FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 vms
COPY --from=build /app/target/vms-backend-*.jar app.jar
USER vms
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
