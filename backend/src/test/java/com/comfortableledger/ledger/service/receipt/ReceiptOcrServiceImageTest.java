package com.comfortableledger.ledger.service.receipt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.comfortableledger.ledger.repository.CategoryRepository;
import com.comfortableledger.ledger.repository.HouseholdRepository;
import com.comfortableledger.ledger.repository.TransactionRepository;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import com.comfortableledger.ledger.service.importing.ImportTextService;

class ReceiptOcrServiceImageTest {

    @Test
    void extractsTransactionDraftFromGeneratedReceiptImageWhenTesseractIsAvailable() throws Exception {
        assumeTrue(tesseractAvailable(), "Tesseract is not installed in this test environment");

        HouseholdRepository households = mock(HouseholdRepository.class);
        CategoryRepository categories = mock(CategoryRepository.class);
        TransactionRepository transactions = mock(TransactionRepository.class);
        when(households.findFirstByOrderByIdAsc()).thenReturn(java.util.Optional.empty());
        ReceiptOcrService service = new ReceiptOcrService(
                new ImportTextService(households, categories, transactions),
                "tesseract",
                "kor+eng"
        );

        MockMultipartFile image = new MockMultipartFile(
                "file",
                "sample-receipt.png",
                "image/png",
                receiptImageBytes()
        );

        var result = service.preview(image);

        assertThat(result.rawText()).isNotBlank();
        assertThat(result.rawText()).contains("STARBUCKS");
        assertThat(result.preview().merchant()).contains("STARBUCKS");
        assertThat(result.preview().amount()).isEqualByComparingTo(new BigDecimal("9500"));
    }

    private boolean tesseractAvailable() {
        try {
            Process process = new ProcessBuilder("tesseract", "--version").start();
            return process.waitFor() == 0;
        } catch (Exception exception) {
            return false;
        }
    }

    private byte[] receiptImageBytes() throws Exception {
        BufferedImage image = new BufferedImage(900, 520, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.setColor(Color.BLACK);
        graphics.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 34));
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int y = 70;
        for (String line : new String[] {
                "STARBUCKS GANGNAM",
                "2026-06-26 12:34",
                "AMERICANO 4,500WON",
                "LATTE 5,000WON",
                "TOTAL 9,500WON"
        }) {
            graphics.drawString(line, 60, y);
            y += 72;
        }
        graphics.dispose();

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
