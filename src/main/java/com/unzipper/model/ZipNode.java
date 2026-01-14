package com.unzipper.model;

import java.util.ArrayList;
import java.util.List;

public class ZipNode {
    private String name;
    private String path; // Relative path from root
    private long compressedSize;
    private long size;
    private List<ZipNode> children = new ArrayList<>();
    private boolean isDirectory;
    private boolean isArchive; // If it was a nested zip that we extracted
    private String documentLinkId; // New field for Documentum ID

    public ZipNode() {}

    public ZipNode(String name, String path, long compressedSize, long size, boolean isDirectory, boolean isArchive) {
        this.name = name;
        this.path = path;
        this.compressedSize = compressedSize;
        this.size = size;
        this.isDirectory = isDirectory;
        this.isArchive = isArchive;
    }

    public String getDocumentLinkId() { return documentLinkId; }
    public void setDocumentLinkId(String documentLinkId) { this.documentLinkId = documentLinkId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public long getCompressedSize() { return compressedSize; }
    public void setCompressedSize(long compressedSize) { this.compressedSize = compressedSize; }

    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }

    public List<ZipNode> getChildren() { return children; }
    public void setChildren(List<ZipNode> children) { this.children = children; }

    public boolean isDirectory() { return isDirectory; }
    public void setDirectory(boolean directory) { isDirectory = directory; }

    public boolean isArchive() { return isArchive; }
    public void setArchive(boolean archive) { isArchive = archive; }
}
