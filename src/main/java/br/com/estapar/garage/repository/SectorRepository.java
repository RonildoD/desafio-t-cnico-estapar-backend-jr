package br.com.estapar.garage.repository;

import br.com.estapar.garage.model.Sector;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

public interface SectorRepository  extends JpaRepository<Sector,String> {
}
