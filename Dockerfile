# syntax=docker/dockerfile:1
# Stage 1: Build the application with Maven
FROM maven:3.9.6-eclipse-temurin-17 AS build

# Set the working directory
WORKDIR /usr/src/app

# Copy dependency files
COPY pom.xml .
COPY libs ./libs

# Install custom libraries and download dependencies with cache mount
# This will persist the Maven cache across builds
RUN --mount=type=cache,target=/root/.m2 \
    mvn install:install-file -Dfile=libs/NiceID_v1.2.jar -DgroupId=com.niceid -DartifactId=niceid -Dversion=1.2 -Dpackaging=jar && \
    mvn dependency:go-offline

# Copy source code and build with cache mount
COPY src ./src
COPY .env ./

RUN --mount=type=cache,target=/root/.m2 \
    mvn clean package -DskipTests

# Stage 2: Create the final, smaller image with just the JRE
FROM eclipse-temurin:17-jre-jammy

# Set the working directory
WORKDIR /usr/src/app

# Copy the JAR file from the build stage
COPY --from=build /usr/src/app/target/*.jar app.jar
COPY --from=build /usr/src/app/.env .

# Expose the port the application runs on (default for Spring Boot is 8080)
EXPOSE 8080

# The command to run the application
ENTRYPOINT ["java", "-jar", "app.jar"] 