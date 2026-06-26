package com.comfortableledger.ledger.service.importing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class InitialDataWorkbookReaderTest {
    @Test
    void readsInitialAssetWorkbookRows() throws Exception {
        List<Map<String, String>> rows = InitialDataWorkbookReader.readRows(
                new ClassPathResource("initial-data/assets_자산설정_초기거래.xlsx")
        );

        assertThat(rows).hasSize(9);
        assertThat(rows.get(0)).containsEntry("A", "날짜");
        assertThat(rows.subList(1, rows.size()))
                .extracting(row -> row.get("F"))
                .contains("1534679", "10500000", "11675076");
    }

    @Test
    void readsInitialTransactionWorkbookRows() throws Exception {
        List<Map<String, String>> rows = InitialDataWorkbookReader.readRows(
                new ClassPathResource("initial-data/transactions_6월_가계부.xlsx")
        );

        assertThat(rows).hasSize(124);
        assertThat(rows.get(0)).containsEntry("A", "날짜");
        assertThat(rows.subList(1, rows.size()))
                .extracting(row -> row.get("G"))
                .contains("지출");
        assertThat(rows.subList(1, rows.size()))
                .extracting(row -> row.get("F"))
                .doesNotContainNull();
    }
}
