package com.unzipper.model;

import java.io.InputStream;

public class DownloadedDocument {
    private String filename;
    private InputStream contentStream;

    public DownloadedDocument(String filename, InputStream contentStream) {
        this.filename = filename;
        this.contentStream = contentStream;
    }

    public String getFilename() {
        return filename;
    }

    public InputStream getContentStream() {
        return contentStream;
    }
}
