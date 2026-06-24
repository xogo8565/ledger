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
@Table(name = "members")
public class Member {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @NotNull
    private Household household;

    @NotBlank
    private String name;

    @Enumerated(EnumType.STRING)
    @NotNull
    private MemberRole role;

    protected Member() {
    }

    public Member(Household household, String name, MemberRole role) {
        this.household = household;
        this.name = name;
        this.role = role;
    }

    public Long getId() {
        return id;
    }

    public Household getHousehold() {
        return household;
    }

    public String getName() {
        return name;
    }

    public MemberRole getRole() {
        return role;
    }

    public void rename(String name) {
        this.name = name;
    }
}
