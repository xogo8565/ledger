package com.comfortableledger.ledger.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

@Entity
@Table(name = "initial_data_imports")
public class InitialDataImport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    private String resourceName;

    @NotBlank
    private String resourceKind;

    @NotBlank
    private String checksum;

    @NotNull
    private OffsetDateTime importedAt;

    private int rowCount;

    protected InitialDataImport() {
    }

    public InitialDataImport(String resourceName, String resourceKind, String checksum, int rowCount) {
        this.resourceName = resourceName;
        this.resourceKind = resourceKind;
        this.checksum = checksum;
        this.rowCount = rowCount;
        this.importedAt = OffsetDateTime.now();
    }

    public String getResourceName() {
        return resourceName;
    }

    public Long getId() {
        return id;
    }

    public String getResourceKind() {
        return resourceKind;
    }

    public String getChecksum() {
        return checksum;
    }

    public OffsetDateTime getImportedAt() {
        return importedAt;
    }

    public int getRowCount() {
        return rowCount;
    }

    public void markImported(String checksum, int rowCount) {
        this.checksum = checksum;
        this.rowCount = rowCount;
        this.importedAt = OffsetDateTime.now();
    }
}
