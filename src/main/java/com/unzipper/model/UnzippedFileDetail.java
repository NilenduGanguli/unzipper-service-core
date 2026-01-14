package com.unzipper.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class UnzippedFileDetail {
    @JsonProperty("file_name")
    private String fileName;
    
    @JsonProperty("document_link_id")
    private String documentLinkId;
    
    @JsonProperty("file_size")
    private String fileSize;

    public UnzippedFileDetail() {}

    public UnzippedFileDetail(String fileName, String documentLinkId, String fileSize) {
        this.fileName = fileName;
        this.documentLinkId = documentLinkId;
        this.fileSize = fileSize;
    }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getDocumentLinkId() { return documentLinkId; }
    public void setDocumentLinkId(String documentLinkId) { this.documentLinkId = documentLinkId; }

    public String getFileSize() { return fileSize; }
    public void setFileSize(String fileSize) { this.fileSize = fileSize; }
}
