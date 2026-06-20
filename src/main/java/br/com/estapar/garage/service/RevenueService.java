package br.com.estapar.garage.service;
import br.com.estapar.garage.dto.RevenueRequestDTO;
import br.com.estapar.garage.dto.RevenueResponseDTO;
import br.com.estapar.garage.repository.ParkingSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;


@Service
@RequiredArgsConstructor
public class RevenueService {
    private final ParkingSessionRepository sessionRepository;

    // Formato ISO-8601 em UTC com milissegundos, ex.: "2025-01-01T12:00:00.000Z"
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    public RevenueResponseDTO getDailyRevenue(RevenueRequestDTO request) {
        BigDecimal totalRevenue = sessionRepository.calculateTotalRevenueBySectorAndDate(
                request.sector(),
                request.date()
        );

        // Garante o formato monetário com 2 casas decimais (ex.: 0.00) exigido pelo PDF.
        BigDecimal amount = (totalRevenue != null ? totalRevenue : BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);

        String currentTimestamp = TIMESTAMP_FORMAT.format(Instant.now());

        return new RevenueResponseDTO(
                amount,
                "BRL",
                currentTimestamp
        );
    }
}
