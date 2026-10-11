package com.warrantyhub.vault;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
  List<Product> findByOwnerIdOrderByPurchaseDateDesc(Long ownerId);

  Optional<Product> findByIdAndOwnerId(Long id, Long ownerId);
}
