package com.verdict.castcourt.domain.account.repository;

import com.verdict.castcourt.domain.account.entity.AccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<AccountEntity, Integer> {
    Optional<AccountEntity> findByUsername(String username);
    boolean existsByUsername(String username);
}
