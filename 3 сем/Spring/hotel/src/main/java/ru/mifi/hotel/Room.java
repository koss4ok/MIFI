package ru.mifi.hotel;

import jakarta.persistence.*;

@Entity
@Table(name = "rooms", uniqueConstraints = @UniqueConstraint(columnNames = {"hotel_id", "number"}))
public class Room {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @ManyToOne(optional = false, fetch = FetchType.EAGER) @JoinColumn(name = "hotel_id") public Hotel hotel;
    @Column(nullable = false) public String number;
    public boolean available = true;
    @Column(nullable = false) public long timesBooked;
    protected Room() {}
    Room(Hotel hotel, String number, boolean available) { this.hotel = hotel; this.number = number; this.available = available; }
}
