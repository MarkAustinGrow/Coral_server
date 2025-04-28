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

# Set environment variables
ENV DEBUG_MODE=false
ENV TRACE_MODE=false
ENV PORT=3001
ENV BEHIND_PROXY=true

# Set the entrypoint with debug options
ENTRYPOINT ["sh", "-c", "java -jar /app/coral-server.jar ${DEBUG_MODE:+--debug} ${TRACE_MODE:+--trace} --sse-server ${PORT}"]
