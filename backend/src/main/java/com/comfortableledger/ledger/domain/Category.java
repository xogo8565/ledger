package com.comfortableledger.ledger.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "categories")
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @NotNull
    private Household household;

    @Enumerated(EnumType.STRING)
    @NotNull
    private CategoryType type;

    @NotBlank
    private String name;

    private String icon;
    private String color;
    private int sortOrder;
    private boolean active = true;

    protected Category() {
    }

    public Category(Household household, CategoryType type, String name, String icon, String color, int sortOrder) {
        this.household = household;
        this.type = type;
        this.name = name;
        this.icon = icon;
        this.color = color;
        this.sortOrder = sortOrder;
    }

    public Long getId() {
        return id;
    }

    public CategoryType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String getIcon() {
        return icon;
    }

    public String getColor() {
        return color;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public boolean isActive() {
        return active;
    }

    public void update(String name, String icon, String color) {
        this.name = name;
        this.icon = icon;
        this.color = color;
    }

    public void deactivate() {
        this.active = false;
    }
}
