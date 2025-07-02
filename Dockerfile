FROM openjdk:21-jdk-slim
WORKDIR /app
COPY target/jira-1.0.jar app.jar
COPY config ./config
COPY resources ./resources
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]