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

    @Test
    void readsPlanAssetWorkbookRowsWithOwnerNames() throws Exception {
        List<Map<String, String>> rows = InitialDataWorkbookReader.readRows(
                new ClassPathResource("initial-data/assets_plan_20260629.xlsx")
        );

        assertThat(rows).hasSize(18);
        assertThat(rows.get(0)).containsEntry("J", "명의");
        assertThat(rows.get(0)).containsEntry("K", "확정일");
        assertThat(rows.get(0)).containsEntry("L", "결제일");
        assertThat(rows.subList(1, rows.size()))
                .extracting(row -> row.get("J"))
                .containsOnly("석수");
        assertThat(rows.subList(1, rows.size()))
                .extracting(row -> row.get("B"))
                .contains(
                        "하나은행 은행재원(일시상환) 대출",
                        "하나은행 납부계좌",
                        "KB국민 코웨이III 카드",
                        "삼성카드 taptap O"
                );
        assertThat(rows.subList(12, rows.size()))
                .extracting(row -> row.get("F"))
                .containsOnly("0");
        assertThat(rows.subList(12, rows.size()))
                .extracting(row -> row.get("K"))
                .containsOnly("16");
        assertThat(rows.subList(12, rows.size()))
                .extracting(row -> row.get("L"))
                .containsOnly("25");
    }
}
