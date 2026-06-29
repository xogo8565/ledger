package com.comfortableledger.ledger.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

@Entity
@Table(name = "assets")
public class Asset {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @NotNull
    private Household household;

    @Enumerated(EnumType.STRING)
    @NotNull
    private AssetType type;

    @NotBlank
    private String name;

    @NotNull
    private BigDecimal balance;

    private String groupName;
    private String ownerName;
    private String memo;
    private int sortOrder;
    private boolean hidden;

    @OneToOne(mappedBy = "asset", cascade = CascadeType.ALL, orphanRemoval = true)
    private CardProfile cardProfile;

    @OneToOne(mappedBy = "asset", cascade = CascadeType.ALL, orphanRemoval = true)
    private DebtProfile debtProfile;

    protected Asset() {
    }

    public Asset(Household household, AssetType type, String name, BigDecimal balance, String groupName) {
        this.household = household;
        this.type = type;
        this.name = name;
        this.balance = balance;
        this.groupName = groupName;
    }

    public Long getId() {
        return id;
    }

    public Household getHousehold() {
        return household;
    }

    public AssetType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public String getGroupName() {
        return groupName;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public String getMemo() {
        return memo;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public boolean isHidden() {
        return hidden;
    }

    public CardProfile getCardProfile() {
        return cardProfile;
    }

    public void setCardProfile(CardProfile cardProfile) {
        this.cardProfile = cardProfile;
    }

    public DebtProfile getDebtProfile() {
        return debtProfile;
    }

    public void setDebtProfile(DebtProfile debtProfile) {
        this.debtProfile = debtProfile;
    }

    public void changeBalance(BigDecimal delta) {
        this.balance = this.balance.add(delta);
    }

    public void update(AssetType type, String name, BigDecimal balance, String groupName, String ownerName, String memo) {
        this.type = type;
        this.name = name;
        this.balance = balance;
        this.groupName = groupName;
        this.ownerName = ownerName;
        this.memo = memo;
    }

    public void hide() {
        this.hidden = true;
    }
}
