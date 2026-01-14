# Unzipper Service

A Spring Boot service for unzipping files, processing them recursively, and interacting with a Documentum backend.

## Configuration

The service is configured using environment variables. You can set these in your `docker run` command or `docker-compose.yml`.

### General
- `SERVER_PORT`: Port the service runs on (default: `8080`).
- `MULTIPART_MAX_FILE_SIZE`: Max file upload size (default: `500MB`).
- `MULTIPART_MAX_REQUEST_SIZE`: Max request size (default: `500MB`).

### Database
- `DB_URL`: JDBC URL for SQL Server.
- `DB_USERNAME`: Database username.
- `DB_PASSWORD`: Database password.

### Documentum Integration
- `DOCUMENTUM_FETCH_URL`: URL to fetch documents from Documentum.
- `DOCUMENTUM_UPLOAD_URL`: URL to upload documents to Documentum.
- `DOCUMENTUM_MAX_MEMORY_SIZE`: Max memory for WebClient buffer (default: `16777216` bytes / 16MB).

### SSL/TLS Configuration for Documentum
To enable mutual TLS (mTLS) or client authentication with Documentum:
- `USE_CERTS`: Set to `true` to enable SSL configuration (default: `false`).
- `DOCUMENTUM_CERT_PATH`: Path to the `.p12` certificate file inside the container.
- `DOCUMENTUM_CERT_PASSWORD`: Password for the `.p12` certificate.

**Example usage with Certs:**
```bash
docker run -d \
  -p 8080:8080 \
  -v /local/path/to/certs:/app/certs \
  -e USE_CERTS=true \
  -e DOCUMENTUM_CERT_PATH=/app/certs/client.p12 \
  -e DOCUMENTUM_CERT_PASSWORD=your_password \
  unzipper-service
```

### Performance Tuning
- `UNZIP_UPLOAD_THREADS`: Number of parallel upload threads (default: `10`).
- `UNZIP_PROCESS_THREADS_MULTIPLIER`: Multiplier for CPU cores to determine unzip processing threads (default: `2`).

## Running
### Using Launcher Script
```bash
./launcher.sh build
./launcher.sh up
```
