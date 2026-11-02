package ru.mifi.hotel;

import jakarta.persistence.*;

@Entity
@Table(name = "hotels")
public class Hotel {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false) public String name;
    @Column(nullable = false) public String address;
    protected Hotel() {}
    Hotel(String name, String address) { this.name = name; this.address = address; }
}
