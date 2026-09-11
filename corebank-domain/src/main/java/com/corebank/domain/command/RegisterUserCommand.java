package com.corebank.domain.command;

import java.time.LocalDate;

public record RegisterUserCommand(
    String fullName,
    String cpf,
    String email,
    String phone,
    String password,
    LocalDate birthDate,
    String street,
    String number,
    String complement,
    String neighborhood,
    String city,
    String state,
    String zipCode) {}
