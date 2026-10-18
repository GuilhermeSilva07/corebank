package com.corebank.gateway.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StatementResponse {
  private List<StatementItemResponse> items;
  private int page;
  private int size;
  private long totalElements;
  private int totalPages;
}
