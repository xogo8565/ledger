package com.comfortableledger.ledger.service;

import com.comfortableledger.ledger.domain.ReceiptAttachment;
import com.comfortableledger.ledger.domain.TransactionRecord;
import com.comfortableledger.ledger.repo.ReceiptAttachmentRepository;
import com.comfortableledger.ledger.repo.TransactionRepository;
import com.comfortableledger.ledger.web.ApiDtos.ReceiptDto;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ReceiptService {
    private final TransactionRepository transactionRepository;
    private final ReceiptAttachmentRepository receiptAttachmentRepository;
    private final Path uploadDir;

    public ReceiptService(TransactionRepository transactionRepository,
                          ReceiptAttachmentRepository receiptAttachmentRepository,
                          @Value("${app.upload-dir}") String uploadDir) {
        this.transactionRepository = transactionRepository;
        this.receiptAttachmentRepository = receiptAttachmentRepository;
        this.uploadDir = Path.of(uploadDir);
    }

    @Transactional
    public ReceiptDto attach(Long transactionId, MultipartFile file) throws IOException {
        TransactionRecord transaction = transactionRepository.findById(transactionId).orElseThrow();
        Files.createDirectories(uploadDir);
        String originalName = file.getOriginalFilename() == null ? "receipt" : file.getOriginalFilename();
        String storedName = UUID.randomUUID() + "-" + originalName.replaceAll("[^a-zA-Z0-9._-]", "_");
        Path target = uploadDir.resolve(storedName);
        file.transferTo(target);
        ReceiptAttachment attachment = receiptAttachmentRepository.save(new ReceiptAttachment(
                transaction,
                originalName,
                target.toString(),
                file.getContentType(),
                file.getSize()
        ));
        return ReceiptDto.from(attachment);
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
    public void delete(Long receiptId) throws IOException {
        ReceiptAttachment attachment = receiptAttachmentRepository.findById(receiptId).orElseThrow();
        Path path = Path.of(attachment.getStoredPath());
        receiptAttachmentRepository.delete(attachment);
        Files.deleteIfExists(path);
    }

    public record ReceiptFile(String filename, String contentType, byte[] bytes) {
    }
}
