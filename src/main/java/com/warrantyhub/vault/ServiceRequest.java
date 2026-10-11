package com.warrantyhub.vault;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "service_requests")
public class ServiceRequest {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false)
  public Long productId;

  @Column(nullable = false, length = 2000)
  public String issueDescription;

  @Column(nullable = false, length = 20)
  public String status;

  @Column(nullable = false)
  public LocalDate requestDate;
}
