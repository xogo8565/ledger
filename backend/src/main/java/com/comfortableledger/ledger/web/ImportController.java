package com.comfortableledger.ledger.web;

import com.comfortableledger.ledger.service.ImportTextService;
import com.comfortableledger.ledger.web.ApiDtos.TextImportPreview;
import com.comfortableledger.ledger.web.ApiDtos.TextImportRequest;
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
    public TextImportPreview parseText(@RequestBody TextImportRequest request) {
        return importTextService.preview(request.rawText());
    }
}
