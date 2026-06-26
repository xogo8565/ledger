package com.comfortableledger.ledger.web;

import com.comfortableledger.ledger.service.ReceiptOcrService;
import com.comfortableledger.ledger.web.ApiDtos.ReceiptOcrPreview;
import java.io.IOException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/receipts/ocr")
public class ReceiptOcrController {
    private final ReceiptOcrService receiptOcrService;

    public ReceiptOcrController(ReceiptOcrService receiptOcrService) {
        this.receiptOcrService = receiptOcrService;
    }

    @PostMapping
    public ReceiptOcrPreview preview(@RequestPart MultipartFile file) throws IOException, InterruptedException {
        return receiptOcrService.preview(file);
    }
}

