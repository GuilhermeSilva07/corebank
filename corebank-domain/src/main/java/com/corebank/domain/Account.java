package com.corebank.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Account {

  @Id @GeneratedValue private UUID id;

  @Column(name = "account_number", nullable = false, unique = true)
  private String accountNumber;

  @Column(name = "agency_number", nullable = false)
  private String agencyNumber;

  @Column(name = "balance", nullable = false)
  private BigDecimal balance;

  @ManyToOne
  @JoinColumn(name = "user_id", nullable = false, unique = true)
  private User user;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
