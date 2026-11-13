FROM eclipse-temurin:21.0.8_9-jdk-jammy AS build

WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY helpTree-service/pom.xml helpTree-service/pom.xml
RUN chmod +x mvnw \
    && ./mvnw -B -pl helpTree-service -am dependency:go-offline

COPY helpTree-service/src helpTree-service/src
RUN ./mvnw -B -pl helpTree-service -am package -DskipTests

FROM eclipse-temurin:21.0.8_9-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl wget \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app \
    && useradd --system --gid app --home-dir /app app

WORKDIR /app
COPY --from=build --chown=app:app \
    /workspace/helpTree-service/target/helpTree-service-0.0.1-SNAPSHOT.jar app.jar

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
USER app
EXPOSE 8080 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
