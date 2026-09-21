package com.corebank.domain.repository;

import com.corebank.domain.entity.RefreshToken;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

  Optional<RefreshToken> findByTokenHashAndRevokedAtIsNull(String tokenHash);
}
