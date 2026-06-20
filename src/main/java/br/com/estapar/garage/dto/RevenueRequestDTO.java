package br.com.estapar.garage.dto;

import java.time.LocalDate;

public record RevenueRequestDTO(
        LocalDate date,
        String sector
) {}
