package br.com.estapar.garage.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "sectors")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Sector {
    @Id
    @Column(name = "id", length = 10)
    private String id; // Ex: "A"

    @Column(name = "base_price", nullable = false)
    @JsonProperty("base_price")
    private BigDecimal basePrice;

    @Column(name = "max_capacity", nullable = false)
    @JsonProperty("max_capacity")
    private Integer maxCapacity;
}
