package com.comfortableledger.ledger.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

@Entity
@Table(name = "receipt_attachments")
public class ReceiptAttachment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @NotNull
    private TransactionRecord transaction;

    @NotBlank
    private String originalFilename;

    @NotBlank
    private String storedPath;

    private String contentType;
    private long size;
    private OffsetDateTime createdAt;

    protected ReceiptAttachment() {
    }

    public ReceiptAttachment(TransactionRecord transaction, String originalFilename, String storedPath,
                             String contentType, long size) {
        this.transaction = transaction;
        this.originalFilename = originalFilename;
        this.storedPath = storedPath;
        this.contentType = contentType;
        this.size = size;
        this.createdAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getStoredPath() {
        return storedPath;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSize() {
        return size;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void reassignTo(TransactionRecord transaction) {
        this.transaction = transaction;
    }
}
