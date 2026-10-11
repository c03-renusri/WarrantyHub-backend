package com.warrantyhub.vault;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceRequestRepository extends JpaRepository<ServiceRequest, Long> {
  List<ServiceRequest> findByProductIdOrderByIdDesc(Long productId);
}
