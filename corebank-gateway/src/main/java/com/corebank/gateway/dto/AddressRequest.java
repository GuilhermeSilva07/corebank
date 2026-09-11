package com.corebank.gateway.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AddressRequest {

  @NotBlank private String street;

  @NotBlank private String number;

  @NotBlank private String neighborhood;

  @NotBlank private String city;

  @NotBlank private String state;

  @NotBlank private String zipCode;

  private String complement;
}
