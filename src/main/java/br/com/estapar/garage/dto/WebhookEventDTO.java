package br.com.estapar.garage.dto;

import java.time.LocalDateTime;

public record WebhookEventDTO(String license_plate,
                              LocalDateTime entry_time,
                              LocalDateTime exit_time,
                              Double lat,
                              Double lng,
                              String event_type
) {}
