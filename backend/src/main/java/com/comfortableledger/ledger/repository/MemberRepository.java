package com.comfortableledger.ledger.repository;

import com.comfortableledger.ledger.domain.Member;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {
    List<Member> findByHouseholdId(Long householdId);

    boolean existsByHouseholdIdAndNameIgnoreCase(Long householdId, String name);
}
