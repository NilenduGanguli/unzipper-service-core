# Testing Guide for /unzip_save_doc Endpoint

## Prerequisites

1. **SQL Server Database** running at: `sql-server:1433`
   - Database: `unzip_db`
   - User: `sa`
   - Password: `YourStrong!Passw0rd`
   - Table: `dbo.kyc_document_unzip` must exist with schema as documented

2. **Documentum Service** running at: `http://documentum:8000`
   - POST endpoint: `/fetch` - to retrieve documents
   - POST endpoint: `/upload` - to upload extracted files

3. **Spring Boot Application** running on: `http://localhost:8080`

## Database Setup

Before running the service, ensure the SQL Server table exists:

```sql
CREATE TABLE dbo.kyc_document_unzip (
    KYC_UNZIP_ID BIGINT PRIMARY KEY IDENTITY(1,1),
    CLIENT_ID NVARCHAR(MAX) NOT NULL,
    DOCUMENT_LINK_ID NVARCHAR(MAX) NOT NULL,
    DOCUMENT_NAME NVARCHAR(MAX),
    DOCUMENT_TYPE NVARCHAR(MAX),
    PARENT_DOCUMENT_LINK_ID NVARCHAR(MAX),
    LST_UPD_TIME DATETIME2,
    LST_UPD_DT DATE,
    DOCUMENT_PATH NVARCHAR(MAX)
);
```

## Test Cases

### 1. Basic Request - Valid Parameters

```bash
curl -X GET "http://localhost:8080/unzip_save_doc?document_link_id=000000000a&client_id=CLIENT_001"
```

**Expected Response (200 OK):**
```json
{
  "docIds": [
    "DOC_12345678",
    "DOC_87654321",
    "DOC_abcdef12"
  ],
  "metadata": {
    "name": "000000000a.zip",
    "path": "000000000a.zip",
    "compressedSize": 1048576,
    "size": 2097152,
    "isDirectory": false,
    "isArchive": true,
    "children": [
      {
        "name": "file1.txt",
        "path": "000000000a.zip/file1.txt",
        "compressedSize": 524288,
        "size": 1048576,
        "isDirectory": false,
        "isArchive": false,
        "children": []
      },
      {
        "name": "nested.zip",
        "path": "000000000a.zip/nested.zip",
        "compressedSize": 262144,
        "size": 524288,
        "isDirectory": false,
        "isArchive": true,
        "children": [
          {
            "name": "inner_file.pdf",
            "path": "000000000a.zip/nested.zip/inner_file.pdf",
            "compressedSize": 131072,
            "size": 262144,
            "isDirectory": false,
            "isArchive": false,
            "children": []
          }
        ]
      }
    ]
  }
}
```

### 2. Invalid Requests

#### Missing document_link_id
```bash
curl -X GET "http://localhost:8080/unzip_save_doc?client_id=CLIENT_001"
```
**Expected Response (400 Bad Request)**

#### Missing client_id
```bash
curl -X GET "http://localhost:8080/unzip_save_doc?document_link_id=000000000a"
```
**Expected Response (400 Bad Request)**

#### Empty document_link_id
```bash
curl -X GET "http://localhost:8080/unzip_save_doc?document_link_id=&client_id=CLIENT_001"
```
**Expected Response (400 Bad Request)**

### 3. Database Verification

After running the endpoint, verify the database entry:

```sql
SELECT * FROM dbo.kyc_document_unzip 
WHERE CLIENT_ID = 'CLIENT_001' AND DOCUMENT_LINK_ID = '000000000a'
ORDER BY LST_UPD_TIME DESC;
```

**Expected Result:**
- One row with the request details
- LST_UPD_TIME and LST_UPD_DT set to current datetime/date
- DOCUMENT_PATH populated with the extracted document structure

## Logging Output

Check application logs for the following messages:

```
[DEBUG] com.unzipper.controller.UnzipController - Received unzip_save_doc request for documentLinkId: 000000000a, clientId: CLIENT_001
[INFO]  com.unzipper.service.UnzipSaveDocService - Processing document unzip for clientId: CLIENT_001, documentLinkId: 000000000a
[INFO]  com.unzipper.repository - Logged request to database with KYC_UNZIP_ID: X
[INFO]  com.unzipper.client.DocumentumClient - Fetching document from Documentum with documentLinkId: 000000000a
[INFO]  com.unzipper.client.DocumentumClient - Successfully fetched document from Documentum, size: XXXX bytes
[DEBUG] com.unzipper.service.UnzipSaveDocService - Processing zip file: 000000000a.zip
[DEBUG] com.unzipper.client.DocumentumClient - Successfully uploaded file to Documentum, received documentLinkId: DOC_12345678
[INFO]  com.unzipper.service.UnzipSaveDocService - Successfully processed X files for documentLinkId: 000000000a
```

## Performance Testing

For load testing, use the existing `load_test.py` script or create a new one:

```bash
# Multiple sequential requests
for i in {1..10}; do
  curl -X GET "http://localhost:8080/unzip_save_doc?document_link_id=doc_$i&client_id=CLIENT_$i"
done
```

## Troubleshooting

### Database Connection Error
```
java.sql.SQLServerException: The TCP/IP connection to the host sql-server, port 1433 has failed
```
**Solution:** Ensure SQL Server is running and accessible at the configured connection string

### Documentum Fetch Error
```
Failed to fetch document from Documentum: Connection refused
```
**Solution:** Ensure Documentum service is running at `http://documentum:8000`

### Empty ZIP File
```
java.util.zip.ZipException: Not in gzip format
```
**Solution:** Verify that the document returned from Documentum is a valid ZIP file

### OutOfMemoryError
**Solution:** Increase JVM heap size if processing very large files:
```bash
java -Xmx2g -jar target/unzipper-service-0.0.1-SNAPSHOT.jar
```

## Next Steps

1. Deploy the application to your environment
2. Configure SQL Server connection string as needed
3. Configure Documentum endpoints
4. Run integration tests with actual data
5. Monitor performance and adjust thread pool size if needed
