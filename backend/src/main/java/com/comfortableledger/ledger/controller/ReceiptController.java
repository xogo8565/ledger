package com.comfortableledger.ledger.controller;

import com.comfortableledger.ledger.service.ReceiptService;
import com.comfortableledger.ledger.service.ReceiptService.ReceiptFile;
import com.comfortableledger.ledger.dto.ApiDtos.ReceiptDto;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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

    @PostMapping(path = "/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public List<ReceiptDto> attachMany(
            @PathVariable Long transactionId,
            @RequestPart("files") List<MultipartFile> files
    ) throws IOException {
        return receiptService.attachMany(transactionId, files);
    }

    @GetMapping
    public List<ReceiptDto> receipts(@PathVariable Long transactionId) {
        return receiptService.receipts(transactionId);
    }

    @GetMapping("/{receiptId}/file")
    public ResponseEntity<byte[]> file(@PathVariable Long receiptId) throws IOException {
        ReceiptFile file = receiptService.receiptFile(receiptId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.filename() + "\"")
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.bytes());
    }

    @DeleteMapping("/{receiptId}")
    public void delete(@PathVariable Long receiptId) {
        receiptService.delete(receiptId);
    }
}
