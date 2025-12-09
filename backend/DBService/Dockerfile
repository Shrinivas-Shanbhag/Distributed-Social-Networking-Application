# Use OpenJDK 17 as base image
FROM eclipse-temurin:17-jdk-jammy

# Set working directory
WORKDIR /app

# Copy Maven build files first (to cache dependencies)
COPY pom.xml .
COPY src ./src

# Install Maven, build the project, then remove Maven to reduce image size
RUN apt-get update && apt-get install -y maven \
    && mvn clean package -DskipTests \
    && apt-get remove -y maven \
    && rm -rf /var/lib/apt/lists/*

# Expose default Spring Boot port
EXPOSE 8080

# Run the Spring Boot JAR
CMD ["java", "-jar", "target/db-service.jar"]
