# STAGE 1: Build the Maven project inside a Linux container.
# This avoids any host system Java/Maven dependency and prevents CRLF issues with mvnw.
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app

# Copy the pom.xml file first to download project dependencies
COPY pom.xml .

# Download dependencies (cached layer)
RUN mvn dependency:go-offline -B

# Copy the source files and package the application
COPY src ./src
RUN mvn package -DskipTests -B

# STAGE 2: Lightweight Run Stage using JRE (Java Runtime Environment)
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Copy the built JAR from the builder stage
COPY --from=builder /app/target/*.jar app.jar

# Spring Boot default port is 8080. Render expects services to expose a port.
EXPOSE 8080

# Execute the application
ENTRYPOINT ["java", "-jar", "app.jar"]
