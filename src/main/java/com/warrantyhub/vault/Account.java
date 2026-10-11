package com.warrantyhub.vault;

import jakarta.persistence.*;

@Entity
@Table(name = "accounts")
public class Account {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false, length = 100)
  public String name;

  @Column(nullable = false, unique = true, length = 254)
  public String email;

  @Column(nullable = false)
  public String passwordHash;

  @Column(nullable = false)
  public long tokenVersion;
}
