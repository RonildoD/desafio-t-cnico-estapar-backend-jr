package br.com.estapar.garage.dto;

import java.time.Instant;
import com.fasterxml.jackson.annotation.JsonProperty;

public record WebhookEventDTO(
        @JsonProperty("license_plate") String licensePlate,
        @JsonProperty("entry_time") Instant entryTime,
        @JsonProperty("exit_time") Instant exitTime,
        Double lat,
        Double lng,
        @JsonProperty("event_type") String eventType
) {}
