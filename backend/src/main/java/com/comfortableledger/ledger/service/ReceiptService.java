package com.comfortableledger.ledger.service;

import com.comfortableledger.ledger.domain.ReceiptAttachment;
import com.comfortableledger.ledger.domain.TransactionRecord;
import com.comfortableledger.ledger.repository.ReceiptAttachmentRepository;
import com.comfortableledger.ledger.repository.TransactionRepository;
import com.comfortableledger.ledger.dto.ApiDtos.ReceiptDto;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ReceiptService {
    static final int MAX_BATCH_FILES = 10;

    private final TransactionRepository transactionRepository;
    private final ReceiptAttachmentRepository receiptAttachmentRepository;
    private final ReceiptFileStorage receiptFileStorage;
    private final Path uploadDir;

    public ReceiptService(TransactionRepository transactionRepository,
                          ReceiptAttachmentRepository receiptAttachmentRepository,
                          ReceiptFileStorage receiptFileStorage,
                          @Value("${app.upload-dir}") String uploadDir) {
        this.transactionRepository = transactionRepository;
        this.receiptAttachmentRepository = receiptAttachmentRepository;
        this.receiptFileStorage = receiptFileStorage;
        this.uploadDir = Path.of(uploadDir);
    }

    @Transactional(rollbackFor = IOException.class)
    public ReceiptDto attach(Long transactionId, MultipartFile file) throws IOException {
        return attachMany(transactionId, List.of(file)).getFirst();
    }

    @Transactional(rollbackFor = IOException.class)
    public List<ReceiptDto> attachMany(Long transactionId, List<MultipartFile> files) throws IOException {
        TransactionRecord transaction = transactionRepository.findById(transactionId).orElseThrow();
        validateFiles(files);
        Files.createDirectories(uploadDir);
        List<Path> storedPaths = new ArrayList<>();
        List<ReceiptDto> attached = new ArrayList<>();
        try {
            for (MultipartFile file : files) {
                StoredReceipt stored = storeFile(file);
                storedPaths.add(stored.path());
                ReceiptAttachment attachment = receiptAttachmentRepository.save(new ReceiptAttachment(
                        transaction,
                        stored.originalName(),
                        stored.path().toString(),
                        file.getContentType(),
                        file.getSize()
                ));
                attached.add(ReceiptDto.from(attachment));
            }
            return attached;
        } catch (IOException | RuntimeException exception) {
            receiptFileStorage.deleteNowQuietly(storedPaths);
            throw exception;
        }
    }

    private StoredReceipt storeFile(MultipartFile file) throws IOException {
        String originalName = file.getOriginalFilename() == null ? "receipt" : file.getOriginalFilename();
        String storedName = UUID.randomUUID() + "-" + originalName.replaceAll("[^a-zA-Z0-9._-]", "_");
        Path target = uploadDir.resolve(storedName);
        file.transferTo(target);
        return new StoredReceipt(originalName, target);
    }

    private void validateFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("At least one receipt file is required");
        }
        if (files.size() > MAX_BATCH_FILES) {
            throw new IllegalArgumentException("Up to " + MAX_BATCH_FILES + " receipt files can be uploaded at once");
        }
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                throw new IllegalArgumentException("Receipt file cannot be empty");
            }
            String contentType = file.getContentType();
            if (contentType == null || !contentType.toLowerCase().startsWith("image/")) {
                throw new IllegalArgumentException("Receipt file must be an image");
            }
        }
    }

    @Transactional(readOnly = true)
    public List<ReceiptDto> receipts(Long transactionId) {
        return receiptAttachmentRepository.findByTransactionId(transactionId).stream()
                .map(ReceiptDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ReceiptFile receiptFile(Long receiptId) throws IOException {
        ReceiptAttachment attachment = receiptAttachmentRepository.findById(receiptId).orElseThrow();
        return new ReceiptFile(
                attachment.getOriginalFilename(),
                attachment.getContentType() == null || attachment.getContentType().isBlank()
                        ? "application/octet-stream"
                        : attachment.getContentType(),
                Files.readAllBytes(Path.of(attachment.getStoredPath()))
        );
    }

    @Transactional
    public void delete(Long receiptId) {
        ReceiptAttachment attachment = receiptAttachmentRepository.findById(receiptId).orElseThrow();
        String storedPath = attachment.getStoredPath();
        receiptAttachmentRepository.delete(attachment);
        receiptFileStorage.deleteAfterCommit(List.of(storedPath));
    }

    public record ReceiptFile(String filename, String contentType, byte[] bytes) {
    }

    private record StoredReceipt(String originalName, Path path) {
    }
}
