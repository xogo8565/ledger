package com.comfortableledger.ledger.controller;

import static com.comfortableledger.ledger.controller.support.ApiResponses.ok;

import com.comfortableledger.ledger.dto.ApiResponse;
import com.comfortableledger.ledger.service.receipt.ReceiptOcrService;
import com.comfortableledger.ledger.dto.ReceiptDtos.ReceiptOcrPreview;
import java.io.IOException;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<ApiResponse<ReceiptOcrPreview>> preview(@RequestPart MultipartFile file) throws IOException, InterruptedException {
        return ok(receiptOcrService.preview(file));
    }
}

