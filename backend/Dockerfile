ARG BACKEND_BUILD_IMAGE=maven:3.9-eclipse-temurin-17
ARG BACKEND_RUNTIME_IMAGE=eclipse-temurin:17-jre-ubi10-minimal

FROM ${BACKEND_BUILD_IMAGE} AS build

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

FROM ${BACKEND_RUNTIME_IMAGE}

WORKDIR /app

RUN microdnf install -y curl-minimal \
    && microdnf clean all \
    && mkdir -p /var/lib/learnflow/resource-sources \
    && chown -R 10001:10001 /var/lib/learnflow

ENV SERVER_PORT=18081

COPY --from=build /workspace/backend/target/learnflow-backend-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 18081

USER 10001:10001

HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
  CMD curl --fail --silent --show-error http://127.0.0.1:18081/actuator/health/liveness >/dev/null || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
