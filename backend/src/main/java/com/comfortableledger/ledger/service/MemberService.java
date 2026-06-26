package com.comfortableledger.ledger.service;

import com.comfortableledger.ledger.domain.Asset;
import com.comfortableledger.ledger.domain.Household;
import com.comfortableledger.ledger.domain.Member;
import com.comfortableledger.ledger.domain.MemberRole;
import com.comfortableledger.ledger.domain.TransactionRecord;
import com.comfortableledger.ledger.repository.AssetRepository;
import com.comfortableledger.ledger.repository.HouseholdRepository;
import com.comfortableledger.ledger.repository.MemberRepository;
import com.comfortableledger.ledger.repository.TransactionRepository;
import com.comfortableledger.ledger.util.StringValues;
import com.comfortableledger.ledger.dto.ApiDtos.ConsumerMigrationDto;
import com.comfortableledger.ledger.dto.ApiDtos.MemberDto;
import com.comfortableledger.ledger.dto.ApiDtos.SaveMemberRequest;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberService {
    private final HouseholdRepository householdRepository;
    private final MemberRepository memberRepository;
    private final AssetRepository assetRepository;
    private final TransactionRepository transactionRepository;

    public MemberService(HouseholdRepository householdRepository, MemberRepository memberRepository,
                         AssetRepository assetRepository, TransactionRepository transactionRepository) {
        this.householdRepository = householdRepository;
        this.memberRepository = memberRepository;
        this.assetRepository = assetRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional(readOnly = true)
    public List<MemberDto> members() {
        return memberRepository.findByHouseholdId(defaultHousehold().getId()).stream()
                .sorted(Comparator
                        .comparing((Member member) -> member.getRole() != MemberRole.OWNER)
                        .thenComparing(Member::getName)
                        .thenComparing(Member::getId))
                .map(MemberDto::from)
                .toList();
    }

    @Transactional
    public MemberDto createMember(SaveMemberRequest request) {
        Household household = defaultHousehold();
        String name = normalizedMemberName(request.name());
        if (memberRepository.existsByHouseholdIdAndNameIgnoreCase(household.getId(), name)) {
            throw new IllegalArgumentException("Member name already exists");
        }
        return MemberDto.from(memberRepository.save(new Member(household, name, MemberRole.EDITOR)));
    }

    @Transactional
    public MemberDto updateMember(Long id, SaveMemberRequest request) {
        Member member = memberRepository.findById(id).orElseThrow();
        String name = normalizedMemberName(request.name());
        boolean duplicate = memberRepository.findByHouseholdId(member.getHousehold().getId()).stream()
                .anyMatch(item -> !item.getId().equals(id) && item.getName().equalsIgnoreCase(name));
        if (duplicate) {
            throw new IllegalArgumentException("Member name already exists");
        }
        String oldName = member.getName();
        member.rename(name);
        assetRepository.findAll().stream()
                .filter(asset -> asset.getHousehold().getId().equals(member.getHousehold().getId()))
                .filter(asset -> normalizedMemberNameOrEmpty(asset.getOwnerName()).equalsIgnoreCase(oldName))
                .forEach(asset -> updateOwnerName(asset, name));
        return MemberDto.from(member);
    }

    @Transactional
    public void deleteMember(Long id) {
        Member member = memberRepository.findById(id).orElseThrow();
        if (member.getRole() == MemberRole.OWNER) {
            throw new IllegalArgumentException("Owner member cannot be deleted");
        }
        boolean usedByAsset = assetRepository.findAll().stream()
                .filter(asset -> asset.getHousehold().getId().equals(member.getHousehold().getId()))
                .anyMatch(asset -> normalizedMemberNameOrEmpty(asset.getOwnerName()).equalsIgnoreCase(member.getName()));
        if (usedByAsset) {
            throw new IllegalArgumentException("Member is used by an asset");
        }
        if (transactionRepository.existsByConsumerId(member.getId())) {
            throw new IllegalArgumentException("Member is used by a personal expense");
        }
        memberRepository.delete(member);
    }

    @Transactional(readOnly = true)
    public ConsumerMigrationDto consumerMigrationStatus() {
        Household household = defaultHousehold();
        Member owner = ownerMember(household);
        long eligibleCount = transactionRepository.findUnassignedPersonalExpenses(household.getId()).size();
        return new ConsumerMigrationDto(owner.getId(), owner.getName(), eligibleCount, 0);
    }

    @Transactional
    public ConsumerMigrationDto migrateUnassignedPersonalExpenses() {
        Household household = defaultHousehold();
        Member owner = ownerMember(household);
        List<TransactionRecord> eligible = transactionRepository.findUnassignedPersonalExpenses(household.getId());
        long migratedCount = eligible.stream()
                .filter(record -> record.assignConsumerIfUnassignedPersonalExpense(owner))
                .count();
        return new ConsumerMigrationDto(owner.getId(), owner.getName(), 0, migratedCount);
    }

    private void updateOwnerName(Asset asset, String ownerName) {
        asset.update(
                asset.getType(),
                asset.getName(),
                asset.getBalance(),
                asset.getGroupName(),
                ownerName,
                asset.getMemo()
        );
    }

    private Member ownerMember(Household household) {
        return memberRepository.findByHouseholdId(household.getId()).stream()
                .filter(member -> member.getRole() == MemberRole.OWNER)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Owner member not found"));
    }

    private String normalizedMemberName(String name) {
        String normalized = normalizedMemberNameOrEmpty(name);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Member name is required");
        }
        return normalized;
    }

    private String normalizedMemberNameOrEmpty(String name) {
        return StringValues.normalizeWhitespace(name);
    }

    private Household defaultHousehold() {
        return householdRepository.findFirstByOrderByIdAsc().orElseThrow();
    }
}
