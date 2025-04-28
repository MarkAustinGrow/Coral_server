# Build stage
FROM gradle:8.6-jdk21 AS build
WORKDIR /app
COPY ./coral-server-master /app/
RUN gradle build --no-daemon

# Run stage
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /app/build/libs/coral-server-*.jar /app/coral-server.jar

# Expose the default port
EXPOSE 3001

# Set the entrypoint
ENTRYPOINT ["java", "-jar", "/app/coral-server.jar", "--sse-server", "3001"]
