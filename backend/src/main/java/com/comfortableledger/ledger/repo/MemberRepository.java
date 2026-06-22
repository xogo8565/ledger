package com.comfortableledger.ledger.repo;

import com.comfortableledger.ledger.domain.Member;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {
    List<Member> findByHouseholdId(Long householdId);
}
