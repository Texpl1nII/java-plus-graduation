FROM openjdk:21-jdk-slim
COPY target/user-service-*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]