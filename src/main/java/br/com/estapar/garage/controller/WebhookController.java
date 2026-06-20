package br.com.estapar.garage.controller;
import br.com.estapar.garage.dto.WebhookEventDTO;
import br.com.estapar.garage.service.ParkingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/webhook")
@RequiredArgsConstructor
public class WebhookController {
    private final ParkingService parkingService;

    @PostMapping
    public ResponseEntity<Void> receiveEvent(@RequestBody WebhookEventDTO event) {
        parkingService.processWebhook(event);
        return ResponseEntity.ok().build();
    }
}
