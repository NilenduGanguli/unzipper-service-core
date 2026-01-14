package com.unzipper.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public class UnzipDetail {
    @JsonProperty("document_link_id")
    private String documentLinkId;

    @JsonProperty("client_id")
    private String clientId;

    @JsonProperty("file_name")
    private String fileName;

    @JsonProperty("zipped_size")
    private String zippedSize;

    @JsonProperty("unzipped_size")
    private String unzippedSize;

    @JsonProperty("tree_struct")
    private Map<String, Object> treeStruct;

    @JsonProperty("files_unzipped")
    private Map<String, UnzippedFileDetail> filesUnzipped;

    public UnzipDetail() {}

    public String getDocumentLinkId() { return documentLinkId; }
    public void setDocumentLinkId(String documentLinkId) { this.documentLinkId = documentLinkId; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getZippedSize() { return zippedSize; }
    public void setZippedSize(String zippedSize) { this.zippedSize = zippedSize; }

    public String getUnzippedSize() { return unzippedSize; }
    public void setUnzippedSize(String unzippedSize) { this.unzippedSize = unzippedSize; }

    public Map<String, Object> getTreeStruct() { return treeStruct; }
    public void setTreeStruct(Map<String, Object> treeStruct) { this.treeStruct = treeStruct; }

    public Map<String, UnzippedFileDetail> getFilesUnzipped() { return filesUnzipped; }
    public void setFilesUnzipped(Map<String, UnzippedFileDetail> filesUnzipped) { this.filesUnzipped = filesUnzipped; }
}
