package com.unzipper.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import com.unzipper.model.DownloadedDocument;

import javax.net.ssl.KeyManagerFactory;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;

@Component
public class DocumentumClient {

    private static final Logger logger = LoggerFactory.getLogger(DocumentumClient.class);
    
    private final String documentumFetchUrl;
    private final String documentumUploadUrl;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public DocumentumClient(WebClient.Builder webClientBuilder,
                            @Value("${documentum.fetch.url}") String documentumFetchUrl,
                            @Value("${documentum.upload.url}") String documentumUploadUrl,
                            @Value("${documentum.max.memory.size}") int maxMemorySize,
                            @Value("${documentum.cert.enabled}") boolean certEnabled,
                            @Value("${documentum.cert.path}") String certPath,
                            @Value("${documentum.cert.password}") String certPassword) {
        this.documentumFetchUrl = documentumFetchUrl;
        this.documentumUploadUrl = documentumUploadUrl;
        
        HttpClient httpClient = HttpClient.create();

        if (certEnabled) {
            try {
                if (certPath == null || certPath.isBlank()) {
                     logger.warn("Certificate enabled but path is empty. Skipping SSL configuration.");
                } else {
                    logger.info("Configuring SSL with certificate: {}", certPath);
                    KeyStore keyStore = KeyStore.getInstance("PKCS12");
                    try (InputStream keyStoreStream = new FileInputStream(certPath)) {
                        keyStore.load(keyStoreStream, certPassword.toCharArray());
                    }
                    KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                    keyManagerFactory.init(keyStore, certPassword.toCharArray());

                    SslContext sslContext = SslContextBuilder.forClient()
                            .keyManager(keyManagerFactory)
                            .build();

                    httpClient = httpClient.secure(spec -> spec.sslContext(sslContext));
                }
            } catch (Exception e) {
                logger.error("Failed to configure SSL context", e);
                throw new RuntimeException("Failed to configure SSL context", e);
            }
        }

        // Increase buffer size to handle large Base64 encoded files
        this.webClient = webClientBuilder
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(maxMemorySize))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Fetch a document from Documentum by document_link_id
     */
    public DownloadedDocument fetchDocument(String documentLinkId) {
        logger.info("Fetching document from Documentum with documentLinkId: {}", documentLinkId);
        
        try {
            String requestBody = "{\"document_link_id\": \"" + documentLinkId + "\"}";
            
            JsonNode responseJson = webClient.post()
                    .uri(documentumFetchUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue(requestBody))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (responseJson == null || !responseJson.has("content")) {
                throw new RuntimeException("Invalid response from Documentum: Missing 'content' field for documentLinkId: " + documentLinkId);
            }

            String base64Content = responseJson.get("content").asText();
            if (base64Content == null || base64Content.isEmpty()) {
                throw new RuntimeException("Empty content received from Documentum for documentLinkId: " + documentLinkId);
            }

            String filename = documentLinkId + ".zip"; // Fallback default
            if (responseJson.has("filename")) {
                filename = responseJson.get("filename").asText();
            } else if (responseJson.has("file_name")) {
                filename = responseJson.get("file_name").asText();
            }

            byte[] decodedBytes = java.util.Base64.getDecoder().decode(base64Content);
            logger.info("Successfully fetched and decoded document from Documentum, size: {} bytes, filename: {}", decodedBytes.length, filename);
            return new DownloadedDocument(filename, new java.io.ByteArrayInputStream(decodedBytes));

        } catch (Exception e) {
            logger.error("Error fetching document from Documentum", e);
            throw new RuntimeException("Failed to fetch document from Documentum: " + e.getMessage(), e);
        }
    }

    /**
     * Upload extracted file to Documentum and return the document_link_id
     */
    public String uploadDocument(byte[] fileContent, String fileName, String parentDocumentLinkId) {
        logger.info("Uploading document to Documentum: {} with parent: {}", fileName, parentDocumentLinkId);
        
        try {
            // Prepare multipart upload request
            // This is a simplified example - adjust according to actual Documentum API
            String uploadResponse = webClient.post()
                    .uri(documentumUploadUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue(createUploadRequest(fileContent, fileName, parentDocumentLinkId)))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            // Parse the response to extract document_link_id
            JsonNode responseJson = objectMapper.readTree(uploadResponse);
            String documentLinkId = responseJson.get("document_link_id").asText();
            
            logger.info("Successfully uploaded document to Documentum, received documentLinkId: {}", documentLinkId);
            return documentLinkId;

        } catch (Exception e) {
            logger.error("Error uploading document to Documentum", e);
            throw new RuntimeException("Failed to upload document to Documentum: " + e.getMessage(), e);
        }
    }

    private String createUploadRequest(byte[] fileContent, String fileName, String parentDocumentLinkId) {
        try {
            // Updated to match Documentum Service OpenAPI schema:
            // {"content": "...", "filename": "..."}
            // Note: parentDocumentLinkId is not part of the schema, so it is omitted or handled elsewhere?
            // Since the user requirements mentioned simple upload, we will follow the schema.
            return objectMapper.writeValueAsString(java.util.Map.of(
                    "filename", fileName,
                    "content", java.util.Base64.getEncoder().encodeToString(fileContent)
            ));
        } catch (Exception e) {
            logger.error("Error creating upload request", e);
            throw new RuntimeException("Failed to create upload request", e);
        }
    }
}
