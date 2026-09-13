package com.corebank.gateway.dto.error;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {

  private String message;
  private String code;
  private List<ApiFieldError> fieldErrors;
}
