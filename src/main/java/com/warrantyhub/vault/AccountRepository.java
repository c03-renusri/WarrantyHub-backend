package com.warrantyhub.vault;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select a from Account a where a.id = :id")
  java.util.Optional<Account> lockById(
      @org.springframework.data.repository.query.Param("id") Long id);

  Optional<Account> findByEmail(String email);
}
