# Use OpenJDK 17 as the base image
FROM eclipse-temurin:17-jdk-jammy

# Set the working directory inside the container
WORKDIR /app

# Copy Maven build files first (for caching dependencies)
COPY pom.xml .
COPY src ./src

# Build the application using Maven
RUN apt-get update && apt-get install -y maven \
    && mvn clean package -DskipTests \
    && apt-get remove -y maven \
    && rm -rf /var/lib/apt/lists/*

# Expose port (Spring Boot default)
EXPOSE 8080

# Run the JAR file
CMD ["java", "-jar", "target/auth-server.jar"]
