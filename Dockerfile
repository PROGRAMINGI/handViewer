FROM gradle:8.5-jdk21-alpine AS builder
WORKDIR /app
COPY . .
RUN chmod +x ./gradlew
RUN ./gradlew build -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]