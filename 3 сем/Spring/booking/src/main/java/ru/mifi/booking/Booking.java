package ru.mifi.booking;

import java.time.Instant;
import java.time.LocalDate;
import jakarta.persistence.*;

@Entity
@Table(name = "bookings", indexes = @Index(columnList = "user_id,created_at"), uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "request_id"}))
public class Booking {
    @Id @Column(length = 36) public String id;
    @Column(name = "request_id", nullable = false, length = 36) public String requestId;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "user_id") public User user;
    @Column(nullable = false) public Long roomId;
    @Column(nullable = false) public LocalDate startDate;
    @Column(nullable = false) public LocalDate endDate;
    @Column(nullable = false) public String status;
    @Column(nullable = false) public Instant createdAt;
    @Column(nullable = false) public boolean releasePending;
    @Column(nullable = false) public boolean autoSelect;
    public Long requestedHotelId;
    protected Booking() {}
    Booking(String id, String requestId, User user, Long roomId, LocalDate startDate, LocalDate endDate, boolean autoSelect, Long hotelId) {
        this.id = id; this.requestId = requestId; this.user = user; this.roomId = roomId;
        this.startDate = startDate; this.endDate = endDate; this.status = "PENDING"; this.createdAt = Instant.now();
        this.autoSelect = autoSelect; this.requestedHotelId = hotelId;
    }
}
