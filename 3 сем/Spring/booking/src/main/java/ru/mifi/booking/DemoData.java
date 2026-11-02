package ru.mifi.booking;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DemoData {
    @Bean CommandLineRunner userDemo(UserService service) {
        return args -> { service.create("admin", "admin123", "ADMIN"); service.create("student", "student123", "USER"); };
    }
}
