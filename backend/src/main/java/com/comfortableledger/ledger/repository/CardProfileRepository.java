package com.comfortableledger.ledger.repository;

import com.comfortableledger.ledger.domain.CardProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CardProfileRepository extends JpaRepository<CardProfile, Long> {
}
