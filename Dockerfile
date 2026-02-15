FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app
COPY gradle gradle
COPY gradlew build.gradle settings.gradle ./
COPY src ./src
RUN ./gradlew bootWar --no-daemon -x test

FROM eclipse-temurin:17-jre-alpine AS base
WORKDIR /app

FROM base AS dev
RUN apk add --no-cache unzip
COPY --from=build /app/build/libs/*.war app.war
RUN mkdir -p /app/extracted && unzip app.war -d /app/extracted
ENTRYPOINT ["java", "-cp", "/app/extracted/WEB-INF/classes:/app/extracted/WEB-INF/lib/*:/app/extracted/WEB-INF/lib-provided/*", "com.flexcodelabs.flextuma.FlextumaApplication"]

FROM base AS prod
COPY --from=build /app/build/libs/*.war app.war
ENTRYPOINT ["java", "-jar", "app.war"]
