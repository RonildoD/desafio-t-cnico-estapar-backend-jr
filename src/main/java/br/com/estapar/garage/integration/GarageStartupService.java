package br.com.estapar.garage.integration;

import br.com.estapar.garage.dto.GarageSyncDTO;
import br.com.estapar.garage.model.Sector;
import br.com.estapar.garage.model.Spot;
import br.com.estapar.garage.model.SpotStatus;
import br.com.estapar.garage.repository.SectorRepository;
import br.com.estapar.garage.repository.SpotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class GarageStartupService {
    private final RestTemplate restTemplate;
    private final SectorRepository sectorRepository;
    private final SpotRepository spotRepository;

    @Value("${simulator.url:http://localhost:8080}")
    private String simulatorUrl;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void SyncGarageData() {
        log.info("Buscando dados da garagem no simulador...");
        System.out.println("DEBUG: Tentando conectar no simulador em: " + simulatorUrl);
        try {
            GarageSyncDTO response = restTemplate.getForObject(simulatorUrl + "/garage", GarageSyncDTO.class);

            if (response != null) {
                // 1. Salvar Setores
                response.garage().forEach(dto -> {
                    Sector sector = Sector.builder()
                            .id(dto.sector())
                            .basePrice(dto.basePrice())
                            .maxCapacity(dto.max_capacity())
                            .build();
                    sectorRepository.save(sector);
                });

                // 2. Salvar Vagas
                response.spots().forEach(dto -> {
                    Sector sectorRef = sectorRepository.getReferenceById(dto.sector());
                    Spot spot = Spot.builder()
                            .id(dto.id())
                            .sector(sectorRef)
                            .lat(dto.lat())
                            .lng(dto.lng())
                            .status(SpotStatus.AVAILABLE)
                            .build();
                    spotRepository.save(spot);
                });
                log.info("Garagem sincronizada com sucesso!");
            }
        } catch (Exception e) {
            log.error("Erro ao sincronizar dados da garagem no startup", e);
        }
    }
}
