package com.comfortableledger.ledger.service.transaction;

import com.comfortableledger.ledger.domain.Household;
import com.comfortableledger.ledger.domain.TransactionRecord;
import com.comfortableledger.ledger.repository.HouseholdRepository;
import com.comfortableledger.ledger.repository.TransactionRepository;
import com.comfortableledger.ledger.dto.TransactionDtos.TransactionDto;
import com.comfortableledger.ledger.dto.TransactionDtos.TransactionSearchResultDto;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionQueryService {
    private final HouseholdRepository householdRepository;
    private final TransactionRepository transactionRepository;

    public TransactionQueryService(HouseholdRepository householdRepository,
                                   TransactionRepository transactionRepository) {
        this.householdRepository = householdRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional(readOnly = true)
    public List<TransactionDto> transactions(String month) {
        YearMonth yearMonth = month == null || month.isBlank() ? YearMonth.now() : YearMonth.parse(month);
        return transactions(yearMonth.atDay(1), yearMonth.atEndOfMonth());
    }

    @Transactional(readOnly = true)
    public List<TransactionDto> transactionsBetween(LocalDate startDate, LocalDate endDate) {
        LocalDate start = startDate == null ? LocalDate.now().withDayOfMonth(1) : startDate;
        LocalDate end = endDate == null ? start : endDate;
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("End date cannot be before start date");
        }
        return transactions(start, end);
    }

    @Transactional(readOnly = true)
    public TransactionSearchResultDto searchTransactions(TransactionSearchCriteria criteria) {
        Long householdId = defaultHousehold().getId();
        Specification<TransactionRecord> specification = specification(householdId, criteria);
        Page<TransactionRecord> page = transactionRepository.findAll(
                specification,
                PageRequest.of(criteria.page(), criteria.size(), criteria.sort().toSpringSort())
        );
        List<TransactionDto> items = page.getContent().stream()
                .map(TransactionDto::from)
                .toList();
        return new TransactionSearchResultDto(
                items,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                criteria.sort().name()
        );
    }

    @Transactional(readOnly = true)
    public String exportTransactionsCsv(String month, Integer year) {
        List<TransactionRecord> records;
        String period;
        if (year != null) {
            records = records(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
            period = String.valueOf(year);
        } else {
            YearMonth yearMonth = month == null || month.isBlank() ? YearMonth.now() : YearMonth.parse(month);
            records = records(yearMonth.atDay(1), yearMonth.atEndOfMonth());
            period = yearMonth.toString();
        }

        StringBuilder csv = new StringBuilder();
        csv.append("기간,거래일,유형,금액,카테고리,소비태그,소비구분,소비명의,자산,출금자산,입금자산,제목,메모,할부회차,할부개월\n");
        records.stream()
                .sorted(Comparator.comparing(TransactionRecord::getTransactionDate).thenComparing(TransactionRecord::getId))
                .forEach(record -> csv.append(csvRow(
                        period,
                        record.getTransactionDate().toString(),
                        record.getType().name(),
                        record.getAmount().toPlainString(),
                        record.getCategory() == null ? "" : record.getCategory().getName(),
                        record.getSpendingTag(),
                        record.getConsumptionScope() == null ? "" : record.getConsumptionScope().name(),
                        record.getConsumer() == null ? "" : record.getConsumer().getName(),
                        record.getAsset() == null ? "" : record.getAsset().getName(),
                        record.getFromAsset() == null ? "" : record.getFromAsset().getName(),
                        record.getToAsset() == null ? "" : record.getToAsset().getName(),
                        record.getTitle(),
                        record.getMemo(),
                        record.getInstallmentIndex() == 0 ? "" : String.valueOf(record.getInstallmentIndex()),
                        record.getInstallmentMonths() == 0 ? "" : String.valueOf(record.getInstallmentMonths())
                )).append('\n'));
        return csv.toString();
    }

    @Transactional(readOnly = true)
    public TransactionDto getTransaction(Long id) {
        return TransactionDto.from(transactionRepository.findById(id).orElseThrow());
    }

    @Transactional(readOnly = true)
    public List<TransactionDto> dailyTransactions(String date) {
        LocalDate targetDate = date == null || date.isBlank() ? LocalDate.now() : LocalDate.parse(date);
        return transactionRepository.findByHouseholdIdAndTransactionDateOrderByTransactionDateDescIdDesc(
                        defaultHousehold().getId(), targetDate)
                .stream()
                .map(TransactionDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TransactionDto> installmentTransactions(String installmentGroupId) {
        return transactionRepository.findByInstallmentGroupIdOrderByTransactionDateAscIdAsc(installmentGroupId)
                .stream()
                .map(TransactionDto::from)
                .toList();
    }

    private Specification<TransactionRecord> specification(Long householdId, TransactionSearchCriteria criteria) {
        return (root, criteriaQuery, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("household").get("id"), householdId));
            if (criteria.startDate() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("transactionDate"), criteria.startDate()));
            }
            if (criteria.endDate() != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("transactionDate"), criteria.endDate()));
            }
            if (criteria.type() != null) predicates.add(builder.equal(root.get("type"), criteria.type()));
            if (criteria.categoryId() != null) {
                predicates.add(builder.equal(root.get("category").get("id"), criteria.categoryId()));
            }
            if (criteria.consumptionScope() != null) {
                predicates.add(builder.equal(root.get("consumptionScope"), criteria.consumptionScope()));
            }
            if (criteria.consumerMemberId() != null) {
                predicates.add(builder.equal(root.get("consumer").get("id"), criteria.consumerMemberId()));
            }
            if (criteria.minAmount() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("amount"), criteria.minAmount()));
            }
            if (criteria.maxAmount() != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("amount"), criteria.maxAmount()));
            }
            addAssetPredicate(root, builder, predicates, criteria.assetId());
            addKeywordPredicate(root, builder, predicates, criteria.query());
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private void addAssetPredicate(jakarta.persistence.criteria.Root<TransactionRecord> root,
                                   jakarta.persistence.criteria.CriteriaBuilder builder,
                                   List<Predicate> predicates, Long assetId) {
        if (assetId == null) return;
        predicates.add(builder.or(
                builder.equal(root.get("asset").get("id"), assetId),
                builder.equal(root.get("fromAsset").get("id"), assetId),
                builder.equal(root.get("toAsset").get("id"), assetId)
        ));
    }

    private void addKeywordPredicate(jakarta.persistence.criteria.Root<TransactionRecord> root,
                                     jakarta.persistence.criteria.CriteriaBuilder builder,
                                     List<Predicate> predicates, String query) {
        if (query.isBlank()) return;
        String pattern = "%" + query.toLowerCase() + "%";
        var category = root.join("category", JoinType.LEFT);
        var asset = root.join("asset", JoinType.LEFT);
        var fromAsset = root.join("fromAsset", JoinType.LEFT);
        var toAsset = root.join("toAsset", JoinType.LEFT);
        var consumer = root.join("consumer", JoinType.LEFT);
        predicates.add(builder.or(
                builder.like(builder.lower(root.get("title")), pattern),
                builder.like(builder.lower(root.get("memo")), pattern),
                builder.like(builder.lower(root.get("spendingTag")), pattern),
                builder.like(builder.lower(category.get("name")), pattern),
                builder.like(builder.lower(asset.get("name")), pattern),
                builder.like(builder.lower(fromAsset.get("name")), pattern),
                builder.like(builder.lower(toAsset.get("name")), pattern),
                builder.like(builder.lower(consumer.get("name")), pattern)
        ));
    }

    private List<TransactionDto> transactions(LocalDate start, LocalDate end) {
        return records(start, end).stream().map(TransactionDto::from).toList();
    }

    private List<TransactionRecord> records(LocalDate start, LocalDate end) {
        return transactionRepository.findByHouseholdIdAndTransactionDateBetweenOrderByTransactionDateDescIdDesc(
                defaultHousehold().getId(), start, end);
    }

    private String csvRow(String... values) {
        return java.util.Arrays.stream(values).map(this::csvCell).collect(Collectors.joining(","));
    }

    private String csvCell(String value) {
        String normalized = value == null ? "" : value;
        return "\"" + normalized.replace("\"", "\"\"") + "\"";
    }

    private Household defaultHousehold() {
        return householdRepository.findFirstByOrderByIdAsc().orElseThrow();
    }
}
