package br.com.estapar.garage.service;

import br.com.estapar.garage.dto.WebhookEventDTO;
import br.com.estapar.garage.exception.GarageException;
import br.com.estapar.garage.model.*;
import br.com.estapar.garage.repository.ParkingSessionRepository;
import br.com.estapar.garage.repository.SpotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class ParkingService {
    private final SpotRepository spotRepository;
    private final ParkingSessionRepository sessionRepository;

    @Transactional
    public void processWebhook(WebhookEventDTO event) {
        if (event.eventType() == null) {
            throw GarageException.badRequest("event_type é obrigatório.");
        }
        switch (event.eventType()) {
            case "ENTRY" -> handleEntry(event);
            case "PARKED" -> handleParked(event);
            case "EXIT" -> handleExit(event);
            default -> throw GarageException.badRequest("Evento desconhecido: " + event.eventType());
        }
    }

    /**
     * ENTRY: o evento traz apenas placa e horário (não traz setor nem vaga).
     * O setor só é conhecido no PARKED. Aqui validamos a capacidade global da
     * garagem (se toda a garagem estiver cheia, não há onde estacionar) e
     * registramos a sessão como ativa.
     */
    private void handleEntry(WebhookEventDTO event) {
        if (event.licensePlate() == null || event.entryTime() == null) {
            throw GarageException.badRequest("license_plate e entry_time são obrigatórios no ENTRY.");
        }

        sessionRepository.findByLicensePlateAndStatus(event.licensePlate(), SessionStatus.ACTIVE)
                .ifPresent(s -> {
                    throw GarageException.conflict("Veículo " + event.licensePlate() + " já está na garagem.");
                });

        long totalSpots = spotRepository.count();
        long occupiedSpots = spotRepository.countByStatus(SpotStatus.OCCUPIED);
        if (occupiedSpots >= totalSpots) {
            // Garagem totalmente lotada: não permite novas entradas até liberar uma vaga.
            throw GarageException.conflict("Garagem cheia. Nenhuma vaga disponível.");
        }

        ParkingSession session = ParkingSession.builder()
                .licensePlate(event.licensePlate())
                .entryTime(event.entryTime())
                .status(SessionStatus.ACTIVE)
                .build();

        sessionRepository.save(session);
        log.info("ENTRY registrada para placa {}", event.licensePlate());
    }

    /**
     * PARKED: agora conhecemos a vaga (via lat/lng) e, por consequência, o setor.
     * É o momento de aplicar a regra de lotação (fechar setor a 100%) e calcular
     * o preço dinâmico com base na lotação daquele setor.
     */
    private void handleParked(WebhookEventDTO event) {
        if (event.lat() == null || event.lng() == null) {
            throw GarageException.badRequest("lat e lng são obrigatórios no PARKED.");
        }

        ParkingSession session = sessionRepository
                .findByLicensePlateAndStatus(event.licensePlate(), SessionStatus.ACTIVE)
                .orElseThrow(() -> GarageException.notFound(
                        "Veículo " + event.licensePlate() + " não possui entrada ativa."));

        Spot spot = spotRepository.findByLatAndLng(event.lat(), event.lng())
                .orElseThrow(() -> GarageException.notFound(
                        "Vaga não encontrada para as coordenadas informadas."));

        Sector sector = spot.getSector();

        long occupied = spotRepository.countBySectorIdAndStatus(sector.getId(), SpotStatus.OCCUPIED);
        // Regra de lotação: com 100% de lotação, o setor está fechado.
        if (occupied >= sector.getMaxCapacity()) {
            throw GarageException.conflict("Setor " + sector.getId() + " está lotado (100%).");
        }

        if (spot.getStatus() == SpotStatus.OCCUPIED) {
            throw GarageException.conflict("A vaga informada já está ocupada.");
        }

        // Lotação no momento da entrada/parada para definir o preço dinâmico.
        double occupationRate = (double) occupied / sector.getMaxCapacity();
        BigDecimal dynamicRate = calculateDynamicRate(sector.getBasePrice(), occupationRate);

        spot.setStatus(SpotStatus.OCCUPIED);
        spotRepository.save(spot);

        session.setSpot(spot);
        session.setSector(sector);
        session.setDynamicHourlyRate(dynamicRate);
        sessionRepository.save(session);
        log.info("PARKED placa {} no setor {} (vaga {}), tarifa/hora {}",
                event.licensePlate(), sector.getId(), spot.getId(), dynamicRate);
    }

    /**
     * EXIT: libera a vaga e calcula o valor a cobrar.
     */
    private void handleExit(WebhookEventDTO event) {
        if (event.exitTime() == null) {
            throw GarageException.badRequest("exit_time é obrigatório no EXIT.");
        }

        ParkingSession session = sessionRepository
                .findByLicensePlateAndStatus(event.licensePlate(), SessionStatus.ACTIVE)
                .orElseThrow(() -> GarageException.notFound(
                        "Veículo " + event.licensePlate() + " não está na garagem."));

        session.setExitTime(event.exitTime());
        session.setStatus(SessionStatus.FINISHED);

        Spot spot = session.getSpot();
        if (spot != null) {
            spot.setStatus(SpotStatus.AVAILABLE);
            spotRepository.save(spot);
        }

        session.setTotalAmount(calculateCharge(session));
        sessionRepository.save(session);
        log.info("EXIT placa {}, valor cobrado {}", event.licensePlate(), session.getTotalAmount());
    }

    /**
     * Primeiros 30 minutos são grátis. Após 30 minutos, cobra tarifa fixa por
     * hora (inclusive a primeira), arredondando as horas para cima.
     * A tarifa/hora é a dinâmica calculada no momento do PARKED; se o veículo
     * sair sem ter estacionado (sem PARKED), não há tarifa definida → cobra-se
     * a partir do basePrice não está disponível, então não há cobrança.
     */
    private BigDecimal calculateCharge(ParkingSession session) {
        // Usa segundos para não truncar a borda dos 30 min (ex.: 30min30s não é "30 min").
        long seconds = Duration.between(session.getEntryTime(), session.getExitTime()).getSeconds();
        if (seconds <= 30 * 60) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal hourlyRate = session.getDynamicHourlyRate();
        if (hourlyRate == null) {
            // Veículo saiu sem evento PARKED (sem setor/tarifa definidos).
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        long hoursToCharge = (long) Math.ceil(seconds / 3600.0);
        return hourlyRate.multiply(BigDecimal.valueOf(hoursToCharge))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Preço dinâmico conforme a lotação do setor no momento da entrada (texto do PDF):
     *  lotação menor que 25%  → desconto de 10%
     *  lotação até 50%        → 0%
     *  lotação até 75%        → +10%
     *  lotação até 100%       → +25%
     * A faixa 1 é estrita (< 25%); as demais usam o limite superior inclusivo ("até").
     */
    private BigDecimal calculateDynamicRate(BigDecimal basePrice, double occupationRate) {
        BigDecimal factor;
        if (occupationRate < 0.25) {
            factor = BigDecimal.valueOf(0.90);
        } else if (occupationRate <= 0.50) {
            factor = BigDecimal.ONE;
        } else if (occupationRate <= 0.75) {
            factor = BigDecimal.valueOf(1.10);
        } else {
            factor = BigDecimal.valueOf(1.25);
        }
        return basePrice.multiply(factor).setScale(2, RoundingMode.HALF_UP);
    }
}
