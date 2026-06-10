FROM maven:3.9.9-eclipse-temurin-17

WORKDIR /app

COPY . .

# Build the project
RUN mvn clean package -DskipTests

# Run the app
CMD ["java", "-jar", "target/stock-checker-0.0.1-SNAPSHOT.jar"]