package com.comfortableledger.ledger.service.importing;

import com.comfortableledger.ledger.domain.Category;
import com.comfortableledger.ledger.domain.CategoryType;
import com.comfortableledger.ledger.domain.Household;
import com.comfortableledger.ledger.domain.TransactionRecord;
import com.comfortableledger.ledger.domain.TransactionType;
import com.comfortableledger.ledger.dto.ImportDtos.TextImportPreview;
import com.comfortableledger.ledger.repository.CategoryRepository;
import com.comfortableledger.ledger.repository.HouseholdRepository;
import com.comfortableledger.ledger.repository.TransactionRepository;
import com.comfortableledger.ledger.util.NumberValues;
import com.comfortableledger.ledger.util.StringValues;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ImportTextService {
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("([0-9][0-9,]{2,})\\s*(?:원|won|WON)?");
    private static final Pattern RECEIPT_TOTAL_AMOUNT_PATTERN = Pattern.compile(
            "(?:합계|총액|총 결제 금액|결제금액|받을금액|매출금액|total)\\s*[: ]*([0-9,]+)\\s*(?:원|won)?",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern YEAR_DATE_PATTERN = Pattern.compile("(20\\d{2})[./-]\\s*(\\d{1,2})[./-]\\s*(\\d{1,2})");
    private static final Pattern MONTH_DATE_PATTERN = Pattern.compile("(\\d{1,2})[./월]\\s*(\\d{1,2})\\s*(?:일)?");
    private static final Pattern SUPPORTING_AMOUNT_PATTERN = Pattern.compile("(?:잔액|누적|한도|사용가능|총액)\\s*[: ]*([0-9,]+)\\s*원?");
    private static final Pattern TIME_PATTERN = Pattern.compile("\\b\\d{1,2}[:시]\\d{2}\\b");
    private static final Pattern CARD_SUFFIX_PATTERN = Pattern.compile("\\b\\d{2,4}[-*]\\*{2,4}\\b|\\(\\d{3,4}\\)");
    private static final List<String> ISSUER_WORDS = List.of(
            "국민", "KB", "신한", "삼성", "현대", "롯데", "우리", "하나", "NH", "농협", "BC", "비씨",
            "카카오", "토스", "케이뱅크", "국민은행", "신한은행", "우리은행", "하나은행", "농협은행"
    );
    private static final List<String> NOISE_WORDS = List.of(
            "카드", "체크", "체크카드", "승인", "사용", "이용", "출금", "입금", "취소", "승인취소",
            "환불", "일시불", "국내", "해외", "결제", "전자금융", "자동입금", "입출금", "알림"
    );
    private static final Map<String, List<String>> EXPENSE_CATEGORY_KEYWORDS = new LinkedHashMap<>();
    private static final Map<String, List<String>> INCOME_CATEGORY_KEYWORDS = new LinkedHashMap<>();

    static {
        EXPENSE_CATEGORY_KEYWORDS.put("식비", List.of("스타벅스", "카페", "커피", "식당", "김밥", "치킨", "피자", "버거", "배달", "푸드", "레스토랑"));
        EXPENSE_CATEGORY_KEYWORDS.put("마트/편의점", List.of("cu", "gs25", "세븐일레븐", "이마트", "홈플러스", "롯데마트", "마트", "편의점"));
        EXPENSE_CATEGORY_KEYWORDS.put("교통/차량", List.of("택시", "카카오t", "주유", "충전소", "하이패스", "코레일", "철도", "버스", "지하철", "주차"));
        EXPENSE_CATEGORY_KEYWORDS.put("문화생활", List.of("영화", "극장", "서점", "공연", "티켓", "게임", "넷플릭스", "유튜브"));
        EXPENSE_CATEGORY_KEYWORDS.put("패션/미용", List.of("미용실", "헤어", "네일", "의류", "패션", "무신사", "올리브영"));
        EXPENSE_CATEGORY_KEYWORDS.put("생활용품", List.of("다이소", "가구", "생활용품", "문구", "인상"));
        EXPENSE_CATEGORY_KEYWORDS.put("주거/통신", List.of("통신", "인터넷", "전기", "가스", "수도", "관리비", "월세"));
        EXPENSE_CATEGORY_KEYWORDS.put("건강", List.of("병원", "의원", "약국", "치과", "한의원", "건강", "검진"));
        INCOME_CATEGORY_KEYWORDS.put("급여", List.of("급여", "월급", "상여", "보너스"));
        INCOME_CATEGORY_KEYWORDS.put("이자", List.of("이자", "예금이자"));
        INCOME_CATEGORY_KEYWORDS.put("부수입", List.of("부수입", "정산", "수익", "판매"));
    }

    private final HouseholdRepository householdRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;

    public ImportTextService(HouseholdRepository householdRepository,
                             CategoryRepository categoryRepository,
                             TransactionRepository transactionRepository) {
        this.householdRepository = householdRepository;
        this.categoryRepository = categoryRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional(readOnly = true)
    public TextImportPreview preview(String rawText) {
        String sourceText = rawText == null ? "" : rawText;
        String normalized = normalize(sourceText);
        String primaryText = stripSupportingAmounts(normalized);
        List<ReceiptLineItem> receiptLineItems = extractReceiptLineItems(sourceText);
        Optional<ReceiptLineItem> receiptLineItem = receiptLineItems.stream().findFirst();
        BigDecimal amount = extractReceiptTotalAmount(normalized)
                .or(() -> receiptLineItem.map(ReceiptLineItem::amount))
                .orElseGet(() -> extractAmount(primaryText));
        LocalDate date = extractDate(primaryText);
        TransactionType type = extractType(primaryText);
        String merchant = receiptLineItem
                .map(ReceiptLineItem::name)
                .filter(name -> !name.isBlank())
                .orElseGet(() -> extractMerchant(sourceText, primaryText));
        CategoryRecommendation recommendation = recommendCategory(type, merchant);
        return new TextImportPreview(
                rawText,
                type,
                date,
                amount,
                merchant,
                importMemo(primaryText, type, isReceiptLike(sourceText), receiptLineItems),
                recommendation.category() == null ? null : recommendation.category().getId(),
                recommendation.category() == null ? null : recommendation.category().getName(),
                recommendation.reason()
        );
    }

    private CategoryRecommendation recommendCategory(TransactionType type, String merchant) {
        Household household = householdRepository.findFirstByOrderByIdAsc().orElse(null);
        if (household == null || type == TransactionType.TRANSFER) {
            return CategoryRecommendation.empty();
        }
        CategoryType categoryType = type == TransactionType.INCOME ? CategoryType.INCOME : CategoryType.EXPENSE;
        List<Category> categories = categoryRepository
                .findByHouseholdIdAndTypeAndActiveTrueOrderBySortOrderAscIdAsc(household.getId(), categoryType);
        if (categories.isEmpty()) {
            return CategoryRecommendation.empty();
        }

        String merchantKey = recommendationKey(merchant);
        if (!merchantKey.isBlank() && !merchantKey.equals(recommendationKey("자동 입력"))) {
            Optional<Category> historyCategory = transactionRepository
                    .findTop200ByHouseholdIdOrderByTransactionDateDescIdDesc(household.getId())
                    .stream()
                    .filter(record -> record.getType() == type)
                    .filter(record -> record.getCategory() != null && record.getCategory().isActive())
                    .filter(record -> sameMerchant(record, merchantKey))
                    .collect(Collectors.groupingBy(TransactionRecord::getCategory, Collectors.counting()))
                    .entrySet()
                    .stream()
                    .sorted(Map.Entry.<Category, Long>comparingByValue().reversed()
                            .thenComparing(entry -> entry.getKey().getSortOrder())
                            .thenComparing(entry -> entry.getKey().getId()))
                    .map(Map.Entry::getKey)
                    .findFirst();
            if (historyCategory.isPresent()) {
                return new CategoryRecommendation(historyCategory.get(), "같은 가맹점의 이전 분류");
            }
        }

        Map<String, List<String>> keywordRules = type == TransactionType.INCOME
                ? INCOME_CATEGORY_KEYWORDS
                : EXPENSE_CATEGORY_KEYWORDS;
        String searchable = normalizeForSearch(merchant);
        for (Map.Entry<String, List<String>> rule : keywordRules.entrySet()) {
            boolean matched = rule.getValue().stream()
                    .map(this::normalizeForSearch)
                    .anyMatch(searchable::contains);
            if (!matched) continue;
            Optional<Category> category = categories.stream()
                    .filter(item -> item.getName().equals(rule.getKey()))
                    .findFirst();
            if (category.isPresent()) {
                return new CategoryRecommendation(category.get(), "가맹점 키워드 추천");
            }
        }
        return CategoryRecommendation.empty();
    }

    private boolean sameMerchant(TransactionRecord record, String merchantKey) {
        String titleKey = recommendationKey(record.getTitle());
        return !titleKey.isBlank() && (titleKey.contains(merchantKey) || merchantKey.contains(titleKey));
    }

    private String recommendationKey(String value) {
        return normalizeForSearch(value).replaceAll("[^가-힣a-z0-9]", "");
    }

    private String normalizeForSearch(String value) {
        return StringValues.normalizeSearchKey(value);
    }

    private BigDecimal extractAmount(String rawText) {
        String amountSearchText = YEAR_DATE_PATTERN.matcher(rawText).replaceAll(" ");
        amountSearchText = MONTH_DATE_PATTERN.matcher(amountSearchText).replaceAll(" ");
        Matcher matcher = AMOUNT_PATTERN.matcher(amountSearchText);
        if (matcher.find()) {
            return NumberValues.parseWonAmount(matcher.group(1));
        }
        return BigDecimal.ZERO;
    }

    private Optional<BigDecimal> extractReceiptTotalAmount(String rawText) {
        Matcher matcher = RECEIPT_TOTAL_AMOUNT_PATTERN.matcher(rawText);
        BigDecimal selected = null;
        while (matcher.find()) {
            selected = NumberValues.parseWonAmount(matcher.group(1));
        }
        return Optional.ofNullable(selected);
    }

    private List<ReceiptLineItem> extractReceiptLineItems(String rawText) {
        List<String> lines = rawText.lines()
                .map(StringValues::normalizeWhitespace)
                .filter(line -> !line.isBlank())
                .toList();
        List<ReceiptLineItem> items = new java.util.ArrayList<>();
        boolean itemTableStarted = false;
        for (String line : lines) {
            if (!itemTableStarted) {
                itemTableStarted = isReceiptItemHeader(line);
                continue;
            }
            if (isReceiptItemTableEnd(line)) {
                break;
            }
            Optional<ReceiptLineItem> item = parseReceiptItemLine(line);
            item.ifPresent(items::add);
        }
        return items;
    }

    private boolean isReceiptItemHeader(String line) {
        String normalized = normalizeForSearch(line).replaceAll("\\s+", "");
        return (normalized.contains("품명") || normalized.contains("상품"))
                && (normalized.contains("단가") || normalized.contains("수량"))
                && (normalized.contains("금액") || normalized.contains("total"));
    }

    private boolean isReceiptItemTableEnd(String line) {
        String normalized = normalizeForSearch(line);
        return containsAny(normalized, "합계", "총액", "결제", "받을금액", "거스름", "부가세", "과세", "면세", "total");
    }

    private Optional<ReceiptLineItem> parseReceiptItemLine(String line) {
        Matcher amountMatcher = Pattern.compile("[0-9,]+").matcher(line);
        int firstNumberStart = -1;
        String lastAmount = null;
        while (amountMatcher.find()) {
            if (firstNumberStart < 0) {
                firstNumberStart = amountMatcher.start();
            }
            lastAmount = amountMatcher.group();
        }
        if (firstNumberStart <= 0 || lastAmount == null) {
            return Optional.empty();
        }
        String name = StringValues.trimToEmpty(line.substring(0, firstNumberStart).replaceAll("[·*]", ""));
        if (name.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new ReceiptLineItem(name, NumberValues.parseWonAmount(lastAmount)));
    }

    private LocalDate extractDate(String rawText) {
        Matcher yearMatcher = YEAR_DATE_PATTERN.matcher(rawText);
        if (yearMatcher.find()) {
            int year = Integer.parseInt(yearMatcher.group(1));
            int month = Integer.parseInt(yearMatcher.group(2));
            int day = Integer.parseInt(yearMatcher.group(3));
            return LocalDate.of(year, month, day);
        }
        Matcher monthMatcher = MONTH_DATE_PATTERN.matcher(rawText);
        if (monthMatcher.find()) {
            int month = Integer.parseInt(monthMatcher.group(1));
            int day = Integer.parseInt(monthMatcher.group(2));
            return LocalDate.of(LocalDate.now().getYear(), month, day);
        }
        return LocalDate.now();
    }

    private TransactionType extractType(String rawText) {
        if (containsAny(rawText, "승인취소", "취소", "환불", "입금", "deposit")) {
            return TransactionType.INCOME;
        }
        return TransactionType.EXPENSE;
    }

    private String extractMerchant(String sourceText, String primaryText) {
        String receiptMerchant = extractReceiptMerchant(sourceText);
        if (!receiptMerchant.isBlank()) {
            return receiptMerchant;
        }

        String candidate = primaryText;
        candidate = candidate.replaceAll("\\[[^\\]]+]", " ");
        candidate = YEAR_DATE_PATTERN.matcher(candidate).replaceAll(" ");
        candidate = MONTH_DATE_PATTERN.matcher(candidate).replaceAll(" ");
        candidate = AMOUNT_PATTERN.matcher(candidate).replaceAll(" ");
        candidate = TIME_PATTERN.matcher(candidate).replaceAll(" ");
        candidate = CARD_SUFFIX_PATTERN.matcher(candidate).replaceAll(" ");
        candidate = candidate.replaceAll("승인번호\\s*\\S+", " ");
        candidate = candidate.replaceAll("[()\\[\\],]", " ");
        for (String word : StringValues.sortedByLengthDesc(ISSUER_WORDS)) {
            candidate = candidate.replaceAll("(?i)" + Pattern.quote(word), " ");
        }
        for (String word : StringValues.sortedByLengthDesc(NOISE_WORDS)) {
            candidate = candidate.replaceAll(Pattern.quote(word), " ");
        }
        candidate = StringValues.normalizeWhitespace(candidate);
        if (!candidate.isBlank()) {
            return candidate;
        }
        return "자동 입력";
    }

    private String extractReceiptMerchant(String rawText) {
        if (!isReceiptLike(rawText)) return "";
        return rawText.lines()
                .map(StringValues::normalizeWhitespace)
                .filter(line -> !line.isBlank())
                .filter(line -> !isReceiptNoiseLine(line))
                .findFirst()
                .orElse("");
    }

    private boolean isReceiptNoiseLine(String line) {
        String normalized = normalizeForSearch(line);
        if (YEAR_DATE_PATTERN.matcher(line).find() || MONTH_DATE_PATTERN.matcher(line).find()) return true;
        if (AMOUNT_PATTERN.matcher(line).find()) return true;
        return containsAny(normalized,
                "영수증", "매출전표", "사업자", "대표", "주소", "전화", "tel", "품명", "단가", "수량", "금액",
                "합계", "총액", "결제", "승인", "카드", "부가세", "과세", "면세", "거스름", "받을금액", "total"
        );
    }

    private boolean isReceiptLike(String rawText) {
        if (rawText == null || rawText.isBlank()) return false;
        long lineCount = rawText.lines().filter(line -> !line.isBlank()).count();
        return lineCount >= 3 || containsAny(rawText, "영수증", "합계", "총액", "사업자", "매출전표");
    }

    private String stripSupportingAmounts(String rawText) {
        return SUPPORTING_AMOUNT_PATTERN.matcher(rawText).replaceAll(" ");
    }

    private String normalize(String rawText) {
        return rawText == null ? "" : rawText.replaceAll("\\s+", " ").trim();
    }

    private String receiptMemo(List<ReceiptLineItem> receiptLineItems) {
        String baseMemo = "영수증 OCR 자동 입력 후보";
        if (receiptLineItems == null || receiptLineItems.isEmpty()) {
            return baseMemo;
        }
        String itemsSummary = receiptLineItems.stream()
                .limit(5)
                .map(item -> item.name() + " " + item.amount().stripTrailingZeros().toPlainString() + "원")
                .collect(Collectors.joining(", "));
        int remainingCount = receiptLineItems.size() - 5;
        if (remainingCount > 0) {
            itemsSummary += " 외 " + remainingCount + "건";
        }
        return baseMemo + "\n항목: " + itemsSummary;
    }

    private String importMemo(String rawText, TransactionType type, boolean receiptLike, List<ReceiptLineItem> receiptLineItems) {
        if (receiptLike) {
            return receiptMemo(receiptLineItems);
        }
        if (type == TransactionType.INCOME && containsAny(rawText, "취소", "환불")) {
            return "문자 자동 입력 후보 · 취소/환불";
        }
        if (type == TransactionType.INCOME) {
            return "문자 자동 입력 후보 · 입금";
        }
        if (containsAny(rawText, "체크", "출금")) {
            return "문자 자동 입력 후보 · 체크/출금";
        }
        return "문자 자동 입력 후보 · 카드 승인";
    }

    private boolean containsAny(String text, String... words) {
        return StringValues.containsAny(text, words);
    }

    private record CategoryRecommendation(Category category, String reason) {
        private static CategoryRecommendation empty() {
            return new CategoryRecommendation(null, null);
        }
    }

    private record ReceiptLineItem(String name, BigDecimal amount) {
    }
}
