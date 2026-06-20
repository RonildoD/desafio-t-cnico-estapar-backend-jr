package br.com.estapar.garage.service;

import br.com.estapar.garage.dto.RevenueRequestDTO;
import br.com.estapar.garage.dto.RevenueResponseDTO;
import br.com.estapar.garage.repository.ParkingSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RevenueServiceTest {

    @Mock
    private ParkingSessionRepository sessionRepository;

    @InjectMocks
    private RevenueService revenueService;

    @Test
    void testGetDailyRevenue_Success() {
        // Cenário (Arrange)
        LocalDate testDate = LocalDate.of(2025, 1, 1);
        RevenueRequestDTO request = new RevenueRequestDTO(testDate, "A");
        BigDecimal expectedRevenue = new BigDecimal("150.00");
        
        when(sessionRepository.calculateTotalRevenueBySectorAndDate(eq("A"), eq(testDate)))
                .thenReturn(expectedRevenue);

        // Ação (Act)
        RevenueResponseDTO response = revenueService.getDailyRevenue(request);

        // Verificação (Assert)
        assertNotNull(response);
        assertEquals(expectedRevenue, response.amount());
        assertEquals("BRL", response.currency());
        assertNotNull(response.timestamp());
    }

    @Test
    void testGetDailyRevenue_amountHasTwoDecimalsAndIsoTimestamp() {
        LocalDate testDate = LocalDate.of(2025, 1, 1);
        RevenueRequestDTO request = new RevenueRequestDTO(testDate, "A");
        // Query retorna 0 "cru" (sem escala) quando não há receita.
        when(sessionRepository.calculateTotalRevenueBySectorAndDate(eq("A"), eq(testDate)))
                .thenReturn(BigDecimal.ZERO);

        RevenueResponseDTO response = revenueService.getDailyRevenue(request);

        // amount deve sair como 0.00 (2 casas), conforme exemplo do PDF.
        assertEquals("0.00", response.amount().toPlainString());
        // timestamp ISO-8601 UTC com milissegundos e sufixo Z (ex.: 2025-01-01T12:00:00.000Z).
        assertTrue(response.timestamp().matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z"),
                "timestamp fora do formato esperado: " + response.timestamp());
    }
}
