package br.com.estapar.garage.service;
import br.com.estapar.garage.dto.RevenueRequestDTO;
import br.com.estapar.garage.dto.RevenueResponseDTO;
import br.com.estapar.garage.repository.ParkingSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;


@Service
@RequiredArgsConstructor
public class RevenueService {
    private final ParkingSessionRepository sessionRepository;

    public RevenueResponseDTO getDailyRevenue(RevenueRequestDTO request) {
        BigDecimal totalRevenue = sessionRepository.calculateTotalRevenueBySectorAndDate(
                request.sector(),
                request.date()
        );

        // Formata o timestamp no padrão ISO 8601 (ex: "2025-01-01T12:00:00.000Z")
        String currentTimestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());

        return new RevenueResponseDTO(
                totalRevenue,
                "BRL",
                currentTimestamp
        );
    }
}
