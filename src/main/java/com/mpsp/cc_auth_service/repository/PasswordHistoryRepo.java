package com.mpsp.cc_auth_service.repository;

import com.mpsp.cc_auth_service.entity.PasswordHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.sesv2.endpoints.internal.Value;

import java.util.Optional;

@Repository
public interface PasswordHistoryRepo extends JpaRepository<PasswordHistory, Integer> {

  Page<PasswordHistory> findAllByUserId(@Param("userId") final int userId, final Pageable pageable);

}
