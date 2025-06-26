# Stage 1: Build the application with Maven
# Use a Maven image that includes JDK 17, matching Ubuntu 22.04 (Jammy)
FROM maven:3.9.6-eclipse-temurin-17 AS build

# Set the working directory
WORKDIR /usr/src/app

# Copy the pom.xml and download dependencies
# This is done as a separate layer to leverage Docker's layer caching
COPY pom.xml .
COPY libs ./libs
COPY .env ./
RUN mvn install:install-file -Dfile=libs/NiceID_v1.2.jar -DgroupId=com.niceid -DartifactId=niceid -Dversion=1.2 -Dpackaging=jar
RUN mvn dependency:go-offline

# Copy the source code and build the application
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Create the final, smaller image with just the JRE
# Use a JRE image for a smaller footprint, matching Ubuntu 22.04 (Jammy)
FROM eclipse-temurin:17-jre-jammy

# Set the working directory
WORKDIR /usr/src/app

# Copy the JAR file from the build stage
# The wildcard (*) is used to match the built JAR file without knowing its exact name
COPY --from=build /usr/src/app/target/*.jar app.jar
COPY --from=build /usr/src/app/.env .env

# Expose the port the application runs on (default for Spring Boot is 8080)
EXPOSE 8080

# The command to run the application
ENTRYPOINT ["java", "-jar", "app.jar"] 