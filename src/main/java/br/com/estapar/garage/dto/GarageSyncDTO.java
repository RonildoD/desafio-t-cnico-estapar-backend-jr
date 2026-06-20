package br.com.estapar.garage.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

public record GarageSyncDTO(List<GarageSectorDTO> garage, List<GarageSpotDTO> spots) {
    // Aceita tanto "base_price" (enviado pelo simulador real) quanto "basePrice" (exemplo do PDF).
    // Mesma robustez para max_capacity / maxCapacity.
    public record GarageSectorDTO(
            String sector,
            @JsonProperty("base_price") @JsonAlias("basePrice") BigDecimal basePrice,
            @JsonProperty("max_capacity") @JsonAlias("maxCapacity") Integer max_capacity) {}

    public record GarageSpotDTO(Long id, String sector, Double lat, Double lng) {}
}
