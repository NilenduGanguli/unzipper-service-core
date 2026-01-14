Unzipping Service Documentation
===============================

This document outlines the architecture and logical flow of the unzipping task performed by the Unzipper Service. The service is designed to handle standard zip files as well as nested zip archives (zips within zips) using an asynchronous, recursive approach.

1. High-Level Overview
----------------------
The service accepts a generic file upload (MultipartFile) via a REST API. It temporarily stores the file, inspects it, and recursively processes its contents. If a file inside the archive is identified as another zip file, checking is performed in parallel (where resources allow) using a custom thread pool. The final output is a JSON structure representing the file tree hierarchy and metadata.

2. Key Classes
--------------

### A. UnzipController (com.unzipper.controller)
**Role:** API Entry Point / REST Controller.
**Key Method:** `unzip(@RequestParam("file") MultipartFile file)`
- **Functionality:**
  - Accepts the HTTP POST request.
  - Validates that the file is not empty.
  - Delegates the processing to `UnzipService`.
  - Wraps the result in a standard `ResponseEntity`.

### B. UnzipService (com.unzipper.service)
**Role:** Core Business Logic.
**Key Attributes:**
- `executor`: A `FixedThreadPool` customized based on available processors (8x core count) to optimize for I/O-intensive operations mixed with CPU processing.

**Key Methods:**

1. `process(MultipartFile file)`
   - **Role:** Orchestrator.
   - **Steps:**
     - Creates a secure temporary directory (`unzipper_service_...`).
     - Transfers the uploaded `MultipartFile` to a physical temporary file on disk.
     - Initiates the recursive processing via `processZipFile()`.
     - Waiting: Blocks on the main thread (`.get()`) until the entire recursive operation completes.
     - Cleanup: Safely deletes the temporary directory and all created temp files after processing ensures no disk space leaks.
     - Returns: `UnzipResponse` containing the document IDs and the file tree structure.

2. `processZipFile(File file, String relativePath, long compressedSize)`
   - **Role:** Asynchronous Task Wrapper.
   - **Steps:**
     - Submits a task to the `executor` thread pool.
     - Opens a specific `ZipInputStream` for the file.
     - Delegates the actual parsing to `processStream()`.
     - Returns a `CompletableFuture<ProcessingResult>` allowing non-blocking invocation for nested zips.

3. `processStream(ZipInputStream zis, ...)`
   - **Role:** The Parsing Engine.
   - **Steps:**
     - Initializes a root `ZipNode` representing the current zip file.
     - Iterates through entries using `zis.getNextEntry()`.
     - **For Directories:**
       - Creates a `ZipNode` marked as `directory: true` and adds it to the tree.
     - **For Files:**
       - Extracts the entry to a unique temporary file (`entry_...tmp`).
       - Checks the file extension.
       - **If it is a Nested Zip (.zip):**
         - Recursively calls `processZipFile` on the extracted temp file.
         - The resulting `CompletableFuture` is added to a list of futures. This allows multiple nested zips at the same level to be processed in parallel.
         - Upon completion, the nested result (its tree root) is added as a child to the current node.
       - **If it is a Regular File:**
         - Creates a leaf `ZipNode`.
         - Adds to the document list (simulating ID generation or extraction logic).
     - **Synchronization:** Waits for all spawned child futures to complete (`CompletableFuture.allOf`) before returning the aggregated result.

### C. ZipNode (com.unzipper.model)
**Role:** Data Model.
- Represents a single node in the file tree (either a file, directory, or nested archive).
- Contains metadata: `name`, `path`, `compressedSize`, `size`, `isDirectory`, `isArchive`.
- Maintains a list of `children` `ZipNode` objects.

### D. UnzipResponse (com.unzipper.model)
**Role:** API Response Wrapper.
- Contains the list of extracted `docIds`.
- Contains the root `metadata` (the top-level `ZipNode`).

3. Execution Flow Summary
-------------------------
1. **User Upload** -> `UnzipController`.
2. Controller calls `UnzipService.process()`.
3. `process()` saves file to disk -> calls `processZipFile()`.
4. `processZipFile()` opens stream -> calls `processStream()`.
5. `processStream()` loops through entries:
   - Normal files are extracted and added to the tree.
   - **Nested Zip Files** trigger a recursive async call to `processZipFile()`.
6. Results from nested zips are aggregated once their threads finish.
7. The complete tree is returned to `process()`.
8. `process()` cleans up temp files and returns the response.
