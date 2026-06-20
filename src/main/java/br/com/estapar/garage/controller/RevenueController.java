package br.com.estapar.garage.controller;

import br.com.estapar.garage.dto.RevenueRequestDTO;
import br.com.estapar.garage.dto.RevenueResponseDTO;
import br.com.estapar.garage.service.RevenueService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/revenue")
@RequiredArgsConstructor
public class RevenueController {
    private final RevenueService revenueService;

    /**
     * Consulta de faturamento por setor e data.
     * O desafio documenta um corpo JSON {date, sector}; aceitamos esses campos
     * tanto via query params (forma REST canônica para GET) quanto via corpo.
     */
    @GetMapping
    public ResponseEntity<RevenueResponseDTO> getRevenue(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String sector,
            @RequestBody(required = false) RevenueRequestDTO body) {

        RevenueRequestDTO request = resolve(date, sector, body);
        return ResponseEntity.ok(revenueService.getDailyRevenue(request));
    }

    private RevenueRequestDTO resolve(LocalDate date, String sector, RevenueRequestDTO body) {
        LocalDate resolvedDate = date != null ? date : (body != null ? body.date() : null);
        String resolvedSector = sector != null ? sector : (body != null ? body.sector() : null);

        if (resolvedDate == null || resolvedSector == null) {
            throw new IllegalArgumentException("Parâmetros 'date' e 'sector' são obrigatórios.");
        }
        return new RevenueRequestDTO(resolvedDate, resolvedSector);
    }
}
