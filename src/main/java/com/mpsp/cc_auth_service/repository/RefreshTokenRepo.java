package com.mpsp.cc_auth_service.repository;

import com.mpsp.cc_auth_service.entity.RefreshToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface RefreshTokenRepo extends JpaRepository<RefreshToken, Integer> {

  @Transactional(readOnly = true)
  Optional<RefreshToken> findByToken(final String token);

  @Query("delete from RefreshToken rt where rt.userId = ?1")
  @Transactional
  @Modifying
  void deleteRefreshToken(final Integer userId);
}
