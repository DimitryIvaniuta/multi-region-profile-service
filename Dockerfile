FROM gradle:9.5.1-jdk25 AS build
WORKDIR /workspace
COPY --chown=gradle:gradle . .
RUN gradle clean bootJar --no-daemon

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN addgroup --system app && adduser --system --ingroup app app
COPY --from=build /workspace/build/libs/*.jar /app/app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
