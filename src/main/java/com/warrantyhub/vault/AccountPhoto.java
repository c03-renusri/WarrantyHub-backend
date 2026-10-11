package com.warrantyhub.vault;

import jakarta.persistence.*;

@Entity
@Table(name = "account_photos")
public class AccountPhoto {
  @Id public Long accountId;

  @Lob
  @Column(nullable = false, columnDefinition = "MEDIUMBLOB")
  public byte[] content;
}
