package com.comfortableledger.ledger.service.transaction;

import com.comfortableledger.ledger.domain.Asset;
import com.comfortableledger.ledger.domain.AssetType;
import com.comfortableledger.ledger.domain.Category;
import com.comfortableledger.ledger.domain.ConsumptionScope;
import com.comfortableledger.ledger.domain.Household;
import com.comfortableledger.ledger.domain.Member;
import com.comfortableledger.ledger.domain.MemberRole;
import com.comfortableledger.ledger.domain.ReceiptAttachment;
import com.comfortableledger.ledger.domain.TransactionRecord;
import com.comfortableledger.ledger.domain.TransactionType;
import com.comfortableledger.ledger.repository.AssetRepository;
import com.comfortableledger.ledger.repository.CategoryRepository;
import com.comfortableledger.ledger.repository.HouseholdRepository;
import com.comfortableledger.ledger.repository.MemberRepository;
import com.comfortableledger.ledger.repository.ReceiptAttachmentRepository;
import com.comfortableledger.ledger.repository.TransactionRepository;
import com.comfortableledger.ledger.dto.TransactionDtos.CreateTransactionRequest;
import com.comfortableledger.ledger.dto.TransactionDtos.TransactionDto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.comfortableledger.ledger.service.receipt.ReceiptFileStorage;

@Service
public class TransactionCommandService {
    private final HouseholdRepository householdRepository;
    private final MemberRepository memberRepository;
    private final AssetRepository assetRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final ReceiptAttachmentRepository receiptAttachmentRepository;
    private final ReceiptFileStorage receiptFileStorage;

    public TransactionCommandService(HouseholdRepository householdRepository, MemberRepository memberRepository,
                                     AssetRepository assetRepository, CategoryRepository categoryRepository,
                                     TransactionRepository transactionRepository,
                                     ReceiptAttachmentRepository receiptAttachmentRepository,
                                     ReceiptFileStorage receiptFileStorage) {
        this.householdRepository = householdRepository;
        this.memberRepository = memberRepository;
        this.assetRepository = assetRepository;
        this.categoryRepository = categoryRepository;
        this.transactionRepository = transactionRepository;
        this.receiptAttachmentRepository = receiptAttachmentRepository;
        this.receiptFileStorage = receiptFileStorage;
    }

    @Transactional
    public TransactionDto createTransaction(CreateTransactionRequest request) {
        int installmentMonths = request.installmentMonths() == null ? 0 : request.installmentMonths();
        if (request.type() == TransactionType.EXPENSE && installmentMonths > 1) {
            return createInstallmentTransactions(request, installmentMonths);
        }
        return TransactionDto.from(createSingleTransaction(
                request, request.transactionDate(), request.amount(), 0, 0, null));
    }

    @Transactional
    public List<TransactionDto> updateInstallmentTransactions(
            String installmentGroupId,
            CreateTransactionRequest request
    ) {
        List<TransactionRecord> records =
                transactionRepository.findByInstallmentGroupIdOrderByTransactionDateAscIdAsc(installmentGroupId);
        if (records.isEmpty()) {
            throw new IllegalArgumentException("Installment group not found");
        }
        if (request.type() != TransactionType.EXPENSE) {
            throw new IllegalArgumentException("Installment group must be an expense");
        }
        int installmentMonths = request.installmentMonths() == null ? records.size() : request.installmentMonths();
        if (installmentMonths <= 1) {
            throw new IllegalArgumentException("Installment month count must be greater than 1");
        }
        BigDecimal baseAmount = installmentAmount(request.amount(), installmentMonths);
        BigDecimal remainingAmount = request.amount();

        records.forEach(this::reverseAssetChange);
        for (int index = 1; index <= installmentMonths; index++) {
            BigDecimal amount = index == installmentMonths ? remainingAmount : baseAmount;
            remainingAmount = remainingAmount.subtract(amount);
            LocalDate transactionDate = request.transactionDate().plusMonths(index - 1L);
            if (index <= records.size()) {
                updateExistingInstallmentTransaction(
                        records.get(index - 1), request, transactionDate, amount,
                        index, installmentMonths, installmentGroupId);
            } else {
                createSingleTransaction(
                        request, transactionDate, amount, index, installmentMonths, installmentGroupId);
            }
        }
        removeExtraInstallments(records, installmentMonths);
        return transactionRepository.findByInstallmentGroupIdOrderByTransactionDateAscIdAsc(installmentGroupId)
                .stream()
                .map(TransactionDto::from)
                .toList();
    }

    @Transactional
    public void deleteInstallmentTransactions(String installmentGroupId) {
        List<TransactionRecord> records =
                transactionRepository.findByInstallmentGroupIdOrderByTransactionDateAscIdAsc(installmentGroupId);
        if (records.isEmpty()) {
            throw new IllegalArgumentException("Installment group not found");
        }
        records.forEach(this::reverseAssetChange);
        deleteReceiptsForTransactions(records);
        transactionRepository.deleteAll(records);
    }

    @Transactional
    public TransactionDto updateTransaction(Long id, CreateTransactionRequest request) {
        TransactionRecord record = transactionRepository.findById(id).orElseThrow();
        reverseAssetChange(record);
        record.update(
                request.type(),
                request.transactionDate(),
                request.amount(),
                category(request.categoryId()),
                asset(request.assetId()),
                asset(request.fromAssetId()),
                asset(request.toAssetId()),
                request.title(),
                request.memo(),
                request.spendingTag(),
                request.consumptionScope(),
                personalExpenseConsumer(record.getHousehold(), request),
                request.installmentMonths() == null ? 0 : request.installmentMonths()
        );
        applyAssetChange(record);
        return TransactionDto.from(transactionRepository.save(record));
    }

    @Transactional
    public void deleteTransaction(Long id) {
        TransactionRecord record = transactionRepository.findById(id).orElseThrow();
        reverseAssetChange(record);
        deleteReceiptsForTransactions(List.of(record));
        transactionRepository.deleteById(id);
    }

    private void deleteReceiptsForTransactions(List<TransactionRecord> records) {
        List<Long> transactionIds = records.stream()
                .map(TransactionRecord::getId)
                .filter(id -> id != null)
                .toList();
        if (transactionIds.isEmpty()) return;

        List<ReceiptAttachment> attachments = receiptAttachmentRepository.findByTransactionIdIn(transactionIds);
        if (attachments.isEmpty()) return;

        receiptAttachmentRepository.deleteAll(attachments);
        receiptFileStorage.deleteAfterCommit(attachments.stream()
                .map(ReceiptAttachment::getStoredPath)
                .toList());
    }

    private TransactionDto createInstallmentTransactions(CreateTransactionRequest request, int installmentMonths) {
        String groupId = UUID.randomUUID().toString();
        BigDecimal baseAmount = installmentAmount(request.amount(), installmentMonths);
        BigDecimal remainingAmount = request.amount();
        TransactionRecord firstRecord = null;
        for (int index = 1; index <= installmentMonths; index++) {
            BigDecimal amount = index == installmentMonths ? remainingAmount : baseAmount;
            remainingAmount = remainingAmount.subtract(amount);
            TransactionRecord record = createSingleTransaction(
                    request,
                    request.transactionDate().plusMonths(index - 1L),
                    amount,
                    index,
                    installmentMonths,
                    groupId
            );
            if (firstRecord == null) firstRecord = record;
        }
        return TransactionDto.from(firstRecord);
    }

    private BigDecimal installmentAmount(BigDecimal totalAmount, int installmentMonths) {
        return totalAmount.divide(BigDecimal.valueOf(installmentMonths), 2, RoundingMode.DOWN);
    }

    private TransactionRecord createSingleTransaction(
            CreateTransactionRequest request,
            LocalDate transactionDate,
            BigDecimal amount,
            int installmentIndex,
            int installmentMonths,
            String installmentGroupId
    ) {
        Household household = defaultHousehold();
        Member author = memberRepository.findByHouseholdId(household.getId()).stream().findFirst().orElseThrow();
        TransactionRecord record = new TransactionRecord(
                household,
                author,
                request.type(),
                transactionDate,
                amount,
                category(request.categoryId()),
                asset(request.assetId()),
                asset(request.fromAssetId()),
                asset(request.toAssetId()),
                request.title(),
                request.memo(),
                request.spendingTag(),
                request.consumptionScope(),
                personalExpenseConsumer(household, request),
                installmentMonths
        );
        if (installmentGroupId != null) {
            record.assignInstallment(installmentGroupId, installmentIndex, installmentMonths);
        }
        applyAssetChange(record);
        return transactionRepository.save(record);
    }

    private void updateExistingInstallmentTransaction(
            TransactionRecord record,
            CreateTransactionRequest request,
            LocalDate transactionDate,
            BigDecimal amount,
            int installmentIndex,
            int installmentMonths,
            String installmentGroupId
    ) {
        record.update(
                request.type(),
                transactionDate,
                amount,
                category(request.categoryId()),
                asset(request.assetId()),
                asset(request.fromAssetId()),
                asset(request.toAssetId()),
                request.title(),
                request.memo(),
                request.spendingTag(),
                request.consumptionScope(),
                personalExpenseConsumer(record.getHousehold(), request),
                installmentMonths
        );
        record.assignInstallment(installmentGroupId, installmentIndex, installmentMonths);
        applyAssetChange(record);
    }

    private void removeExtraInstallments(List<TransactionRecord> records, int installmentMonths) {
        if (records.size() <= installmentMonths) return;
        TransactionRecord fallbackRecord = records.get(installmentMonths - 1);
        List<TransactionRecord> removedRecords = records.subList(installmentMonths, records.size());
        for (TransactionRecord removedRecord : removedRecords) {
            for (ReceiptAttachment attachment : receiptAttachmentRepository.findByTransactionId(removedRecord.getId())) {
                attachment.reassignTo(fallbackRecord);
            }
        }
        transactionRepository.deleteAll(removedRecords);
    }

    private Member personalExpenseConsumer(Household household, CreateTransactionRequest request) {
        if (request.type() != TransactionType.EXPENSE || request.consumptionScope() == ConsumptionScope.SHARED) {
            return null;
        }
        if (request.consumerMemberId() != null) {
            Member member = memberRepository.findById(request.consumerMemberId()).orElseThrow();
            if (!member.getHousehold().getId().equals(household.getId())) {
                throw new IllegalArgumentException("Consumer member must belong to the household");
            }
            return member;
        }
        return memberRepository.findByHouseholdId(household.getId()).stream()
                .min(Comparator.comparing(member -> member.getRole() == MemberRole.OWNER ? 0 : 1))
                .orElseThrow();
    }

    private Category category(Long id) {
        return id == null ? null : categoryRepository.findById(id).orElseThrow();
    }

    private Asset asset(Long id) {
        return id == null ? null : assetRepository.findById(id).orElseThrow();
    }

    private void applyAssetChange(TransactionRecord record) {
        if (record.getType() == TransactionType.INCOME && record.getAsset() != null) {
            record.getAsset().changeBalance(record.getAmount());
        }
        if (record.getType() == TransactionType.EXPENSE && record.getAsset() != null
                && record.getAsset().getType() != AssetType.CARD) {
            record.getAsset().changeBalance(record.getAmount().negate());
        }
        if (record.getType() == TransactionType.TRANSFER) {
            if (record.getFromAsset() != null) record.getFromAsset().changeBalance(record.getAmount().negate());
            if (record.getToAsset() != null) record.getToAsset().changeBalance(record.getAmount());
        }
    }

    private void reverseAssetChange(TransactionRecord record) {
        if (record.getType() == TransactionType.INCOME && record.getAsset() != null) {
            record.getAsset().changeBalance(record.getAmount().negate());
        }
        if (record.getType() == TransactionType.EXPENSE && record.getAsset() != null
                && record.getAsset().getType() != AssetType.CARD) {
            record.getAsset().changeBalance(record.getAmount());
        }
        if (record.getType() == TransactionType.TRANSFER) {
            if (record.getFromAsset() != null) record.getFromAsset().changeBalance(record.getAmount());
            if (record.getToAsset() != null) record.getToAsset().changeBalance(record.getAmount().negate());
        }
    }

    private Household defaultHousehold() {
        return householdRepository.findFirstByOrderByIdAsc().orElseThrow();
    }
}
