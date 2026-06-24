package com.comfortableledger.ledger.service;

import com.comfortableledger.ledger.domain.Category;
import com.comfortableledger.ledger.domain.CategoryType;
import com.comfortableledger.ledger.domain.Household;
import com.comfortableledger.ledger.domain.TransactionRecord;
import com.comfortableledger.ledger.domain.TransactionType;
import com.comfortableledger.ledger.repo.CategoryRepository;
import com.comfortableledger.ledger.repo.HouseholdRepository;
import com.comfortableledger.ledger.repo.TransactionRepository;
import com.comfortableledger.ledger.web.ApiDtos.TextImportPreview;
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
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("([0-9,]+)\\s*원");
    private static final Pattern YEAR_DATE_PATTERN = Pattern.compile("(20\\d{2})[./-]\\s*(\\d{1,2})[./-]\\s*(\\d{1,2})");
    private static final Pattern MONTH_DATE_PATTERN = Pattern.compile("(\\d{1,2})[./월]\\s*(\\d{1,2})\\s*일?");
    private static final Pattern SUPPORTING_AMOUNT_PATTERN = Pattern.compile("(잔액|누적|합계|한도|사용가능|총액)\\s*[: ]*([0-9,]+)\\s*원?");
    private static final Pattern TIME_PATTERN = Pattern.compile("\\b\\d{1,2}[:시]\\d{2}\\b");
    private static final Pattern CARD_SUFFIX_PATTERN = Pattern.compile("\\b\\d{2,4}[-*]\\*{2,4}\\b|\\(\\d{3,4}\\)");
    private static final List<String> ISSUER_WORDS = List.of(
            "국민", "KB", "신한", "삼성", "현대", "롯데", "우리", "하나", "NH", "농협", "BC", "비씨",
            "카카오", "토스", "케이뱅크", "국민은행", "신한은행", "우리은행", "하나은행", "농협은행"
    );
    private static final List<String> NOISE_WORDS = List.of(
            "카드", "체크", "체크카드", "신용", "승인", "사용", "이용", "출금", "입금", "취소", "승인취소",
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
        EXPENSE_CATEGORY_KEYWORDS.put("생활용품", List.of("다이소", "가구", "생활용품", "문구", "세탁"));
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
        String normalized = normalize(rawText);
        String primaryText = stripSupportingAmounts(normalized);
        BigDecimal amount = extractAmount(primaryText);
        LocalDate date = extractDate(primaryText);
        TransactionType type = extractType(primaryText);
        String merchant = extractMerchant(primaryText);
        CategoryRecommendation recommendation = recommendCategory(type, merchant);
        return new TextImportPreview(
                rawText,
                type,
                date,
                amount,
                merchant,
                importMemo(primaryText, type),
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
        if (!merchantKey.isBlank() && !merchantKey.equals("자동입력")) {
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
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private BigDecimal extractAmount(String rawText) {
        Matcher matcher = AMOUNT_PATTERN.matcher(rawText);
        if (matcher.find()) {
            return new BigDecimal(matcher.group(1).replace(",", ""));
        }
        return BigDecimal.ZERO;
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
        if (containsAny(rawText, "승인취소", "취소", "환불", "입금")) {
            return TransactionType.INCOME;
        }
        return TransactionType.EXPENSE;
    }

    private String extractMerchant(String rawText) {
        String candidate = rawText;
        candidate = candidate.replaceAll("\\[[^\\]]+]", " ");
        candidate = YEAR_DATE_PATTERN.matcher(candidate).replaceAll(" ");
        candidate = MONTH_DATE_PATTERN.matcher(candidate).replaceAll(" ");
        candidate = AMOUNT_PATTERN.matcher(candidate).replaceAll(" ");
        candidate = TIME_PATTERN.matcher(candidate).replaceAll(" ");
        candidate = CARD_SUFFIX_PATTERN.matcher(candidate).replaceAll(" ");
        candidate = candidate.replaceAll("승인번호\\s*\\S+", " ");
        candidate = candidate.replaceAll("[()\\[\\],]", " ");
        for (String word : sortedByLengthDesc(ISSUER_WORDS)) {
            candidate = candidate.replaceAll("(?i)" + Pattern.quote(word), " ");
        }
        for (String word : sortedByLengthDesc(NOISE_WORDS)) {
            candidate = candidate.replaceAll(Pattern.quote(word), " ");
        }
        candidate = candidate.replaceAll("\\s+", " ").trim();
        if (!candidate.isBlank()) {
            return candidate;
        }
        return "자동 입력";
    }

    private String stripSupportingAmounts(String rawText) {
        return SUPPORTING_AMOUNT_PATTERN.matcher(rawText).replaceAll(" ");
    }

    private String normalize(String rawText) {
        return rawText == null ? "" : rawText.replaceAll("\\s+", " ").trim();
    }

    private String importMemo(String rawText, TransactionType type) {
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
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private List<String> sortedByLengthDesc(List<String> words) {
        return words.stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
    }

    private record CategoryRecommendation(Category category, String reason) {
        private static CategoryRecommendation empty() {
            return new CategoryRecommendation(null, null);
        }
    }
}
