package br.com.estapar.garage.repository;

import br.com.estapar.garage.model.ParkingSession;
import br.com.estapar.garage.model.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
public interface ParkingSessionRepository extends JpaRepository<ParkingSession, Long>{

    Optional<ParkingSession> findByLicensePlateAndStatus(String licensePlate, SessionStatus status);

    @Query("SELECT COALESCE(SUM(p.totalAmount), 0) FROM ParkingSession p " +
            "WHERE p.sector.id = :sectorId " +
            "AND p.status = 'FINISHED' " +
            "AND DATE(p.exitTime) = :targetDate")
    BigDecimal calculateTotalRevenueBySectorAndDate(
            @Param("sectorId") String sectorId,
            @Param("targetDate") LocalDate targetDate
    );
}
