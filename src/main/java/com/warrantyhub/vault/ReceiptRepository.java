package com.warrantyhub.vault;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReceiptRepository extends JpaRepository<Receipt, Long> {
  @org.springframework.data.jpa.repository.Query(
      "select r.productId from Receipt r where r.productId in :ids")
  java.util.Set<Long> receiptIds(
      @org.springframework.data.repository.query.Param("ids") java.util.Collection<Long> ids);
}
