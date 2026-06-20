package br.com.estapar.garage.repository;

import br.com.estapar.garage.model.Spot;
import br.com.estapar.garage.model.SpotStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SpotRepository extends JpaRepository<Spot, Long> {
    Optional<Spot> findByLatAndLng(Double lat, Double lng);

    long countByStatus(SpotStatus status);

    long countBySectorIdAndStatus(String sectorId, SpotStatus status);
}
