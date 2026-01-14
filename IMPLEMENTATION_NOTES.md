# Implementation Summary: /unzip_save_doc Endpoint

## Overview
Implemented a new GET endpoint `/unzip_save_doc` that processes documents from Documentum, unzips them, uploads components back to Documentum, and logs all requests to a SQL Server database.

## Changes Made

### 1. **Dependencies Added to pom.xml**
- `spring-boot-starter-data-jpa` - For database operations
- `mssql-jdbc` (v12.4.3.jre11) - SQL Server JDBC driver
- `spring-boot-starter-webflux` - For WebClient HTTP operations

### 2. **New Entity Class: KycDocumentUnzip**
- **File**: `src/main/java/com/unzipper/entity/KycDocumentUnzip.java`
- Maps to database table: `dbo.kyc_document_unzip`
- Fields:
  - `KYC_UNZIP_ID` (Primary Key, auto-generated)
  - `CLIENT_ID` (required)
  - `DOCUMENT_LINK_ID` (required)
  - `DOCUMENT_NAME`
  - `DOCUMENT_TYPE`
  - `PARENT_DOCUMENT_LINK_ID`
  - `LST_UPD_TIME` (LocalDateTime)
  - `LST_UPD_DT` (LocalDate)
  - `DOCUMENT_PATH`

### 3. **New Repository: KycDocumentUnzipRepository**
- **File**: `src/main/java/com/unzipper/repository/KycDocumentUnzipRepository.java`
- Extends `JpaRepository<KycDocumentUnzip, Long>`
- Custom query to find by `clientId` and `documentLinkId`

### 4. **New Documentum Client: DocumentumClient**
- **File**: `src/main/java/com/unzipper/client/DocumentumClient.java`
- Methods:
  - `fetchDocument(String documentLinkId)` - Fetches document from `http://documentum:8000/fetch`
  - `uploadDocument(byte[] fileContent, String fileName, String parentDocumentLinkId)` - Uploads to `http://documentum:8000/upload`
- Returns document_link_ids from upload responses

### 5. **New Service: UnzipSaveDocService**
- **File**: `src/main/java/com/unzipper/service/UnzipSaveDocService.java`
- Main method: `processDocumentUnzip(String documentLinkId, String clientId)`
- Workflow:
  1. Logs request to database
  2. Fetches document from Documentum
  3. Saves to temporary file
  4. Recursively unzips (handles nested zips)
  5. Uploads extracted files back to Documentum
  6. Collects and returns document_link_ids
  7. Updates database record with processing results
  8. Cleans up temporary files

### 6. **Updated Controller: UnzipController**
- **File**: `src/main/java/com/unzipper/controller/UnzipController.java`
- New endpoint:
  ```
  GET /unzip_save_doc?document_link_id={id}&client_id={id}
  ```
- Returns: `UnzipResponse` with list of document_link_ids and file tree metadata

### 7. **New WebClient Configuration**
- **File**: `src/main/java/com/unzipper/config/WebClientConfig.java`
- Provides Spring WebClient bean for HTTP operations

### 8. **Updated application.properties**
- **File**: `src/main/resources/application.properties`
- SQL Server configuration:
  ```properties
  spring.datasource.url=jdbc:sqlserver://sql-server:1433;databaseName=unzip_db;encrypt=true;trustServerCertificate=true;
  spring.datasource.username=sa
  spring.datasource.password=YourStrong!Passw0rd
  spring.datasource.driver-class-name=com.microsoft.sqlserver.jdbc.SQLServerDriver
  ```
- JPA/Hibernate configuration for SQL Server 2012

## API Endpoint

### GET /unzip_save_doc

**Request Parameters:**
- `document_link_id` (required) - Document link ID from Documentum
- `client_id` (required) - Client identifier

**Response:**
```json
{
  "docIds": ["DOC_12345678", "DOC_87654321", ...],
  "metadata": {
    "name": "filename.zip",
    "path": "filename.zip",
    "compressedSize": 1024000,
    "size": 2048000,
    "isDirectory": false,
    "isArchive": true,
    "children": [
      {
        "name": "extracted_file.txt",
        "path": "filename.zip/extracted_file.txt",
        "size": 1024000,
        "isDirectory": false,
        "isArchive": false,
        "children": []
      }
    ]
  }
}
```

## Database Logging

Every request to `/unzip_save_doc` is logged to `dbo.kyc_document_unzip` table with:
- Request timestamp (LST_UPD_TIME)
- Request date (LST_UPD_DT)
- Client and document information
- Document path and extracted file paths

## Error Handling

- Returns 400 Bad Request if parameters are missing/empty
- Throws RuntimeException with descriptive messages on Documentum fetch/upload failures
- Logs all errors with full stack traces for debugging

## Performance Features

- Thread pool executor with size = CPU cores × 8
- Parallel processing of multiple files during unzipping
- Efficient stream handling with minimal memory footprint
- Automatic cleanup of temporary files

## Notes

- Supports nested zip files (recursive processing)
- Non-zip files are uploaded to Documentum
- All file operations use streams to minimize memory usage
- Thread-safe collections for concurrent processing
