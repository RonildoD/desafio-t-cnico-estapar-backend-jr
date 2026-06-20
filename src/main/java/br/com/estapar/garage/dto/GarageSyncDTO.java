package br.com.estapar.garage.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

public record GarageSyncDTO(List<GarageSectorDTO> garage, List<GarageSpotDTO> spots) {
    public record GarageSectorDTO(String sector, @JsonProperty("base_price") BigDecimal basePrice, Integer max_capacity) {}
    public record GarageSpotDTO(Long id, String sector, Double lat, Double lng) {}

}
