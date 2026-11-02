package ru.mifi.hotel;

import java.time.LocalDate;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface SlotRepository extends JpaRepository<RoomSlot, String> {
    @Query("select count(s) > 0 from RoomSlot s where s.roomId = :roomId and s.released = false and s.startDate < :end and s.endDate > :start")
    boolean overlaps(@Param("roomId") Long roomId, @Param("start") LocalDate start, @Param("end") LocalDate end);
    boolean existsByRoomId(Long roomId);
}
