package com.corebank.domain.service;

import com.corebank.domain.command.RegisterUserCommand;
import com.corebank.domain.entity.Account;
import com.corebank.domain.entity.Address;
import com.corebank.domain.entity.User;
import com.corebank.domain.repository.AccountRepository;
import com.corebank.domain.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Random;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserRegistrationService {

  private final UserRepository userRepository;
  private final AccountRepository accountRepository;

  @Transactional
  public Account register(RegisterUserCommand command) {
    if (userRepository.existsByCpf(command.cpf())) {
      throw new IllegalArgumentException("CPF already registered");
    }
    if (userRepository.existsByEmail(command.email())) {
      throw new IllegalArgumentException("Email already registered");
    }

    Address address = new Address();
    address.setStreet(command.street());
    address.setNumber(command.number());
    address.setComplement(command.complement());
    address.setNeighborhood(command.neighborhood());
    address.setCity(command.city());
    address.setState(command.state());
    address.setZipCode(command.zipCode());

    User user = new User();
    user.setFullName(command.fullName());
    user.setCpf(command.cpf());
    user.setEmail(command.email());
    user.setPhone(command.phone());
    user.setPasswordHash(command.password());
    user.setBirthDate(command.birthDate());
    user.setAddress(address);
    user.setCreatedAt(Instant.now());

    userRepository.save(user);

    Account account = new Account();
    account.setUser(user);
    account.setBalance(BigDecimal.ZERO);
    account.setAccountNumber(generateAccountNumber());
    account.setAgencyNumber("0001");
    account.setCreatedAt(Instant.now());

    accountRepository.save(account);

    return account;
  }

  private String generateAccountNumber() {
    return String.valueOf(100000 + new Random().nextInt(900000));
  }
}
