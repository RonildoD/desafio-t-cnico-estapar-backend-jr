package br.com.estapar.garage.repository;

import br.com.estapar.garage.model.Spot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SpotRepository extends JpaRepository<Spot,Long> {
    Optional<Spot> findByLatAndLng(Double lat, Double lng);
    long countBySectorIdAndStatus(String sectorId, br.com.estapar.garage.model.SpotStatus status);
    Optional<Spot> findFirstBySectorIdAndStatus(String sectorId, br.com.estapar.garage.model.SpotStatus status);
}
