package br.com.estapar.garage.controller;
import br.com.estapar.garage.dto.RevenueRequestDTO;
import br.com.estapar.garage.dto.RevenueResponseDTO;
import br.com.estapar.garage.service.RevenueService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/revenue")
@RequiredArgsConstructor
public class RevenueController {
    private final RevenueService revenueService;

    @GetMapping
    public ResponseEntity<RevenueResponseDTO> getRevenue(@RequestBody RevenueRequestDTO request) {
        RevenueResponseDTO response = revenueService.getDailyRevenue(request);
        return ResponseEntity.ok(response);
    }
}
