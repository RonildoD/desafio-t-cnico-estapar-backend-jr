package br.com.estapar.garage.service;
import br.com.estapar.garage.dto.WebhookEventDTO;
import br.com.estapar.garage.model.*;
import br.com.estapar.garage.repository.ParkingSessionRepository;
import br.com.estapar.garage.repository.SectorRepository;
import br.com.estapar.garage.repository.SpotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class ParkingService {
    private final SectorRepository sectorRepository;
    private final SpotRepository spotRepository;
    private final ParkingSessionRepository sessionRepository;

    @Transactional
    public void processWebhook(WebhookEventDTO event) {
        switch (event.event_type()) {
            case "ENTRY" -> handleEntry(event);
            case "PARKED" -> handleParked(event);
            case "EXIT" -> handleExit(event);
            default -> throw new IllegalArgumentException("Evento desconhecido: " + event.event_type());
        }
    }

    private void handleEntry(WebhookEventDTO event) {
        // Como o evento ENTRY não manda o setor, e o teste diz que "setores são lógicos",
        // pegamos o primeiro setor configurado (ou iteramos para achar um com vaga).
        Sector sector = sectorRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new RuntimeException("Nenhum setor configurado."));

        long occupiedSpots = spotRepository.countBySectorIdAndStatus(sector.getId(), SpotStatus.OCCUPIED);

        if (occupiedSpots >= sector.getMaxCapacity()) {
            throw new RuntimeException("Estacionamento cheio!");
        }

        double occupationPercentage = (double) occupiedSpots / sector.getMaxCapacity();
        BigDecimal dynamicRate = calculateDynamicRate(sector.getBasePrice(), occupationPercentage);

        ParkingSession session = ParkingSession.builder()
                .licensePlate(event.license_plate())
                .sector(sector)
                .entryTime(event.entry_time())
                .dynamicHourlyRate(dynamicRate)
                .status(SessionStatus.ACTIVE)
                .build();

        sessionRepository.save(session);
    }

    private void handleParked(WebhookEventDTO event) {
        ParkingSession session = sessionRepository.findByLicensePlateAndStatus(event.license_plate(), SessionStatus.ACTIVE)
                .orElseThrow(() -> new RuntimeException("Veículo não encontrado na entrada."));

        Spot spot = spotRepository.findByLatAndLng(event.lat(), event.lng())
                .orElseThrow(() -> new RuntimeException("Vaga não encontrada com as coordenadas informadas."));

        spot.setStatus(SpotStatus.OCCUPIED);
        spotRepository.save(spot);

        session.setSpot(spot);
        // Atualiza o setor para o setor real da vaga física, caso seja diferente
        session.setSector(spot.getSector());
        sessionRepository.save(session);
    }

    private void handleExit(WebhookEventDTO event) {
        ParkingSession session = sessionRepository.findByLicensePlateAndStatus(event.license_plate(), SessionStatus.ACTIVE)
                .orElseThrow(() -> new RuntimeException("Veículo não encontrado."));

        session.setExitTime(event.exit_time());
        session.setStatus(SessionStatus.FINISHED);

        // Libera a vaga
        Spot spot = session.getSpot();
        if (spot != null) {
            spot.setStatus(SpotStatus.AVAILABLE);
            spotRepository.save(spot);
        }

        // Calcula tempo e valor
        long minutes = Duration.between(session.getEntryTime(), session.getExitTime()).toMinutes();
        BigDecimal amountToCharge = BigDecimal.ZERO;

        if (minutes > 30) {
            long hoursToCharge = (long) Math.ceil(minutes / 60.0);
            amountToCharge = session.getDynamicHourlyRate().multiply(BigDecimal.valueOf(hoursToCharge));
        }

        session.setTotalAmount(amountToCharge);
        sessionRepository.save(session);
    }

    private BigDecimal calculateDynamicRate(BigDecimal basePrice, double occupationPercentage) {
        if (occupationPercentage < 0.25) {
            return basePrice.multiply(BigDecimal.valueOf(0.90)); // -10%
        } else if (occupationPercentage < 0.50) {
            return basePrice; // 0%
        } else if (occupationPercentage < 0.75) {
            return basePrice.multiply(BigDecimal.valueOf(1.10)); // +10%
        } else {
            return basePrice.multiply(BigDecimal.valueOf(1.25)); // +25%
        }
    }
}
