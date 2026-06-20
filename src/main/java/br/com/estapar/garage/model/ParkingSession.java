package br.com.estapar.garage.model;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "parking_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParkingSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "license_plate", nullable = false, length = 20)
    private String licensePlate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sector_id", nullable = false)
    private Sector sector;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spot_id")
    private Spot spot; // Preenchido no evento PARKED

    @Column(name = "entry_time", nullable = false)
    private LocalDateTime entryTime;

    @Column(name = "exit_time")
    private LocalDateTime exitTime; // Preenchido no evento EXIT

    @Column(name = "dynamic_hourly_rate", nullable = false)
    private BigDecimal dynamicHourlyRate; // Valor/hora calculado no momento da entrada

    @Column(name = "total_amount")
    private BigDecimal totalAmount; // Valor final cobrado (preenchido no EXIT)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionStatus status;
}
