FROM openjdk:21-jdk-slim
COPY target/aggregator-service-*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]