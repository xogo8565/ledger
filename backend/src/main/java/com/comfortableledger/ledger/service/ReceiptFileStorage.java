package com.comfortableledger.ledger.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class ReceiptFileStorage {
    private static final Logger log = LoggerFactory.getLogger(ReceiptFileStorage.class);

    public void deleteAfterCommit(Collection<String> storedPaths) {
        List<Path> paths = storedPaths.stream()
                .filter(path -> path != null && !path.isBlank())
                .map(Path::of)
                .toList();
        if (paths.isEmpty()) return;

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteNowQuietly(paths);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteNowQuietly(paths);
            }
        });
    }

    public void deleteNowQuietly(Collection<Path> paths) {
        for (Path path : paths) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException exception) {
                log.warn("Failed to delete receipt file: {}", path, exception);
            }
        }
    }
}
