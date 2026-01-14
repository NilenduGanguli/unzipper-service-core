package com.unzipper.entity;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "kyc_document_unzip", schema = "dbo")
public class KycDocumentUnzip {

    @Id
    @Column(name = "KYC_UNZIP_ID", length = 36, columnDefinition = "nvarchar(36)")
    private String kycUnzipId = java.util.UUID.randomUUID().toString();

    @Column(name = "CLIENT_ID", nullable = true, columnDefinition = "nvarchar(255)")
    private String clientId;

    @Column(name = "DOCUMENT_LINK_ID", nullable = false, columnDefinition = "nvarchar(255)")
    private String documentLinkId;

    @Column(name = "DOCUMENT_NAME", columnDefinition = "nvarchar(255)")
    private String documentName;

    @Column(name = "DOCUMENT_TYPE", columnDefinition = "nvarchar(100)")
    private String documentType;

    @Column(name = "PARENT_DOCUMENT_LINK_ID", columnDefinition = "nvarchar(255)")
    private String parentDocumentLinkId;

    @Column(name = "LST_UPD_TIME")
    private LocalTime lstUpdTime;

    @Column(name = "LST_UPD_DT")
    private LocalDate lstUpdDt;

    @Column(name = "DOCUMENT_PATH", columnDefinition = "nvarchar(MAX)")
    private String documentPath;

    @Column(name = "STATUS")
    private Boolean status;

    @Column(name = "ERROR", columnDefinition = "nvarchar(3000)")
    private String error;


    public KycDocumentUnzip() {
    }

    public KycDocumentUnzip(String clientId, String documentLinkId) {
        this.clientId = clientId;
        this.documentLinkId = documentLinkId;
        this.lstUpdTime = LocalTime.now();
        this.lstUpdDt = LocalDate.now();
    }

    // Getters and Setters
    public String getKycUnzipId() {
        return kycUnzipId;
    }

    public void setKycUnzipId(String kycUnzipId) {
        this.kycUnzipId = kycUnzipId;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getDocumentLinkId() {
        return documentLinkId;
    }

    public void setDocumentLinkId(String documentLinkId) {
        this.documentLinkId = documentLinkId;
    }

    public String getDocumentName() {
        return documentName;
    }

    public void setDocumentName(String documentName) {
        this.documentName = documentName;
    }

    public String getDocumentType() {
        return documentType;
    }

    public void setDocumentType(String documentType) {
        this.documentType = documentType;
    }

    public String getParentDocumentLinkId() {
        return parentDocumentLinkId;
    }

    public void setParentDocumentLinkId(String parentDocumentLinkId) {
        this.parentDocumentLinkId = parentDocumentLinkId;
    }

    public LocalTime getLstUpdTime() {
        return lstUpdTime;
    }

    public void setLstUpdTime(LocalTime lstUpdTime) {
        this.lstUpdTime = lstUpdTime;
    }

    public LocalDate getLstUpdDt() {
        return lstUpdDt;
    }

    public void setLstUpdDt(LocalDate lstUpdDt) {
        this.lstUpdDt = lstUpdDt;
    }

    public String getDocumentPath() {
        return documentPath;
    }

    public void setDocumentPath(String documentPath) {
        this.documentPath = documentPath;
    }

    public Boolean getStatus() {
        return status;
    }

    public void setStatus(Boolean status) {
        this.status = status;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
