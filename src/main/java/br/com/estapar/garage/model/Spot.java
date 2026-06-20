package br.com.estapar.garage.model;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "spots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Spot {
    @Id
    private Long id; // Mantendo o ID numérico que vem da API do simulador

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sector_id", nullable = false)
    private Sector sector;

    @Column(nullable = false)
    private Double lat;

    @Column(nullable = false)
    private Double lng;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SpotStatus status;
}
