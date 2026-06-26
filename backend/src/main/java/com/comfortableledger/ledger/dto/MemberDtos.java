package com.comfortableledger.ledger.dto;

import com.comfortableledger.ledger.domain.Member;
import com.comfortableledger.ledger.domain.MemberRole;
import jakarta.validation.constraints.NotBlank;

public final class MemberDtos {
    private MemberDtos() {
    }

    public record MemberDto(Long id, String name, MemberRole role, boolean deletable) {
        public static MemberDto from(Member member) {
            return new MemberDto(
                    member.getId(),
                    member.getName(),
                    member.getRole(),
                    member.getRole() != MemberRole.OWNER
            );
        }
    }

    public record SaveMemberRequest(@NotBlank String name) {
    }

    public record ConsumerMigrationDto(
            Long ownerMemberId,
            String ownerMemberName,
            long eligibleCount,
            long migratedCount
    ) {
    }
}
