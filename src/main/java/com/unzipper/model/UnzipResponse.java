package com.unzipper.model;

import java.util.List;

public class UnzipResponse {
    private List<String> docIds;
    private ZipNode metadata;

    public UnzipResponse() {}

    public UnzipResponse(List<String> docIds, ZipNode metadata) {
        this.docIds = docIds;
        this.metadata = metadata;
    }

    public List<String> getDocIds() {
        return docIds;
    }

    public void setDocIds(List<String> docIds) {
        this.docIds = docIds;
    }

    public ZipNode getMetadata() {
        return metadata;
    }

    public void setMetadata(ZipNode metadata) {
        this.metadata = metadata;
    }
}
