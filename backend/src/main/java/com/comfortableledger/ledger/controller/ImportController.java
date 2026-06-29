package com.comfortableledger.ledger.controller;

import static com.comfortableledger.ledger.controller.support.ApiResponses.ok;

import com.comfortableledger.ledger.dto.ApiResponse;
import com.comfortableledger.ledger.service.importing.ImportTextService;
import com.comfortableledger.ledger.dto.ImportDtos.TextImportPreview;
import com.comfortableledger.ledger.dto.ImportDtos.TextImportRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/import")
public class ImportController {
    private final ImportTextService importTextService;

    public ImportController(ImportTextService importTextService) {
        this.importTextService = importTextService;
    }

    @PostMapping("/text/parse")
    public ResponseEntity<ApiResponse<TextImportPreview>> parseText(@RequestBody TextImportRequest request) {
        return ok(importTextService.preview(request.rawText()));
    }
}
