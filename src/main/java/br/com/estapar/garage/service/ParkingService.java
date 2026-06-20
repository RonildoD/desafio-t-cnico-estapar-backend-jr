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
        switch (event.eventType()) {
            case "ENTRY" -> handleEntry(event);
            case "PARKED" -> handleParked(event);
            case "EXIT" -> handleExit(event);
            default -> throw new IllegalArgumentException("Evento desconhecido: " + event.eventType());
        }
    }

    private void handleEntry(WebhookEventDTO event) {
        Sector sector = sectorRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new RuntimeException("Nenhum setor configurado."));

        long occupiedSpots = spotRepository.countBySectorIdAndStatus(sector.getId(), SpotStatus.OCCUPIED);

        if (occupiedSpots >= sector.getMaxCapacity()) {
            throw new RuntimeException("Estacionamento cheio!");
        }

        // Reserva uma vaga fisicamente na entrada
        Spot spot = spotRepository.findFirstBySectorIdAndStatus(sector.getId(), SpotStatus.AVAILABLE)
                .orElseThrow(() -> new RuntimeException("Estacionamento cheio fisicamente!"));
        spot.setStatus(SpotStatus.OCCUPIED);
        spotRepository.save(spot);

        double occupationPercentage = (double) occupiedSpots / sector.getMaxCapacity();
        BigDecimal dynamicRate = calculateDynamicRate(sector.getBasePrice(), occupationPercentage);

        ParkingSession session = ParkingSession.builder()
                .licensePlate(event.licensePlate())
                .sector(sector)
                .spot(spot) // Vaga já reservada
                .entryTime(event.entryTime())
                .dynamicHourlyRate(dynamicRate)
                .status(SessionStatus.ACTIVE)
                .build();

        sessionRepository.save(session);
    }

    private void handleParked(WebhookEventDTO event) {
        ParkingSession session = sessionRepository.findByLicensePlateAndStatus(event.licensePlate(), SessionStatus.ACTIVE)
                .orElseThrow(() -> new RuntimeException("Veículo não encontrado na entrada."));

        Spot actualSpot = spotRepository.findByLatAndLng(event.lat(), event.lng())
                .orElseThrow(() -> new RuntimeException("Vaga não encontrada com as coordenadas informadas."));

        // Se a vaga em que o carro parou for diferente da sugerida/reservada na entrada
        Spot assignedSpot = session.getSpot();
        if (assignedSpot != null && !assignedSpot.getId().equals(actualSpot.getId())) {
            assignedSpot.setStatus(SpotStatus.AVAILABLE);
            spotRepository.save(assignedSpot);
        }

        actualSpot.setStatus(SpotStatus.OCCUPIED);
        spotRepository.save(actualSpot);

        session.setSpot(actualSpot);
        session.setSector(actualSpot.getSector());
        sessionRepository.save(session);
    }

    private void handleExit(WebhookEventDTO event) {
        ParkingSession session = sessionRepository.findByLicensePlateAndStatus(event.licensePlate(), SessionStatus.ACTIVE)
                .orElseThrow(() -> new RuntimeException("Veículo não encontrado."));

        session.setExitTime(event.exitTime());
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
