FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /workspace

ARG MAVEN_MIRROR_URL=https://repo.maven.apache.org/maven2
ARG HTTP_PROXY
ARG HTTPS_PROXY
ARG NO_PROXY

RUN mkdir -p /root/.m2 && \
    printf '%s\n' \
      '<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd">' \
      '  <mirrors>' \
      '    <mirror>' \
      '      <id>configured-mirror</id>' \
      '      <mirrorOf>*</mirrorOf>' \
      "      <url>${MAVEN_MIRROR_URL}</url>" \
      '    </mirror>' \
      '  </mirrors>' \
      '</settings>' \
      > /root/.m2/settings.xml

COPY backend/pom.xml backend/pom.xml
RUN mvn -f backend/pom.xml -DskipTests dependency:go-offline

COPY backend backend
RUN mvn -f backend/pom.xml -DskipTests package

FROM eclipse-temurin:17-jre-ubi10-minimal

WORKDIR /app

ENV SERVER_PORT=18081

COPY --from=build /workspace/backend/target/learnflow-backend-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 18081

USER 10001:10001

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
