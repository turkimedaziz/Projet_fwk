package tn.rnu.eniso.fwk.scan.core.dal.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.rnu.eniso.fwk.scan.core.infra.model.Alert;
import tn.rnu.eniso.fwk.scan.core.infra.model.AlertSeverity;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {

    List<Alert> findTop100ByOrderByTimestampDesc();

    Page<Alert> findByOrderByTimestampDesc(Pageable pageable);

    List<Alert> findByTimestampBetween(LocalDateTime start, LocalDateTime end);

    List<Alert> findBySeverity(AlertSeverity severity);

    List<Alert> findBySeverityOrderByTimestampDesc(AlertSeverity severity);

    List<Alert> findBySeverity(AlertSeverity severity, Pageable pageable);

    @Query("SELECT a FROM Alert a WHERE a.sourceIp = :ip OR a.destIp = :ip ORDER BY a.timestamp DESC")
    List<Alert> findByIpAddress(@Param("ip") String ip);

    @Query("SELECT a FROM Alert a WHERE (a.sourceIp = :sourceIp OR a.destIp = :destIp) AND a.timestamp >= :since ORDER BY a.timestamp DESC")
    List<Alert> findByIpAddressAndTimestampAfter(
            @Param("sourceIp") String sourceIp,
            @Param("destIp") String destIp,
            @Param("since") LocalDateTime since);

    long countBySeverity(AlertSeverity severity);

    long countBySeverityAndTimestampAfter(AlertSeverity severity, LocalDateTime after);

    long countByTimestampAfter(LocalDateTime after);

    List<Alert> findByTimestampAfter(LocalDateTime after);

    @Query("SELECT a.category, COUNT(a) FROM Alert a WHERE a.timestamp >= :since GROUP BY a.category ORDER BY COUNT(a) DESC")
    List<Object[]> countByCategory(@Param("since") LocalDateTime since);

    @Query("SELECT a.sourceIp, COUNT(a) FROM Alert a WHERE a.timestamp >= :since GROUP BY a.sourceIp ORDER BY COUNT(a) DESC")
    List<Object[]> countBySourceIp(@Param("since") LocalDateTime since);

    @Query("SELECT a.destIp, COUNT(a) FROM Alert a WHERE a.timestamp >= :since GROUP BY a.destIp ORDER BY COUNT(a) DESC")
    List<Object[]> countByDestIp(@Param("since") LocalDateTime since);

    @Query("SELECT a.signature, COUNT(a) FROM Alert a WHERE a.timestamp >= :since GROUP BY a.signature ORDER BY COUNT(a) DESC")
    List<Object[]> countBySignature(@Param("since") LocalDateTime since);
}
