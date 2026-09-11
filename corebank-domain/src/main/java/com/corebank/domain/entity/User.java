package com.corebank.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User {

  @Id @GeneratedValue private UUID id;

  @Column(name = "full_name", nullable = false)
  private String fullName;

  @Column(name = "cpf", nullable = false, unique = true)
  private String cpf;

  @Column(name = "email", nullable = false, unique = true)
  private String email;

  @Column(name = "phone")
  private String phone;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Column(name = "birth_date")
  private LocalDate birthDate;

  @Embedded private Address address;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
