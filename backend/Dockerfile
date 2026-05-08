FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /workspace

COPY backend/pom.xml backend/pom.xml
RUN mvn -f backend/pom.xml -DskipTests dependency:go-offline

COPY backend backend
RUN mvn -f backend/pom.xml -DskipTests package

FROM eclipse-temurin:17-jre

WORKDIR /app

ENV SERVER_PORT=18081

COPY --from=build /workspace/backend/target/learnflow-backend-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 18081

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
