package ru.mifi.booking;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false, unique = true) public String username;
    @Column(nullable = false) public String password;
    @Column(nullable = false) public String role;
    protected User() {}
    User(String username, String password, String role) { this.username = username; this.password = password; this.role = role; }
    public Long getId() { return id; }
}
