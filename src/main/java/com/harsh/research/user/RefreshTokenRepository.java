package com.harsh.research.user;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RefreshToken t where t.tokenHash = :hash")
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("hash") String hash);

    @Transactional
    void deleteAllByExpiresAtBefore(java.time.Instant before);

    @Transactional
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(\"\"\"
            update RefreshToken t set t.revokedAt = :now
            where t.userId = :userId and t.revokedAt is null
            \"\"\")
    int revokeAllByUserId(
            @org.springframework.data.repository.query.Param(\"userId\") UUID userId,
            @org.springframework.data.repository.query.Param(\"now\") java.time.Instant now
    );
}
