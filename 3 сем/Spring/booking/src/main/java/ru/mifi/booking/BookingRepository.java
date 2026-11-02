package ru.mifi.booking;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, String> {
    Optional<Booking> findByUserIdAndRequestId(Long userId, String requestId);
    Page<Booking> findByUserId(Long userId, Pageable pageable);
    boolean existsByUserId(Long userId);
    List<Booking> findByReleasePendingTrue();
    List<Booking> findByStatusAndCreatedAtBefore(String status, Instant cutoff);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> lockById(@Param("id") String id);
}
