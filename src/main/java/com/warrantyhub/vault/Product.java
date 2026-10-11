package com.warrantyhub.vault;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "products")
public class Product {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false)
  public Long ownerId;

  @Column(nullable = false, length = 120)
  public String productName;

  @Column(nullable = false, length = 100)
  public String brand;

  @Column(nullable = false, length = 80)
  public String category;

  @Column(nullable = false, length = 120)
  public String store;

  @Column(nullable = false, precision = 14, scale = 2)
  public BigDecimal price;

  @Column(nullable = false)
  public LocalDate purchaseDate;

  @Column(nullable = false)
  public Integer warrantyMonths;
}
