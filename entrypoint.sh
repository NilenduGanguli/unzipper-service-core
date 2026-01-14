#!/bin/sh

# Default Environment Variables
export SERVER_PORT=${SERVER_PORT:-8080}
export MULTIPART_MAX_FILE_SIZE=${MULTIPART_MAX_FILE_SIZE:-500MB}
export MULTIPART_MAX_REQUEST_SIZE=${MULTIPART_MAX_REQUEST_SIZE:-500MB}

# Database
export DB_URL=${DB_URL:-"jdbc:sqlserver://sql-server:1433;databaseName=unzip_db;encrypt=true;trustServerCertificate=true;"}
export DB_USERNAME=${DB_USERNAME:-"sa"}
export DB_PASSWORD=${DB_PASSWORD:-"YourStrong!Passw0rd"}

# Documentum Service
export DOCUMENTUM_FETCH_URL=${DOCUMENTUM_FETCH_URL:-"http://documentum:8000/fetch"}
export DOCUMENTUM_UPLOAD_URL=${DOCUMENTUM_UPLOAD_URL:-"http://documentum:8000/upload"}
# Default 16MB
export DOCUMENTUM_MAX_MEMORY_SIZE=${DOCUMENTUM_MAX_MEMORY_SIZE:-16777216}

# Documentum Certificates
export USE_CERTS=${USE_CERTS:-false}
export DOCUMENTUM_CERT_PATH=${DOCUMENTUM_CERT_PATH:-""}
export DOCUMENTUM_CERT_PASSWORD=${DOCUMENTUM_CERT_PASSWORD:-""}

# Service Threads
export UNZIP_UPLOAD_THREADS=${UNZIP_UPLOAD_THREADS:-10}
export UNZIP_PROCESS_THREADS_MULTIPLIER=${UNZIP_PROCESS_THREADS_MULTIPLIER:-2}

echo "Starting Unzipper Service with the following configuration:"
echo "SERVER_PORT: $SERVER_PORT"
echo "DOCUMENTUM_FETCH_URL: $DOCUMENTUM_FETCH_URL"
echo "DOCUMENTUM_UPLOAD_URL: $DOCUMENTUM_UPLOAD_URL"
echo "UNZIP_UPLOAD_THREADS: $UNZIP_UPLOAD_THREADS"

# Start the Spring Boot application
exec java -jar app.jar
