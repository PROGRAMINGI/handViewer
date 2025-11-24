FROM gradle:8.5-jdk21-alpine AS builder
WORKDIR /app
COPY . .
RUN ./gradlew build -x test

FROM openjdk:21-jdk-slim
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]