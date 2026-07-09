ARG DOCKER_REGISTRY=docker.io

FROM ${DOCKER_REGISTRY}/maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app

COPY pom.xml .
COPY settings.xml /root/.m2/settings.xml

RUN mvn dependency:go-offline -q

COPY src ./src

RUN mvn package -DskipTests -q

FROM ${DOCKER_REGISTRY}/eclipse-temurin:17-jre

WORKDIR /app

COPY --from=builder /app/target/coldchain-shelf-1.0.0.jar app.jar

EXPOSE 8135

ENTRYPOINT ["java", "-jar", "app.jar"]
