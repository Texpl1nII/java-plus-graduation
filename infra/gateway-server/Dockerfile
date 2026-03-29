FROM amazoncorretto:21-alpine
WORKDIR /app
COPY target/gateway-server-*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]