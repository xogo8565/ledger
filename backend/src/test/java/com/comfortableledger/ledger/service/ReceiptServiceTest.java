package com.comfortableledger.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.comfortableledger.ledger.domain.ReceiptAttachment;
import com.comfortableledger.ledger.domain.TransactionRecord;
import com.comfortableledger.ledger.repository.ReceiptAttachmentRepository;
import com.comfortableledger.ledger.repository.TransactionRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class ReceiptServiceTest {

    @TempDir
    Path uploadDir;

    @Test
    void attachesMultipleImageFilesInOneRequest() throws Exception {
        TransactionRepository transactions = mock(TransactionRepository.class);
        ReceiptAttachmentRepository receipts = mock(ReceiptAttachmentRepository.class);
        when(transactions.findById(1L)).thenReturn(Optional.of(mock(TransactionRecord.class)));
        when(receipts.save(any(ReceiptAttachment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ReceiptService service = new ReceiptService(transactions, receipts, new ReceiptFileStorage(), uploadDir.toString());

        var result = service.attachMany(1L, List.of(
                image("first.png", "first"),
                image("second.jpg", "second")
        ));

        assertThat(result).hasSize(2);
        assertThat(Files.list(uploadDir)).hasSize(2);
    }

    @Test
    void rejectsMoreThanTenFilesBeforeWriting() {
        TransactionRepository transactions = mock(TransactionRepository.class);
        ReceiptAttachmentRepository receipts = mock(ReceiptAttachmentRepository.class);
        when(transactions.findById(1L)).thenReturn(Optional.of(mock(TransactionRecord.class)));
        ReceiptService service = new ReceiptService(transactions, receipts, new ReceiptFileStorage(), uploadDir.toString());
        List<MockMultipartFile> files = IntStream.rangeClosed(1, 11)
                .mapToObj(index -> image(index + ".png", "image"))
                .toList();

        assertThatThrownBy(() -> service.attachMany(1L, List.copyOf(files)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10");
        assertThat(uploadDir).isEmptyDirectory();
        verifyNoInteractions(receipts);
    }

    @Test
    void rejectsNonImageFiles() {
        TransactionRepository transactions = mock(TransactionRepository.class);
        ReceiptAttachmentRepository receipts = mock(ReceiptAttachmentRepository.class);
        when(transactions.findById(1L)).thenReturn(Optional.of(mock(TransactionRecord.class)));
        ReceiptService service = new ReceiptService(transactions, receipts, new ReceiptFileStorage(), uploadDir.toString());
        MockMultipartFile text = new MockMultipartFile("files", "receipt.txt", "text/plain", "not image".getBytes());

        assertThatThrownBy(() -> service.attachMany(1L, List.of(text)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("image");
        verifyNoInteractions(receipts);
    }

    @Test
    void deletesReceiptMetadataAndStoredFile() throws Exception {
        TransactionRepository transactions = mock(TransactionRepository.class);
        ReceiptAttachmentRepository receipts = mock(ReceiptAttachmentRepository.class);
        Path storedFile = uploadDir.resolve("stored-receipt.png");
        Files.writeString(storedFile, "image");
        ReceiptAttachment attachment = new ReceiptAttachment(
                mock(TransactionRecord.class),
                "receipt.png",
                storedFile.toString(),
                "image/png",
                Files.size(storedFile)
        );
        when(receipts.findById(10L)).thenReturn(Optional.of(attachment));
        ReceiptService service = new ReceiptService(transactions, receipts, new ReceiptFileStorage(), uploadDir.toString());

        service.delete(10L);

        verify(receipts).delete(attachment);
        assertThat(storedFile).doesNotExist();
    }

    private MockMultipartFile image(String filename, String contents) {
        return new MockMultipartFile("files", filename, "image/png", contents.getBytes());
    }
}
