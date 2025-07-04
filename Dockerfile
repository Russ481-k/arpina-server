# syntax=docker/dockerfile:1
# Stage 1: Build the application with Maven
FROM maven:3.9.6-eclipse-temurin-17 AS build

# Set the working directory
WORKDIR /usr/src/app

# Copy dependency files first (for better layer caching)
COPY pom.xml .
COPY libs ./libs

# Install custom libraries first (this layer will be cached)
RUN --mount=type=cache,target=/root/.m2 \
    mvn install:install-file -Dfile=libs/NiceID_v1.2.jar -DgroupId=com.niceid -DartifactId=niceid -Dversion=1.2 -Dpackaging=jar

# Download dependencies only (this layer will be cached if pom.xml doesn't change)
RUN --mount=type=cache,target=/root/.m2 \
    mvn dependency:go-offline -Dmaven.wagon.http.retryHandler.count=3 -Dmaven.wagon.httpconnectionManager.ttlSeconds=120

# Copy source code and build (only this layer will change with code changes)
COPY src ./src
COPY .env ./

RUN --mount=type=cache,target=/root/.m2 \
    mvn clean package -DskipTests -Dmaven.wagon.http.retryHandler.count=3

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