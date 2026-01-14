package com.unzipper.service;

import com.unzipper.client.DocumentumClient;
import com.unzipper.entity.KycDocumentUnzip;
import com.unzipper.model.DownloadedDocument;
import com.unzipper.model.UnzipDetail;
import com.unzipper.model.UnzippedFileDetail;
import com.unzipper.model.UnzipResponse;
import com.unzipper.model.ZipNode;
import com.unzipper.repository.KycDocumentUnzipRepository;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class UnzipSaveDocService implements DisposableBean {

    private static final Logger logger = LoggerFactory.getLogger(UnzipSaveDocService.class);

    private final DocumentumClient documentumClient;
    private final KycDocumentUnzipRepository kycDocumentUnzipRepository;
    private final ExecutorService executor;
    private final ExecutorService uploadExecutor; // Separate executor for uploads in parallel

    public UnzipSaveDocService(DocumentumClient documentumClient, 
                               KycDocumentUnzipRepository kycDocumentUnzipRepository,
                               @Value("${unzip.process.threads.multiplier}") int processThreadsMultiplier,
                               @Value("${unzip.upload.threads}") int uploadThreads) {
        this.documentumClient = documentumClient;
        this.kycDocumentUnzipRepository = kycDocumentUnzipRepository;
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        // Processing Executor: for unzip logic and file IO
        this.executor = Executors.newFixedThreadPool(availableProcessors * processThreadsMultiplier); 
        // Upload Executor: Enforce specified parallelism (at least 10)
        this.uploadExecutor = Executors.newFixedThreadPool(uploadThreads);
    }

    @Override
    public void destroy() {
        if (executor != null) {
            executor.shutdownNow();
        }
        if (uploadExecutor != null) {
            uploadExecutor.shutdownNow();
        }
    }

    /**
     * Main process method: fetch from Documentum, unzip, and save metadata to database
     */
    public Map<String, UnzipDetail> processDocumentUnzip(String documentLinkId, String clientId) {
        logger.info("Processing document unzip for clientId: {}, documentLinkId: {}", clientId, documentLinkId);

        KycDocumentUnzip kycRecord = null;
        try {
            // Log the request to database
            kycRecord = new KycDocumentUnzip(clientId, documentLinkId);
            kycRecord.setLstUpdTime(LocalTime.now());
            kycRecord.setLstUpdDt(LocalDate.now());
            kycDocumentUnzipRepository.save(kycRecord);
            logger.info("Logged request to database with KYC_UNZIP_ID: {}", kycRecord.getKycUnzipId());

            // Fetch document from Documentum
            DownloadedDocument downloadedDoc = documentumClient.fetchDocument(documentLinkId);
            InputStream documentStream = downloadedDoc.getContentStream();
            
            // Create temporary directory for processing
            Path tempDir = Files.createTempDirectory("unzipper_service_");
            String safeFilename = FilenameUtils.getName(downloadedDoc.getFilename()); 
            if (safeFilename == null || safeFilename.isEmpty()) {
                 safeFilename = documentLinkId + ".zip";
            }
            File tempZipFile = tempDir.resolve(safeFilename).toFile();

            // Save the stream to a temporary file
            try (FileOutputStream fos = new FileOutputStream(tempZipFile)) {
                IOUtils.copy(documentStream, fos);
            }

            logger.info("Downloaded document to temp file, size: {} bytes", tempZipFile.length());
            long zippedSizeBytes = tempZipFile.length();

            // Process the zip file
            ProcessingResult result = processZipFile(tempZipFile, safeFilename, "", zippedSizeBytes, documentLinkId, clientId).get();

            // Update database record with processing results
            kycRecord.setDocumentName(result.node.getName());
            kycRecord.setDocumentPath(result.node.getPath());
            kycRecord.setLstUpdTime(LocalTime.now());
            kycRecord.setLstUpdDt(LocalDate.now());
            kycDocumentUnzipRepository.save(kycRecord);

            logger.info("Successfully processed {} files for documentLinkId: {}", result.docIds.size(), documentLinkId);

            // Cleanup
            try {
                // IMPORTANT: Recursive delete
                Files.walk(tempDir)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
                logger.debug("Cleaned up temporary directory: {}", tempDir);
            } catch (Exception ignored) {
                logger.warn("Error cleaning up temporary files at {}", tempDir, ignored);
            }

            // Construct new response format
            UnzipDetail detail = new UnzipDetail();
            detail.setDocumentLinkId(documentLinkId);
            detail.setClientId(clientId);
            detail.setFileName(result.node.getName());
            // Size in KB (approx)
            detail.setZippedSize(String.valueOf(zippedSizeBytes / 1024)); 
            
            Map<String, Object> treeStruct = new HashMap<>();
            treeStruct.put(result.node.getName(), buildChildrenMap(result.node));
            detail.setTreeStruct(treeStruct);

            Map<String, UnzippedFileDetail> filesUnzipped = new HashMap<>();
            AtomicLong totalUnzippedBytes = new AtomicLong(0);
            populateFilesUnzipped(result.node, filesUnzipped, totalUnzippedBytes);
            detail.setFilesUnzipped(filesUnzipped);
            
            detail.setUnzippedSize(String.valueOf(totalUnzippedBytes.get() / 1024));

            return Collections.singletonMap(documentLinkId, detail);

        } catch (Exception e) {
            logger.error("Error processing document unzip: {}", e.getMessage(), e);
            if (kycRecord != null) {
                try {
                    String errorMsg = e.getMessage();
                    if (errorMsg != null && errorMsg.length() > 3000) {
                        errorMsg = errorMsg.substring(0, 3000);
                    }
                    kycRecord.setError(errorMsg);
                    kycRecord.setLstUpdTime(LocalTime.now());
                    kycRecord.setLstUpdDt(LocalDate.now());
                    kycDocumentUnzipRepository.save(kycRecord);
                } catch (Exception dbEx) {
                    logger.error("Failed to update error status in database", dbEx);
                }
            }
            throw new RuntimeException("Failed to process document unzip", e);
        }
    }

    /**
     * Upload zip file directly, store in Documentum, then unzip and process.
     */
    public Map<String, UnzipDetail> processDirectUpload(org.springframework.web.multipart.MultipartFile file, String clientId) {
        logger.info("Processing direct upload unzip for clientId: {}, filename: {}", clientId, file.getOriginalFilename());

        KycDocumentUnzip kycRecord = null;
        File tempZipFile = null;
        Path tempDir = null;

        try {
            // 1. Create temp directory and save uploaded file
            tempDir = Files.createTempDirectory("unzipper_upload_");
            String safeFilename = FilenameUtils.getName(file.getOriginalFilename());
            if (safeFilename == null || safeFilename.isEmpty()) {
                safeFilename = "upload.zip";
            }
            tempZipFile = tempDir.resolve(safeFilename).toFile();
            file.transferTo(tempZipFile);
            logger.info("Saved uploaded file to temp: {}, size: {} bytes", tempZipFile.getAbsolutePath(), tempZipFile.length());

            // 2. Upload the PARENT zip to Documentum immediately to get a documentLinkId
            // Using a simple read of the temp file. For very large files, this might need streaming, 
            // but DocumentumClient.uploadDocument currently takes byte[]. 
            // Assuming max file size fits in memory as per existing logic.
            byte[] fileContent = Files.readAllBytes(tempZipFile.toPath());
            String documentLinkId = documentumClient.uploadDocument(fileContent, safeFilename, null);
            logger.info("Uploaded parent zip to Documentum, received documentLinkId: {}", documentLinkId);

            // 3. Log the request to database using the new ID
            kycRecord = new KycDocumentUnzip(clientId, documentLinkId);
            kycRecord.setLstUpdTime(LocalTime.now());
            kycRecord.setLstUpdDt(LocalDate.now());
            kycRecord.setDocumentName(safeFilename);
            kycDocumentUnzipRepository.save(kycRecord);
            logger.info("Logged request to database with KYC_UNZIP_ID: {}", kycRecord.getKycUnzipId());

            // 4. Process the zip file (Reuse existing logic)
            long zippedSizeBytes = tempZipFile.length();
            ProcessingResult result = processZipFile(tempZipFile, safeFilename, "", zippedSizeBytes, documentLinkId, clientId).get();

            // 5. Update database record with processing results
            kycRecord.setDocumentName(result.node.getName());
            kycRecord.setDocumentPath(result.node.getPath());
            kycRecord.setLstUpdTime(LocalTime.now());
            kycRecord.setLstUpdDt(LocalDate.now());
            kycDocumentUnzipRepository.save(kycRecord);

            logger.info("Successfully processed {} files for documentLinkId: {}", result.docIds.size(), documentLinkId);

            // 6. Cleanup
            try {
                Files.walk(tempDir)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
                logger.debug("Cleaned up temporary directory: {}", tempDir);
            } catch (Exception ignored) {
                logger.warn("Error cleaning up temporary files at {}", tempDir, ignored);
            }

            // 7. Construct response (Same format)
            UnzipDetail detail = new UnzipDetail();
            detail.setDocumentLinkId(documentLinkId);
            detail.setClientId(clientId);
            detail.setFileName(result.node.getName());
            detail.setZippedSize(String.valueOf(zippedSizeBytes / 1024)); 
            
            Map<String, Object> treeStruct = new HashMap<>();
            treeStruct.put(result.node.getName(), buildChildrenMap(result.node));
            detail.setTreeStruct(treeStruct);

            Map<String, UnzippedFileDetail> filesUnzipped = new HashMap<>();
            AtomicLong totalUnzippedBytes = new AtomicLong(0);
            populateFilesUnzipped(result.node, filesUnzipped, totalUnzippedBytes);
            detail.setFilesUnzipped(filesUnzipped);
            
            detail.setUnzippedSize(String.valueOf(totalUnzippedBytes.get() / 1024));

            return Collections.singletonMap(documentLinkId, detail);

        } catch (Exception e) {
            logger.error("Error processing direct upload unzip: {}", e.getMessage(), e);
            
            // Clean up temp dir if it exists
            if (tempDir != null) {
                try {
                    Files.walk(tempDir)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
                } catch (Exception ignored) {}
            }

            if (kycRecord != null) {
                try {
                    String errorMsg = e.getMessage();
                    if (errorMsg != null && errorMsg.length() > 3000) {
                        errorMsg = errorMsg.substring(0, 3000);
                    }
                    kycRecord.setError(errorMsg);
                    kycRecord.setLstUpdTime(LocalTime.now());
                    kycRecord.setLstUpdDt(LocalDate.now());
                    kycDocumentUnzipRepository.save(kycRecord);
                } catch (Exception dbEx) {
                    logger.error("Failed to update error status in database", dbEx);
                }
            } else if (clientId != null) {
                 // Try to log minimal error record if we failed before getting a doc ID
                 // But we need a DOC_ID for NOT NULL constraint.
                 try {
                     KycDocumentUnzip errorRecord = new KycDocumentUnzip(clientId, "ERROR_PRE_UPLOAD");
                     String errorMsg = e.getMessage();
                     if (errorMsg != null && errorMsg.length() > 3000) {
                             errorMsg = errorMsg.substring(0, 3000);
                     }
                     errorRecord.setError(errorMsg);
                     kycDocumentUnzipRepository.save(errorRecord);
                 } catch (Exception dbEx) {
                     logger.error("Failed to save pre-upload error record", dbEx);
                 }
            }

            throw new RuntimeException("Failed to process document unzip", e);
        }
    }

    private CompletableFuture<ProcessingResult> processZipFile(File file, String zipName, String relativePath, long compressedSize, 
                                                                String parentDocumentLinkId, String clientId) {
        return CompletableFuture.supplyAsync(() -> {
            logger.debug("Processing zip file: {}", zipName);
            try (FileInputStream fis = new FileInputStream(file);
                 ZipInputStream zis = new ZipInputStream(new BufferedInputStream(fis))) {

                return processStream(zis, relativePath, zipName, compressedSize, file.length(), 
                        parentDocumentLinkId, clientId);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }

    private ProcessingResult processStream(ZipInputStream zis, String parentPath, String zipName, 
                                          long compressedSize, long totalSize, 
                                          String parentDocumentLinkId, String clientId) 
            throws IOException, ExecutionException, InterruptedException {
        
        ZipNode rootNode = new ZipNode(
                zipName,
                parentPath.isEmpty() ? zipName : parentPath,
                compressedSize,
                totalSize,
                false,
                true
        );
        rootNode.setChildren(Collections.synchronizedList(new ArrayList<>()));

        List<String> allDocIds = Collections.synchronizedList(new ArrayList<>());
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        // List to track temp files created in this scope for robust cleanup
        List<File> tempFilesToClean = new ArrayList<>();

        ZipEntry entry;
        try {
            while ((entry = zis.getNextEntry()) != null) {
                final String entryName = entry.getName();
                // Ensure the path starts with the root zip name
                final String currentPath = (parentPath.isEmpty() ? zipName : parentPath) + "/" + entryName;
                long entryCompressedSize = entry.getCompressedSize();

                if (entry.isDirectory()) {
                    ZipNode dirNode = new ZipNode(
                            FilenameUtils.getName(entryName.endsWith("/") ? 
                                    entryName.substring(0, entryName.length() - 1) : entryName),
                            currentPath,
                            entryCompressedSize,
                            0,
                            true,
                            false
                    );
                    rootNode.getChildren().add(dirNode);
                    continue;
                }

                // Create a temp file for THIS entry
                Path tempEntryFile = Files.createTempFile("entry_", ".tmp");
                File processedFile = tempEntryFile.toFile();
                tempFilesToClean.add(processedFile); // Track for cleanup
                
                try (OutputStream out = Files.newOutputStream(tempEntryFile)) {
                    IOUtils.copy(zis, out);
                }
                long actualSize = processedFile.length();

                // Determine if it is a zip file
                boolean isZip = FilenameUtils.getExtension(entryName).equalsIgnoreCase("zip");

                if (isZip) {
                    // Recursive processing - using main 'executor'
                    CompletableFuture<Void> future = processZipFile(processedFile, FilenameUtils.getName(entryName), currentPath, entryCompressedSize, 
                            parentDocumentLinkId, clientId)
                            .thenAccept(result -> {
                                rootNode.getChildren().add(result.node);
                                allDocIds.addAll(result.docIds);
                                // processedFile is owned by the recursive call logic, but we track it here too.
                                // The tracking list in `processZipFile`'s outer scope handles the dir, but 
                                // for nested zips we need to be careful.
                                // Actually, for recursion, we should let the recursive call handle its internal cleanup
                                // but we MUST clean the 'temp zip file' itself afterwards.
                            });
                    futures.add(future);
                } else {
                    // Upload to Documentum - using 'uploadExecutor' for parallelism
                    CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                        KycDocumentUnzip childRecord = new KycDocumentUnzip();
                        childRecord.setClientId(clientId);
                        childRecord.setParentDocumentLinkId(parentDocumentLinkId);
                        childRecord.setDocumentName(FilenameUtils.getName(entryName));
                        childRecord.setDocumentPath(currentPath);
                        childRecord.setLstUpdDt(LocalDate.now());
                        childRecord.setLstUpdTime(LocalTime.now());

                        try {
                            byte[] fileContent = Files.readAllBytes(processedFile.toPath());
                            String docId = documentumClient.uploadDocument(fileContent, entryName, parentDocumentLinkId);
                            allDocIds.add(docId);
                            
                            // Log the unzipped file to database
                            childRecord.setDocumentLinkId(docId);
                            kycDocumentUnzipRepository.save(childRecord);

                            ZipNode fileNode = new ZipNode(
                                    FilenameUtils.getName(entryName),
                                    currentPath,
                                    entryCompressedSize,
                                    actualSize,
                                    false,
                                    false
                            );
                            fileNode.setDocumentLinkId(docId);
                            rootNode.getChildren().add(fileNode);

                            logger.debug("Successfully uploaded file: {} with documentLinkId: {}", entryName, docId);
                        } catch (Exception e) {
                             logger.error("Failed to upload/process file: {}", entryName, e);
                             
                             // Log error to database for this file
                             // Use placeholder ID if upload failed to satisfy NOT NULL constraint
                             childRecord.setDocumentLinkId("ERROR_UPLOAD_FAILED"); 
                             String errorMsg = e.getMessage();
                             if (errorMsg != null && errorMsg.length() > 3000) {
                                 errorMsg = errorMsg.substring(0, 3000);
                             }
                             childRecord.setError(errorMsg);
                             try {
                                kycDocumentUnzipRepository.save(childRecord);
                             } catch (Exception dbEx) {
                                logger.error("Failed to save error record for file: " + entryName, dbEx);
                             }
                             
                             // Re-throw to ensure the main process knows something failed?
                             // Or suppress? Prompt says "log error... ignore status".
                             // Usually file processing is atomic or best-effort. 
                             // If I re-throw, CompletableFuture.allOf().join() will throw.
                             throw new RuntimeException("Failed to upload file: " + entryName, e);
                        }
                    }, uploadExecutor); // Use dedicated upload pool
                    futures.add(future);
                }
            }

            // Wait for all operations (nested zips AND uploads) coming from this zip stream to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            
            return new ProcessingResult(allDocIds, rootNode);
            
        } finally {
             // Cleanup all temp files extracted at this level
             for (File f : tempFilesToClean) {
                 if (f.exists()) {
                     f.delete();
                 }
             }
        }
    }

    private static class ProcessingResult {
        List<String> docIds;
        ZipNode node;

        public ProcessingResult(List<String> docIds, ZipNode node) {
            this.docIds = docIds;
            this.node = node;
        }
    }

    private Map<String, Object> buildChildrenMap(ZipNode node) {
        Map<String, Object> map = new HashMap<>();
        if (node.getChildren() != null) {
            for (ZipNode child : node.getChildren()) {
                map.put(child.getName(), buildChildrenMap(child));
            }
        }
        return map;
    }

    private void populateFilesUnzipped(ZipNode node, Map<String, UnzippedFileDetail> filesUnzipped, AtomicLong totalSizeAccumulator) {
        if (node.getDocumentLinkId() != null) {
            long sizeBytes = node.getSize();
            totalSizeAccumulator.addAndGet(sizeBytes);
            filesUnzipped.put(node.getPath(), new UnzippedFileDetail(
                    node.getName(), node.getDocumentLinkId(), String.valueOf(sizeBytes / 1024.0)
            ));
        }
        if (node.getChildren() != null) {
            for (ZipNode child : node.getChildren()) {
                populateFilesUnzipped(child, filesUnzipped, totalSizeAccumulator);
            }
        }
    }
}
