FROM gradle:8-jdk17 AS build
WORKDIR /app
COPY build.gradle settings.gradle ./
COPY src ./src
RUN gradle bootWar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine AS dev
WORKDIR /app
RUN apk add --no-cache unzip
COPY --from=build /app/build/libs/*.war app.war
RUN mkdir -p /app/extracted && unzip app.war -d /app/extracted
ENTRYPOINT ["java", "-cp", "/app/extracted/WEB-INF/classes:/app/extracted/WEB-INF/lib/*:/app/extracted/WEB-INF/lib-provided/*", "com.flexcodelabs.flextuma.FlextumaApplication"]

FROM dev AS prod
COPY --from=build /app/build/libs/*.war app.war
ENTRYPOINT ["java", "-jar", "app.war"]
