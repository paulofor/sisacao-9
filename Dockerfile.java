FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY contracts/ contracts/
COPY backend/ backend/
COPY agents/ agents/
ARG MODULE
RUN mvn -B -q -pl ${MODULE} -am package -DskipTests && cp ${MODULE}/target/${MODULE}-0.1.0-SNAPSHOT.jar /app.jar
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app.jar ./app.jar
COPY harnesses/ /app/harnesses/
USER 10001:10001
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
