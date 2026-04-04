FROM openjdk:21-jdk-slim
COPY target/category-service-*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]