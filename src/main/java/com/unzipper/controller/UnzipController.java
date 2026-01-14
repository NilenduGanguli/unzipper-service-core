package com.unzipper.controller;

import com.unzipper.client.DocumentumClient;
import com.unzipper.model.DownloadedDocument;
import com.unzipper.model.UnzipDetail;
import com.unzipper.model.UnzipResponse;
import com.unzipper.service.UnzipService;
import com.unzipper.service.UnzipSaveDocService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
public class UnzipController {

    private static final Logger logger = LoggerFactory.getLogger(UnzipController.class);
    private final UnzipService unzipService;
    private final UnzipSaveDocService unzipSaveDocService;
    private final DocumentumClient documentumClient;

    public UnzipController(UnzipService unzipService, UnzipSaveDocService unzipSaveDocService, DocumentumClient documentumClient) {
        this.unzipService = unzipService;
        this.unzipSaveDocService = unzipSaveDocService;
        this.documentumClient = documentumClient;
    }

    @PostMapping("/unzip")
    public ResponseEntity<UnzipResponse> unzip(@RequestParam("file") MultipartFile file) {
        logger.info("Received unzip request for file: {}", file.getOriginalFilename());
        if (file.isEmpty()) {
            logger.warn("Received empty file");
            return ResponseEntity.badRequest().build();
        }
        UnzipResponse response = unzipService.process(file);
        logger.info("Successfully processed file: {}", file.getOriginalFilename());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/unzip_upload_save_doc/{clientId}/{documentLinkId}")
    public ResponseEntity<Map<String, UnzipDetail>> unzipUploadSaveDoc(
            @PathVariable("documentLinkId") String documentLinkId,
            @PathVariable("clientId") String clientId) {
        logger.info("Received unzip_upload_save_doc request for clientId: {}, documentLinkId: {}", clientId, documentLinkId);
        
        if (documentLinkId == null || documentLinkId.trim().isEmpty()) {
            logger.warn("Received empty document_link_id");
            return ResponseEntity.badRequest().build();
        }
        
        if (clientId == null || clientId.trim().isEmpty()) {
            logger.warn("Received empty client_id");
            return ResponseEntity.badRequest().build();
        }
        
        Map<String, UnzipDetail> response = unzipSaveDocService.processDocumentUnzip(documentLinkId, clientId);
        logger.info("Successfully processed unzip_upload_save_doc for documentLinkId: {}, clientId: {}", documentLinkId, clientId);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/unzip_upload_doc/{clientId}", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, UnzipDetail>> unzipUploadDoc(
            @PathVariable("clientId") String clientId,
            @RequestPart("file") MultipartFile file) {
        
        logger.info("Received unzip_upload_doc request for clientId: {}, file: {}", clientId, file.getOriginalFilename());
        
        if (clientId == null || clientId.trim().isEmpty()) {
            logger.warn("Received empty client_id");
            return ResponseEntity.badRequest().build();
        }

        if (file.isEmpty()) {
            logger.warn("Received empty file");
            return ResponseEntity.badRequest().build();
        }
        
        try {
            Map<String, UnzipDetail> response = unzipSaveDocService.processDirectUpload(file, clientId);
            // The key in the map is the generated documentLinkId
            logger.info("Successfully processed unzip_upload_doc for clientId: {}", clientId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
             logger.error("Internal server error during unzip_upload_doc", e);
             return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/fetch_file_documentum/{documentLinkId}")
    public ResponseEntity<Resource> fetchFileDocumentum(@PathVariable("documentLinkId") String documentLinkId) {
        logger.info("Received fetch_file_documentum request for documentLinkId: {}", documentLinkId);

        if (documentLinkId == null || documentLinkId.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        try {
            DownloadedDocument doc = documentumClient.fetchDocument(documentLinkId);
            InputStreamResource resource = new InputStreamResource(doc.getContentStream());

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + doc.getFilename() + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);
        } catch (Exception e) {
            logger.error("Error fetching file from Documentum", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
