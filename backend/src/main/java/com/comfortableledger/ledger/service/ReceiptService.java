package com.comfortableledger.ledger.service;

import com.comfortableledger.ledger.domain.ReceiptAttachment;
import com.comfortableledger.ledger.domain.TransactionRecord;
import com.comfortableledger.ledger.repo.ReceiptAttachmentRepository;
import com.comfortableledger.ledger.repo.TransactionRepository;
import com.comfortableledger.ledger.web.ApiDtos.ReceiptDto;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
}
