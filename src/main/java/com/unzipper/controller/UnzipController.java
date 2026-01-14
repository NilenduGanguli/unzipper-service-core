package com.unzipper.controller;

import com.unzipper.model.UnzipDetail;
import com.unzipper.model.UnzipResponse;
import com.unzipper.service.UnzipService;
import com.unzipper.service.UnzipSaveDocService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
public class UnzipController {

    private static final Logger logger = LoggerFactory.getLogger(UnzipController.class);
    private final UnzipService unzipService;
    private final UnzipSaveDocService unzipSaveDocService;

    public UnzipController(UnzipService unzipService, UnzipSaveDocService unzipSaveDocService) {
        this.unzipService = unzipService;
        this.unzipSaveDocService = unzipSaveDocService;
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

    @GetMapping("/unzip_save_doc")
    public ResponseEntity<Map<String, UnzipDetail>> unzipSaveDoc(
            @RequestParam("document_link_id") String documentLinkId,
            @RequestParam("client_id") String clientId) {
        logger.info("Received unzip_save_doc request for documentLinkId: {}, clientId: {}", documentLinkId, clientId);
        
        if (documentLinkId == null || documentLinkId.trim().isEmpty()) {
            logger.warn("Received empty document_link_id");
            return ResponseEntity.badRequest().build();
        }
        
        if (clientId == null || clientId.trim().isEmpty()) {
            logger.warn("Received empty client_id");
            return ResponseEntity.badRequest().build();
        }
        
        Map<String, UnzipDetail> response = unzipSaveDocService.processDocumentUnzip(documentLinkId, clientId);
        logger.info("Successfully processed unzip_save_doc for documentLinkId: {}, clientId: {}", documentLinkId, clientId);
        return ResponseEntity.ok(response);
    }
}
