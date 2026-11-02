package ru.mifi.hotel;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DemoData {
    @Bean CommandLineRunner hotelDemo(HotelService service) {
        return args -> {
            Hotel hotel = service.createHotel("Гостиница МИФИ", "Москва, Каширское шоссе, 31");
            service.createRoom(hotel.id, "101", true);
            service.createRoom(hotel.id, "102", true);
            service.createRoom(hotel.id, "103", false);
        };
    }
}
