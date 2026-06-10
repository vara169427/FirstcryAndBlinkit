# Stage 1: Build the application
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# Stage 2: Runtime image containing Playwright and system dependencies
FROM mcr.microsoft.com/playwright/java:v1.54.0-jammy
WORKDIR /app
COPY --from=build /app/target/stock-checker-0.0.1-SNAPSHOT.jar app.jar

# Expose the application port
EXPOSE 9091

# Run the app
CMD ["java", "-jar", "app.jar"]