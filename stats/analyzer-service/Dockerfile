FROM openjdk:21-jdk-slim
COPY target/analyzer-service-*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]