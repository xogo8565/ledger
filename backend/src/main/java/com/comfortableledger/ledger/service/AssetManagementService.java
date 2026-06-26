package com.comfortableledger.ledger.service;

import com.comfortableledger.ledger.domain.Asset;
import com.comfortableledger.ledger.domain.AssetType;
import com.comfortableledger.ledger.domain.CardProfile;
import com.comfortableledger.ledger.domain.Category;
import com.comfortableledger.ledger.domain.CategoryType;
import com.comfortableledger.ledger.domain.Household;
import com.comfortableledger.ledger.domain.Member;
import com.comfortableledger.ledger.dto.ApiDtos.AssetDto;
import com.comfortableledger.ledger.dto.ApiDtos.AssetSummaryDto;
import com.comfortableledger.ledger.dto.ApiDtos.CategoryDto;
import com.comfortableledger.ledger.dto.ApiDtos.SaveAssetRequest;
import com.comfortableledger.ledger.dto.ApiDtos.SaveCardAssetRequest;
import com.comfortableledger.ledger.dto.ApiDtos.SaveCategoryRequest;
import com.comfortableledger.ledger.repository.AssetRepository;
import com.comfortableledger.ledger.repository.CardProfileRepository;
import com.comfortableledger.ledger.repository.CategoryRepository;
import com.comfortableledger.ledger.repository.HouseholdRepository;
import com.comfortableledger.ledger.repository.MemberRepository;
import com.comfortableledger.ledger.util.StringValues;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssetManagementService {
    private final HouseholdRepository householdRepository;
    private final MemberRepository memberRepository;
    private final AssetRepository assetRepository;
    private final CategoryRepository categoryRepository;
    private final CardProfileRepository cardProfileRepository;

    public AssetManagementService(HouseholdRepository householdRepository, MemberRepository memberRepository,
                                  AssetRepository assetRepository, CategoryRepository categoryRepository,
                                  CardProfileRepository cardProfileRepository) {
        this.householdRepository = householdRepository;
        this.memberRepository = memberRepository;
        this.assetRepository = assetRepository;
        this.categoryRepository = categoryRepository;
        this.cardProfileRepository = cardProfileRepository;
    }

    @Transactional(readOnly = true)
    public List<AssetDto> assets() {
        return assetRepository.findByHouseholdIdAndHiddenFalseOrderBySortOrderAscIdAsc(defaultHousehold().getId())
                .stream().map(AssetDto::from).toList();
    }

    @Transactional(readOnly = true)
    public AssetSummaryDto assetSummary() {
        return summarizeAssets(assetRepository
                .findByHouseholdIdAndHiddenFalseOrderBySortOrderAscIdAsc(defaultHousehold().getId()));
    }

    static AssetSummaryDto summarizeAssets(List<Asset> assets) {
        BigDecimal totalAssets = assets.stream()
                .filter(asset -> asset.getType() != AssetType.CARD && asset.getType() != AssetType.DEBT)
                .map(Asset::getBalance).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalLiabilities = assets.stream()
                .filter(asset -> asset.getType() == AssetType.CARD || asset.getType() == AssetType.DEBT)
                .map(Asset::getBalance).map(BigDecimal::abs).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, List<Asset>> byOwner = assets.stream().collect(Collectors.groupingBy(
                asset -> normalizedOwnerName(asset.getOwnerName()),
                java.util.LinkedHashMap::new,
                Collectors.toList()
        ));
        List<AssetSummaryDto.OwnerAssetSummary> owners = byOwner.entrySet().stream()
                .map(entry -> ownerSummary(entry.getKey(), entry.getValue()))
                .sorted(Comparator
                        .comparing((AssetSummaryDto.OwnerAssetSummary item) -> "명의 미지정".equals(item.ownerName()))
                        .thenComparing(AssetSummaryDto.OwnerAssetSummary::netWorth, Comparator.reverseOrder())
                        .thenComparing(AssetSummaryDto.OwnerAssetSummary::ownerName))
                .toList();
        return new AssetSummaryDto(
                totalAssets, totalLiabilities, totalAssets.subtract(totalLiabilities), owners);
    }

    @Transactional(readOnly = true)
    public List<CategoryDto> categories() {
        return categoryRepository.findByHouseholdIdAndActiveTrueOrderBySortOrderAscIdAsc(defaultHousehold().getId())
                .stream().map(CategoryDto::from).toList();
    }

    @Transactional
    public AssetDto createAsset(SaveAssetRequest request) {
        Household household = defaultHousehold();
        String ownerName = registeredOwnerName(household, request.ownerName());
        Asset asset = new Asset(
                household, request.type(), request.name(), request.balance(),
                normalizedAssetGroup(request.type(), request.groupName()));
        asset.update(asset.getType(), asset.getName(), asset.getBalance(), asset.getGroupName(), ownerName, request.memo());
        return AssetDto.from(assetRepository.save(asset));
    }

    @Transactional
    public AssetDto createCardAsset(SaveCardAssetRequest request) {
        Household household = defaultHousehold();
        String ownerName = registeredOwnerName(household, request.ownerName());
        Asset paymentAccount = assetRepository.findById(request.paymentAccountId()).orElseThrow();
        Asset cardAsset = new Asset(
                household, AssetType.CARD, request.name(), request.balance(),
                normalizedAssetGroup(AssetType.CARD, request.groupName()));
        cardAsset.update(cardAsset.getType(), cardAsset.getName(), cardAsset.getBalance(),
                cardAsset.getGroupName(), ownerName, request.memo());
        assetRepository.save(cardAsset);
        CardProfile cardProfile = new CardProfile(
                cardAsset, paymentAccount, request.statementClosingDay(), request.paymentDay(), request.autoPayment());
        cardProfileRepository.save(cardProfile);
        cardAsset.setCardProfile(cardProfile);
        return AssetDto.from(cardAsset);
    }

    @Transactional
    public AssetDto updateAsset(Long id, SaveAssetRequest request) {
        Asset asset = assetRepository.findById(id).orElseThrow();
        asset.update(
                request.type(), request.name(), request.balance(),
                normalizedAssetGroup(request.type(), request.groupName()),
                registeredOwnerName(asset.getHousehold(), request.ownerName()), request.memo());
        return AssetDto.from(asset);
    }

    @Transactional
    public AssetDto updateCardAsset(Long id, SaveCardAssetRequest request) {
        Asset asset = assetRepository.findById(id).orElseThrow();
        Asset paymentAccount = assetRepository.findById(request.paymentAccountId()).orElseThrow();
        asset.update(
                AssetType.CARD, request.name(), request.balance(),
                normalizedAssetGroup(AssetType.CARD, request.groupName()),
                registeredOwnerName(asset.getHousehold(), request.ownerName()), request.memo());
        CardProfile cardProfile = asset.getCardProfile();
        if (cardProfile == null) {
            cardProfile = new CardProfile(
                    asset, paymentAccount, request.statementClosingDay(), request.paymentDay(), request.autoPayment());
            cardProfileRepository.save(cardProfile);
            asset.setCardProfile(cardProfile);
        } else {
            cardProfile.update(
                    paymentAccount, request.statementClosingDay(), request.paymentDay(), request.autoPayment());
        }
        return AssetDto.from(asset);
    }

    @Transactional
    public void deleteAsset(Long id) {
        assetRepository.findById(id).orElseThrow().hide();
    }

    @Transactional
    public CategoryDto createCategory(SaveCategoryRequest request) {
        Household household = defaultHousehold();
        int sortOrder = categoryRepository.findByHouseholdIdAndTypeAndActiveTrueOrderBySortOrderAscIdAsc(
                household.getId(), request.type()).size();
        Category category = new Category(
                household, request.type(), request.name(),
                normalizedCategoryIcon(request.type(), request.icon()),
                normalizedCategoryColor(request.color()), sortOrder);
        return CategoryDto.from(categoryRepository.save(category));
    }

    @Transactional
    public CategoryDto updateCategory(Long id, SaveCategoryRequest request) {
        Category category = categoryRepository.findById(id).orElseThrow();
        category.update(
                request.name(), normalizedCategoryIcon(request.type(), request.icon()),
                normalizedCategoryColor(request.color()));
        return CategoryDto.from(category);
    }

    @Transactional
    public void deleteCategory(Long id) {
        categoryRepository.findById(id).orElseThrow().deactivate();
    }

    private static AssetSummaryDto.OwnerAssetSummary ownerSummary(String ownerName, List<Asset> assets) {
        BigDecimal ownerAssets = assets.stream()
                .filter(asset -> asset.getType() != AssetType.CARD && asset.getType() != AssetType.DEBT)
                .map(Asset::getBalance).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal ownerLiabilities = assets.stream()
                .filter(asset -> asset.getType() == AssetType.CARD || asset.getType() == AssetType.DEBT)
                .map(Asset::getBalance).map(BigDecimal::abs).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new AssetSummaryDto.OwnerAssetSummary(
                ownerName, ownerAssets, ownerLiabilities,
                ownerAssets.subtract(ownerLiabilities), assets.size());
    }

    private static String normalizedOwnerName(String ownerName) {
        return ownerName == null || ownerName.isBlank()
                ? "명의 미지정"
                : StringValues.normalizeWhitespace(ownerName);
    }

    private String registeredOwnerName(Household household, String ownerName) {
        String normalized = StringValues.normalizeWhitespace(ownerName);
        if (normalized.isBlank()) return null;
        return memberRepository.findByHouseholdId(household.getId()).stream()
                .filter(member -> member.getName().equalsIgnoreCase(normalized))
                .map(Member::getName)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Asset owner must be a registered member"));
    }

    private String normalizedAssetGroup(AssetType type, String groupName) {
        if (groupName != null && !groupName.isBlank()) return groupName;
        return switch (type) {
            case CASH -> "현금";
            case BANK -> "계좌";
            case CARD -> "카드";
            case OTHER -> "기타";
            case DEBT -> "부채";
        };
    }

    private String normalizedCategoryIcon(CategoryType type, String icon) {
        if (icon != null && !icon.isBlank()) return icon;
        return type == CategoryType.INCOME ? "＋" : "•";
    }

    private String normalizedCategoryColor(String color) {
        return color == null || color.isBlank() ? "#ff625c" : color;
    }

    private Household defaultHousehold() {
        return householdRepository.findFirstByOrderByIdAsc().orElseThrow();
    }
}
