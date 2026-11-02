package ru.mifi.hotel;

import java.time.LocalDate;
import jakarta.persistence.*;

@Entity
@Table(name = "room_slots", indexes = @Index(columnList = "room_id,start_date,end_date"))
public class RoomSlot {
    @Id @Column(length = 36) public String bookingId;
    @Column(name = "room_id", nullable = false) public Long roomId;
    @Column(name = "start_date") public LocalDate startDate;
    @Column(name = "end_date") public LocalDate endDate;
    @Column(nullable = false) public boolean released;
    protected RoomSlot() {}
    RoomSlot(String bookingId, Long roomId, LocalDate startDate, LocalDate endDate, boolean released) {
        this.bookingId = bookingId; this.roomId = roomId; this.startDate = startDate; this.endDate = endDate; this.released = released;
    }
}
