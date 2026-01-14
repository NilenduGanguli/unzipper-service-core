# Build stage
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Run stage
FROM eclipse-temurin:17-jre
WORKDIR /app

# Install dependencies for Oracle Instant Client (OCI)
# Support both old and new libaio naming
RUN apt-get update && \
    apt-get install -y wget unzip && \
    (apt-get install -y libaio1 || apt-get install -y libaio1t64) && \
    rm -rf /var/lib/apt/lists/* && \
    ARCH=$(uname -m) && \
    if [ "$ARCH" = "aarch64" ]; then \
      # Use 19.19 for ARM64 and x64 to match the JDBC driver version
      wget https://download.oracle.com/otn_software/linux/instantclient/1919000/instantclient-basic-linux.arm64-19.19.0.0.0dbru.zip -O instantclient.zip; \
      EXTRACT_DIR="instantclient_19_19"; \
    else \
      # Use 19.19 for x64
      wget https://download.oracle.com/otn_software/linux/instantclient/1919000/instantclient-basic-linux.x64-19.19.0.0.0dbru.zip -O instantclient.zip; \
      EXTRACT_DIR="instantclient_19_19"; \
    fi && \
    unzip instantclient.zip && \
    rm instantclient.zip && \
    mv $EXTRACT_DIR instantclient && \
    # Fix for libaio missing symlink in newer Ubuntu versions (libaio1t64)
    if [ -f /usr/lib/$(uname -m)-linux-gnu/libaio.so.1t64 ]; then \
      ln -s /usr/lib/$(uname -m)-linux-gnu/libaio.so.1t64 /usr/lib/$(uname -m)-linux-gnu/libaio.so.1; \
    fi


# Set environment variables for Oracle Instant Client
ENV LD_LIBRARY_PATH=/app/instantclient
ENV TNS_ADMIN=/app/instantclient/network/admin

COPY --from=build /app/target/unzipper-service-0.0.1-SNAPSHOT.jar app.jar
COPY entrypoint.sh .
RUN chmod +x entrypoint.sh

# Expose port
EXPOSE 8080

# Run the application
ENTRYPOINT ["./entrypoint.sh"]
