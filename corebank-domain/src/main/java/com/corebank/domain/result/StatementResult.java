package com.corebank.domain.result;

import java.util.List;

public record StatementResult(
    List<StatementItemResult> items, int page, int size, long totalElements, int totalPages) {}
