package com.warrantyhub.vault;

import jakarta.persistence.*;

@Entity
@Table(name = "receipts")
public class Receipt {
  @Id public Long productId;

  @Column(nullable = false, length = 255)
  public String filename;

  @Column(nullable = false, length = 40)
  public String contentType;

  @Lob
  @Column(nullable = false, columnDefinition = "LONGBLOB")
  public byte[] content;
}
