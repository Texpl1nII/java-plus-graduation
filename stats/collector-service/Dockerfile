FROM openjdk:21-jdk-slim
COPY target/collector-service-*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]