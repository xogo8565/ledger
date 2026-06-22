package com.comfortableledger.ledger.repo;

import com.comfortableledger.ledger.domain.ReceiptAttachment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReceiptAttachmentRepository extends JpaRepository<ReceiptAttachment, Long> {
    List<ReceiptAttachment> findByTransactionId(Long transactionId);
}
