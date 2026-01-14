package com.unzipper.service;

import com.unzipper.model.UnzipResponse;
import com.unzipper.model.ZipNode;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.web.multipart.MultipartFile;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class UnzipService implements DisposableBean {

    private static final Logger logger = LoggerFactory.getLogger(UnzipService.class);

    // High performance thread pool for I/O bound tasks
    private final ExecutorService executor;

    public UnzipService() {
        // Create a pool optimized for I/O tasks. 
        // Using a high number of threads since many will be waiting on "upload" I/O.
        // For CPU bound compression, we are limited by the single stream read, but recursive zips offer parallelism.
        int threads = Runtime.getRuntime().availableProcessors() * 8; 
        this.executor = Executors.newFixedThreadPool(threads);
    }

    @Override
    public void destroy() {
        if (executor != null) {
            executor.shutdown();
        }
    }

    public UnzipResponse process(MultipartFile file) {
        logger.debug("Starting processing of file: {} size: {}", file.getOriginalFilename(), file.getSize());
        try {
            // Use a temporary directory for processing
            Path tempDir = Files.createTempDirectory("unzipper_service_");
            File tempFile = tempDir.resolve(Objects.requireNonNull(file.getOriginalFilename())).toFile();
            file.transferTo(tempFile);

            // Start the recursive process
            ProcessingResult result = processZipFile(tempFile, "", tempFile.length()).get();

            // Cleanup
            try {
                Files.walk(tempDir)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
            } catch (Exception ignored) {}

            return new UnzipResponse(result.docIds, result.node);

        } catch (Exception e) {
            logger.error("Error processing zip file", e);
            throw new RuntimeException("Failed to process zip file", e);
        }
    }

    private CompletableFuture<ProcessingResult> processZipFile(File file, String relativePath, long compressedSize) {
        return CompletableFuture.supplyAsync(() -> {
            logger.debug("Processing zip file: {}", file.getName());
            try (FileInputStream fis = new FileInputStream(file);
                 ZipInputStream zis = new ZipInputStream(new BufferedInputStream(fis))) {
                
                return processStream(zis, relativePath, file.getName(), compressedSize, file.length());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }

    private ProcessingResult processStream(ZipInputStream zis, String parentPath, String zipName, long compressedSize, long totalSize) throws IOException, ExecutionException, InterruptedException {
        ZipNode rootNode = new ZipNode(
            zipName,
            parentPath.isEmpty() ? zipName : parentPath, 
            compressedSize,
            totalSize, // This will be updated with sum of children
            false,
            true
        );
        rootNode.setChildren(Collections.synchronizedList(new ArrayList<>()));
        
        List<String> allDocIds = Collections.synchronizedList(new ArrayList<>());
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        ZipEntry entry;
        while ((entry = zis.getNextEntry()) != null) {
            final String entryName = entry.getName();
            // Normalize path separator
            final String currentPath = parentPath.isEmpty() ? entryName : parentPath + "/" + entryName;
            
            // Capture entry details for the lambda
            long entryCompressedSize = entry.getCompressedSize();

            if (entry.isDirectory()) {
                ZipNode dirNode = new ZipNode(
                    FilenameUtils.getName(entryName.endsWith("/") ? entryName.substring(0, entryName.length() - 1) : entryName),
                    currentPath,
                    entryCompressedSize,
                    0,
                    true,
                    false
                );
                rootNode.getChildren().add(dirNode);
                continue;
            }
            
            // Let's create a temp file for THIS entry.
            Path tempEntryFile = Files.createTempFile("entry_", ".tmp");
            try (OutputStream out = Files.newOutputStream(tempEntryFile)) {
                IOUtils.copy(zis, out);
            }

            File processedFile = tempEntryFile.toFile();
            long actualSize = processedFile.length();
            
            // Determine if it is a zip file
            boolean isZip = FilenameUtils.getExtension(entryName).equalsIgnoreCase("zip");
            
            if (isZip) {
                // Recurse
               CompletableFuture<Void> future = processZipFile(processedFile, currentPath, entryCompressedSize)
                        .thenAccept(result -> {
                            rootNode.getChildren().add(result.node);
                            allDocIds.addAll(result.docIds);
                            // Delete temp file after processing
                            processedFile.delete();
                        });
               futures.add(future);
            } else {
                // Upload
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        String docId = mockUpload(processedFile, currentPath);
                        allDocIds.add(docId);
                        
                        ZipNode fileNode = new ZipNode(
                                FilenameUtils.getName(entryName),
                                currentPath,
                                entryCompressedSize,
                                actualSize,
                                false,
                                false
                        );
                        rootNode.getChildren().add(fileNode);
                        
                        // Delete temp file after upload
                        processedFile.delete();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }, executor);
                futures.add(future);
            }
        }

        // Wait for all children to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // Calculate total extracted size for the root node (simple sum of children approx)
        long extractedSize = rootNode.getChildren().stream().mapToLong(ZipNode::getSize).sum();
        rootNode.setSize(extractedSize);

        return new ProcessingResult(rootNode, allDocIds);
    }

    // Mock upload service
    private String mockUpload(File file, String path) throws InterruptedException {
        // Simulate network latency
        Thread.sleep(50); 
        // Return a mock ID
        return "DOC_" + UUID.nameUUIDFromBytes(path.getBytes()).toString().substring(0, 8);
    }

    private static class ProcessingResult {
        final ZipNode node;
        final List<String> docIds;

        public ProcessingResult(ZipNode node, List<String> docIds) {
            this.node = node;
            this.docIds = docIds;
        }
    }
}
