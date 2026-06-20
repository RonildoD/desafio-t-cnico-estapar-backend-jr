package br.com.estapar.garage.dto;

import java.math.BigDecimal;

public record RevenueResponseDTO(
        BigDecimal amount,
        String currency,
        String timestamp
) {}
