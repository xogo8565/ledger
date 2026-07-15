package com.comfortableledger.ledger.service.importing;

import com.comfortableledger.ledger.domain.Category;
import com.comfortableledger.ledger.domain.CategoryType;
import com.comfortableledger.ledger.domain.Household;
import com.comfortableledger.ledger.domain.TransactionRecord;
import com.comfortableledger.ledger.domain.TransactionType;
import com.comfortableledger.ledger.dto.ImportDtos.TextImportItem;
import com.comfortableledger.ledger.dto.ImportDtos.TextImportPreview;
import com.comfortableledger.ledger.repository.CategoryRepository;
import com.comfortableledger.ledger.repository.HouseholdRepository;
import com.comfortableledger.ledger.repository.TransactionRepository;
import com.comfortableledger.ledger.util.NumberValues;
import com.comfortableledger.ledger.util.StringValues;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
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
    private static final Pattern SIGNED_WON_PATTERN = Pattern.compile("([+-]?)\\s*([0-9][0-9,]*)\\s*원");
    private static final Pattern RECEIPT_TOTAL_AMOUNT_PATTERN = Pattern.compile(
            "(?:합계|총액|총 결제 금액|결제금액|받을금액|매출금액|total)\\s*[: ]*([0-9,]+)\\s*(?:원|won)?",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern YEAR_DATE_PATTERN = Pattern.compile("(20\\d{2})[./-]\\s*(\\d{1,2})[./-]\\s*(\\d{1,2})");
    private static final Pattern MONTH_DATE_PATTERN = Pattern.compile("(\\d{1,2})[./월\\s]+(\\d{1,2})\\s*(?:일)?");
    private static final Pattern DAILY_HEADER_PATTERN = Pattern.compile("^(?:\\[\\s*)?(\\d{1,2})(?:월|[./-])\\s*(\\d{1,2})(?:일)?(?:\\s*\\])?(?:\\s+\\S+요일)?\\s*$");
    private static final Pattern SUPPORTING_AMOUNT_PATTERN = Pattern.compile("(?:잔액|누적|한도|사용가능|총액)\\s*[: ]*([0-9,]+)\\s*원");
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
        EXPENSE_CATEGORY_KEYWORDS.put("편의점", List.of(
                "cu", "씨유", "gs25", "지에스25", "세븐일레븐", "이마트24", "emart24", "미니스톱", "편의점"
        ));
        EXPENSE_CATEGORY_KEYWORDS.put("레오", List.of(
                "동물병원", "몰리스", "몰리스펫샵", "펫샵", "펫마트", "펫프렌즈", "강아지", "반려동물", "애견", "사료", "동물약국", "수의", "수의사", "펫"
        ));
        EXPENSE_CATEGORY_KEYWORDS.put("식비", List.of(
                "스타벅스", "투썸", "이디야", "메가커피", "빽다방", "컴포즈", "할리스", "커피빈", "공차",
                "카페", "커피", "식당", "음식점", "한식", "중식", "일식", "분식", "김밥", "국밥", "마라탕",
                "치킨", "피자", "버거", "배달", "푸드", "맥도날드", "버거킹", "롯데리아", "맘스터치", "써브웨이",
                "bbq", "bhc", "교촌", "스시", "샐러드", "베이커리", "파리바게뜨", "뚜레쥬르", "던킨", "배스킨",
                "배달의민족", "배민", "요기요", "쿠팡이츠", "휴게소",
                "이마트", "원플러스", "롯데마트", "홈플러스", "코스트코", "트레이더스", "하나로마트", "농협하나로",
                "노브랜드", "롯데슈퍼", "gs더프레시", "마켓컬리", "컬리", "ssg", "마트"
        ));
        EXPENSE_CATEGORY_KEYWORDS.put("교통/차량", List.of(
                "택시", "카카오t", "카카오택시", "우티", "타다", "티머니", "후불교통", "코레일", "철도", "ktx",
                "srt", "버스", "지하철", "주유", "주유소", "충전소", "전기차충전", "sk에너지", "gs칼텍스",
                "s-oil", "에쓰오일", "현대오일뱅크", "하이패스", "톨게이트", "주차", "주차장", "쏘카", "그린카"
        ));
        EXPENSE_CATEGORY_KEYWORDS.put("문화생활", List.of(
                "영화", "극장", "cgv", "롯데시네마", "메가박스", "서점", "교보문고", "영풍문고", "알라딘",
                "예스24", "공연", "티켓", "인터파크", "게임", "스팀", "플레이스테이션", "닌텐도", "넷플릭스",
                "유튜브", "디즈니", "왓챠", "티빙", "웨이브", "멜론", "지니뮤직", "벅스", "백화점"
        ));
        EXPENSE_CATEGORY_KEYWORDS.put("패션/미용", List.of(
                "미용실", "헤어", "네일", "의류", "패션", "무신사", "지그재그", "에이블리", "브랜디", "w컨셉",
                "29cm", "올리브영", "화장품", "뷰티", "클리닉", "피부", "왁싱"
        ));
        EXPENSE_CATEGORY_KEYWORDS.put("생활용품", List.of(
                "다이소", "쿠팡", "네이버페이", "오늘의집", "생활용품", "문구", "문구점", "가구", "이케아",
                "모던하우스", "아트박스", "무인양품", "알파문구", "오피스디포"
        ));
        EXPENSE_CATEGORY_KEYWORDS.put("주거/통신", List.of(
                "통신", "인터넷", "휴대폰", "핸드폰", "skt", "kt", "lg유플러스", "u+", "전기", "한국전력",
                "가스", "삼천리", "수도", "관리비", "월세", "도시가스", "보험료", "렌탈", "정수기", "코웨이", "oracle"
        ));
        EXPENSE_CATEGORY_KEYWORDS.put("건강", List.of(
                "병원", "의원", "약국", "치과", "한의원", "안과", "피부과", "내과", "정형외과", "이비인후과",
                "소아과", "건강", "검진", "의료", "처방"
        ));
        INCOME_CATEGORY_KEYWORDS.put("급여", List.of("급여", "월급", "상여", "보너스", "성과급", "연말정산", "환급급여"));
        INCOME_CATEGORY_KEYWORDS.put("이자", List.of("이자", "예금이자", "입출금통장 이자", "결산이자", "적금이자", "배당"));
        INCOME_CATEGORY_KEYWORDS.put("부수입", List.of("부수입", "정산", "수익", "판매", "중고거래", "당근", "캐시백", "포인트", "환불", "취소"));
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
        List<TextImportItem> importedItems = parseLedgerLines(sourceText);
        if (!importedItems.isEmpty()) {
            TextImportItem first = importedItems.getFirst();
            return new TextImportPreview(
                    sourceText,
                    first.type(),
                    first.transactionDate(),
                    first.amount(),
                    first.merchant(),
                    "다건 거래 자동 입력 후보 · " + importedItems.size() + "건",
                    first.recommendedCategoryId(),
                    first.recommendedCategoryName(),
                    first.categoryRecommendationReason(),
                    importedItems
            );
        }

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

    private List<TextImportItem> parseLedgerLines(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return List.of();
        }
        List<TextImportItem> items = new ArrayList<>();
        LocalDate currentDate = null;
        for (String rawLine : rawText.lines().toList()) {
            String line = StringValues.normalizeWhitespace(rawLine);
            if (line.isBlank()) {
                continue;
            }
            Optional<LocalDate> headerDate = parseDailyHeader(line);
            if (headerDate.isPresent()) {
                currentDate = headerDate.get();
                continue;
            }
            parseLedgerLine(line, currentDate).ifPresent(items::add);
        }
        return items.size() <= 1 ? List.of() : items;
    }

    private Optional<LocalDate> parseDailyHeader(String line) {
        Matcher matcher = DAILY_HEADER_PATTERN.matcher(line);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        return Optional.of(LocalDate.of(
                LocalDate.now().getYear(),
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2))
        ));
    }

    private Optional<TextImportItem> parseLedgerLine(String line, LocalDate currentDate) {
        Matcher amountMatcher = SIGNED_WON_PATTERN.matcher(line);
        if (!amountMatcher.find()) {
            return Optional.empty();
        }
        BigDecimal amount = NumberValues.parseWonAmount(amountMatcher.group(2)).abs();
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }
        String sign = amountMatcher.group(1);
        String remainder = StringValues.normalizeWhitespace(line.substring(amountMatcher.end()));
        if (remainder.startsWith("|") || remainder.startsWith("/")) {
            remainder = StringValues.normalizeWhitespace(remainder.substring(1));
        }

        String[] columns = splitColumns(remainder);
        String merchant = columns.length == 0 || columns[0].isBlank() ? extractMerchant(line, line) : columns[0];
        String assetName = columns.length >= 2 ? columns[1] : "";
        String extraMemo = columns.length >= 3
                ? java.util.Arrays.stream(columns).skip(2).collect(Collectors.joining(" | "))
                : "";
        TransactionType type = ledgerLineType(line, sign, merchant);
        LocalDate date = currentDate == null ? extractDate(line) : currentDate;
        CategoryRecommendation recommendation = recommendCategory(type, merchant);
        String memo = ledgerLineMemo(line, assetName, extraMemo, itemsTransferHint(merchant));
        return Optional.of(new TextImportItem(
                line,
                type,
                date,
                amount,
                merchant,
                assetName,
                memo,
                recommendation.category() == null ? null : recommendation.category().getId(),
                recommendation.category() == null ? null : recommendation.category().getName(),
                recommendation.reason()
        ));
    }

    private String[] splitColumns(String text) {
        String delimiterPattern = text.contains("|") ? "\\s*\\|\\s*" : "\\s+/\\s+";
        return Pattern.compile(delimiterPattern)
                .splitAsStream(text)
                .map(StringValues::normalizeWhitespace)
                .filter(value -> !value.isBlank())
                .toArray(String[]::new);
    }

    private TransactionType ledgerLineType(String line, String sign, String merchant) {
        if (containsAny(line.toLowerCase(Locale.ROOT), "취소", "환불", "승인취소")) {
            return TransactionType.INCOME;
        }
        if ("+".equals(sign)) {
            return TransactionType.INCOME;
        }
        if (itemsTransferHint(merchant)) {
            return TransactionType.TRANSFER;
        }
        return TransactionType.EXPENSE;
    }

    private boolean itemsTransferHint(String merchant) {
        String normalized = StringValues.normalizeSearchKey(merchant);
        return normalized.contains("→")
                && containsAny(normalized, "내 ", "계좌", "카카오페이", "카카오페이 머니", "nh농협", "하나", "카카오뱅크")
                && !containsAny(normalized, "보험료", "카드출금", "신한카드", "삼성카드", "kb카드");
    }

    private String ledgerLineMemo(String line, String assetName, String extraMemo, boolean transferHint) {
        List<String> parts = new ArrayList<>();
        parts.add(transferHint ? "다건 거래 자동 입력 후보 · 이체" : "다건 거래 자동 입력 후보");
        if (!assetName.isBlank()) {
            parts.add("자산: " + assetName);
        }
        if (!extraMemo.isBlank()) {
            parts.add(extraMemo);
        }
        if (containsAny(line, "취소", "환불")) {
            parts.add("취소/환불");
        }
        return String.join(" · ", parts);
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
        amountSearchText = TIME_PATTERN.matcher(amountSearchText).replaceAll(" ");
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
        List<ReceiptLineItem> items = new ArrayList<>();
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
        return baseMemo + "\n품목: " + itemsSummary;
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
