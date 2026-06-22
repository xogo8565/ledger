package com.comfortableledger.ledger.web;

import com.comfortableledger.ledger.service.ReceiptService;
import com.comfortableledger.ledger.web.ApiDtos.ReceiptDto;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/transactions/{transactionId}/receipts")
public class ReceiptController {
    private final ReceiptService receiptService;

    public ReceiptController(ReceiptService receiptService) {
        this.receiptService = receiptService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ReceiptDto attach(@PathVariable Long transactionId, @RequestPart MultipartFile file) throws IOException {
        return receiptService.attach(transactionId, file);
    }
}
