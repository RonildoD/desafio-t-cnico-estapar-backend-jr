package br.com.estapar.garage.service;

import br.com.estapar.garage.dto.WebhookEventDTO;
import br.com.estapar.garage.exception.GarageException;
import br.com.estapar.garage.model.*;
import br.com.estapar.garage.repository.ParkingSessionRepository;
import br.com.estapar.garage.repository.SpotRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ParkingServiceTest {

    @Mock
    private SpotRepository spotRepository;
    @Mock
    private ParkingSessionRepository sessionRepository;
    @InjectMocks
    private ParkingService parkingService;

    private Sector sectorA(BigDecimal basePrice, int capacity) {
        return Sector.builder().id("A").basePrice(basePrice).maxCapacity(capacity).build();
    }

    private Spot spot(long id, Sector sector, double lat, double lng) {
        return Spot.builder().id(id).sector(sector).lat(lat).lng(lng).status(SpotStatus.AVAILABLE).build();
    }

    // ---------- ENTRY ----------

    @Test
    void entry_createsActiveSession() {
        when(sessionRepository.findByLicensePlateAndStatus("ZUL0001", SessionStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(spotRepository.count()).thenReturn(10L);
        when(spotRepository.countByStatus(SpotStatus.OCCUPIED)).thenReturn(3L);

        parkingService.processWebhook(new WebhookEventDTO(
                "ZUL0001", Instant.parse("2025-01-01T12:00:00Z"), null, null, null, "ENTRY"));

        ArgumentCaptor<ParkingSession> captor = ArgumentCaptor.forClass(ParkingSession.class);
        verify(sessionRepository).save(captor.capture());
        assertEquals(SessionStatus.ACTIVE, captor.getValue().getStatus());
        assertEquals("ZUL0001", captor.getValue().getLicensePlate());
    }

    @Test
    void entry_rejectsWhenGarageFull() {
        when(sessionRepository.findByLicensePlateAndStatus(anyString(), any())).thenReturn(Optional.empty());
        when(spotRepository.count()).thenReturn(10L);
        when(spotRepository.countByStatus(SpotStatus.OCCUPIED)).thenReturn(10L);

        GarageException ex = assertThrows(GarageException.class, () -> parkingService.processWebhook(
                new WebhookEventDTO("ZUL0001", Instant.now(), null, null, null, "ENTRY")));
        assertTrue(ex.getMessage().contains("cheia"));
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void entry_rejectsDuplicateActiveVehicle() {
        when(sessionRepository.findByLicensePlateAndStatus("ZUL0001", SessionStatus.ACTIVE))
                .thenReturn(Optional.of(new ParkingSession()));

        assertThrows(GarageException.class, () -> parkingService.processWebhook(
                new WebhookEventDTO("ZUL0001", Instant.now(), null, null, null, "ENTRY")));
    }

    // ---------- PARKED / Preço dinâmico ----------

    @Test
    void parked_appliesDiscountWhenOccupationBelow25Percent() {
        Sector sector = sectorA(new BigDecimal("10.00"), 100);
        Spot spot = spot(1, sector, -23.5, -46.6);
        ParkingSession session = ParkingSession.builder()
                .licensePlate("ZUL0001").status(SessionStatus.ACTIVE)
                .entryTime(Instant.parse("2025-01-01T12:00:00Z")).build();

        when(sessionRepository.findByLicensePlateAndStatus("ZUL0001", SessionStatus.ACTIVE))
                .thenReturn(Optional.of(session));
        when(spotRepository.findByLatAndLng(-23.5, -46.6)).thenReturn(Optional.of(spot));
        when(spotRepository.countBySectorIdAndStatus("A", SpotStatus.OCCUPIED)).thenReturn(10L); // 10%

        parkingService.processWebhook(new WebhookEventDTO(
                "ZUL0001", null, null, -23.5, -46.6, "PARKED"));

        // 10.00 * 0.90 = 9.00
        assertEquals(new BigDecimal("9.00"), session.getDynamicHourlyRate());
        assertEquals(SpotStatus.OCCUPIED, spot.getStatus());
    }

    @Test
    void parked_appliesSurchargeWhenOccupationAbove75Percent() {
        Sector sector = sectorA(new BigDecimal("10.00"), 100);
        Spot spot = spot(1, sector, -23.5, -46.6);
        ParkingSession session = ParkingSession.builder()
                .licensePlate("ZUL0001").status(SessionStatus.ACTIVE)
                .entryTime(Instant.parse("2025-01-01T12:00:00Z")).build();

        when(sessionRepository.findByLicensePlateAndStatus("ZUL0001", SessionStatus.ACTIVE))
                .thenReturn(Optional.of(session));
        when(spotRepository.findByLatAndLng(-23.5, -46.6)).thenReturn(Optional.of(spot));
        when(spotRepository.countBySectorIdAndStatus("A", SpotStatus.OCCUPIED)).thenReturn(80L); // 80%

        parkingService.processWebhook(new WebhookEventDTO(
                "ZUL0001", null, null, -23.5, -46.6, "PARKED"));

        // 10.00 * 1.25 = 12.50
        assertEquals(new BigDecimal("12.50"), session.getDynamicHourlyRate());
    }

    @Test
    void parked_exactly50PercentIsZeroSurcharge() {
        // "lotação até 50% → 0%" : 50% exato deve ficar na faixa de 0% (fator 1.00)
        Sector sector = sectorA(new BigDecimal("10.00"), 100);
        Spot spot = spot(1, sector, -23.5, -46.6);
        ParkingSession session = ParkingSession.builder()
                .licensePlate("ZUL0001").status(SessionStatus.ACTIVE)
                .entryTime(Instant.parse("2025-01-01T12:00:00Z")).build();

        when(sessionRepository.findByLicensePlateAndStatus("ZUL0001", SessionStatus.ACTIVE))
                .thenReturn(Optional.of(session));
        when(spotRepository.findByLatAndLng(-23.5, -46.6)).thenReturn(Optional.of(spot));
        when(spotRepository.countBySectorIdAndStatus("A", SpotStatus.OCCUPIED)).thenReturn(50L); // 50%

        parkingService.processWebhook(new WebhookEventDTO(
                "ZUL0001", null, null, -23.5, -46.6, "PARKED"));

        assertEquals(new BigDecimal("10.00"), session.getDynamicHourlyRate());
    }

    @Test
    void parked_exactly75PercentIsTenPercentSurcharge() {
        // "lotação até 75% → +10%" : 75% exato deve ficar na faixa de +10% (fator 1.10)
        Sector sector = sectorA(new BigDecimal("10.00"), 100);
        Spot spot = spot(1, sector, -23.5, -46.6);
        ParkingSession session = ParkingSession.builder()
                .licensePlate("ZUL0001").status(SessionStatus.ACTIVE)
                .entryTime(Instant.parse("2025-01-01T12:00:00Z")).build();

        when(sessionRepository.findByLicensePlateAndStatus("ZUL0001", SessionStatus.ACTIVE))
                .thenReturn(Optional.of(session));
        when(spotRepository.findByLatAndLng(-23.5, -46.6)).thenReturn(Optional.of(spot));
        when(spotRepository.countBySectorIdAndStatus("A", SpotStatus.OCCUPIED)).thenReturn(75L); // 75%

        parkingService.processWebhook(new WebhookEventDTO(
                "ZUL0001", null, null, -23.5, -46.6, "PARKED"));

        assertEquals(new BigDecimal("11.00"), session.getDynamicHourlyRate());
    }

    @Test
    void parked_rejectsWhenSectorFull() {
        Sector sector = sectorA(new BigDecimal("10.00"), 10);
        Spot spot = spot(1, sector, -23.5, -46.6);
        ParkingSession session = ParkingSession.builder()
                .licensePlate("ZUL0001").status(SessionStatus.ACTIVE)
                .entryTime(Instant.now()).build();

        when(sessionRepository.findByLicensePlateAndStatus("ZUL0001", SessionStatus.ACTIVE))
                .thenReturn(Optional.of(session));
        when(spotRepository.findByLatAndLng(-23.5, -46.6)).thenReturn(Optional.of(spot));
        when(spotRepository.countBySectorIdAndStatus("A", SpotStatus.OCCUPIED)).thenReturn(10L); // 100%

        GarageException ex = assertThrows(GarageException.class, () -> parkingService.processWebhook(
                new WebhookEventDTO("ZUL0001", null, null, -23.5, -46.6, "PARKED")));
        assertTrue(ex.getMessage().contains("lotado"));
    }

    // ---------- EXIT / Cobrança ----------

    @Test
    void exit_freeWhenUnder30Minutes() {
        Sector sector = sectorA(new BigDecimal("10.00"), 100);
        Spot spot = spot(1, sector, -23.5, -46.6);
        spot.setStatus(SpotStatus.OCCUPIED);
        ParkingSession session = ParkingSession.builder()
                .licensePlate("ZUL0001").status(SessionStatus.ACTIVE).spot(spot).sector(sector)
                .dynamicHourlyRate(new BigDecimal("10.00"))
                .entryTime(Instant.parse("2025-01-01T12:00:00Z")).build();

        when(sessionRepository.findByLicensePlateAndStatus("ZUL0001", SessionStatus.ACTIVE))
                .thenReturn(Optional.of(session));

        parkingService.processWebhook(new WebhookEventDTO(
                "ZUL0001", null, Instant.parse("2025-01-01T12:25:00Z"), null, null, "EXIT"));

        assertEquals(0, new BigDecimal("0.00").compareTo(session.getTotalAmount()));
        assertEquals(SpotStatus.AVAILABLE, spot.getStatus());
        assertEquals(SessionStatus.FINISHED, session.getStatus());
    }

    @Test
    void exit_chargesWhenJustOver30Minutes() {
        // 30min30s NÃO é "30 minutos" — deve cobrar (1 hora). Trava o bug de truncamento.
        Sector sector = sectorA(new BigDecimal("10.00"), 100);
        Spot spot = spot(1, sector, -23.5, -46.6);
        spot.setStatus(SpotStatus.OCCUPIED);
        ParkingSession session = ParkingSession.builder()
                .licensePlate("ZUL0001").status(SessionStatus.ACTIVE).spot(spot).sector(sector)
                .dynamicHourlyRate(new BigDecimal("10.00"))
                .entryTime(Instant.parse("2025-01-01T12:00:00Z")).build();

        when(sessionRepository.findByLicensePlateAndStatus("ZUL0001", SessionStatus.ACTIVE))
                .thenReturn(Optional.of(session));

        parkingService.processWebhook(new WebhookEventDTO(
                "ZUL0001", null, Instant.parse("2025-01-01T12:30:30Z"), null, null, "EXIT"));

        // 30min30s > 30min -> 1 hora cobrada -> 10.00
        assertEquals(new BigDecimal("10.00"), session.getTotalAmount());
    }

    @Test
    void exit_freeWhenExactly30Minutes() {
        // 30 minutos exatos ainda são grátis ("primeiros 30 minutos são grátis").
        Sector sector = sectorA(new BigDecimal("10.00"), 100);
        Spot spot = spot(1, sector, -23.5, -46.6);
        spot.setStatus(SpotStatus.OCCUPIED);
        ParkingSession session = ParkingSession.builder()
                .licensePlate("ZUL0001").status(SessionStatus.ACTIVE).spot(spot).sector(sector)
                .dynamicHourlyRate(new BigDecimal("10.00"))
                .entryTime(Instant.parse("2025-01-01T12:00:00Z")).build();

        when(sessionRepository.findByLicensePlateAndStatus("ZUL0001", SessionStatus.ACTIVE))
                .thenReturn(Optional.of(session));

        parkingService.processWebhook(new WebhookEventDTO(
                "ZUL0001", null, Instant.parse("2025-01-01T12:30:00Z"), null, null, "EXIT"));

        assertEquals(0, new BigDecimal("0.00").compareTo(session.getTotalAmount()));
    }

    @Test
    void exit_chargesRoundedUpHoursIncludingFirstHour() {
        Sector sector = sectorA(new BigDecimal("10.00"), 100);
        Spot spot = spot(1, sector, -23.5, -46.6);
        spot.setStatus(SpotStatus.OCCUPIED);
        ParkingSession session = ParkingSession.builder()
                .licensePlate("ZUL0001").status(SessionStatus.ACTIVE).spot(spot).sector(sector)
                .dynamicHourlyRate(new BigDecimal("10.00"))
                .entryTime(Instant.parse("2025-01-01T12:00:00Z")).build();

        when(sessionRepository.findByLicensePlateAndStatus("ZUL0001", SessionStatus.ACTIVE))
                .thenReturn(Optional.of(session));

        // 1h30 -> arredonda para 2 horas -> 2 * 10.00 = 20.00
        parkingService.processWebhook(new WebhookEventDTO(
                "ZUL0001", null, Instant.parse("2025-01-01T13:30:00Z"), null, null, "EXIT"));

        assertEquals(new BigDecimal("20.00"), session.getTotalAmount());
    }

    @Test
    void unknownEventType_throwsBadRequest() {
        assertThrows(GarageException.class, () -> parkingService.processWebhook(
                new WebhookEventDTO("ZUL0001", null, null, null, null, "FOO")));
    }
}
