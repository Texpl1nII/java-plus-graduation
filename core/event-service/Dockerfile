FROM amazoncorretto:21-alpine
WORKDIR /app
COPY target/main-service-*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]