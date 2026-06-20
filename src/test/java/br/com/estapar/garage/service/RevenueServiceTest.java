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
}
