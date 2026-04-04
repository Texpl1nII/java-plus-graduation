FROM openjdk:21-jdk-slim
COPY target/request-service-*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]