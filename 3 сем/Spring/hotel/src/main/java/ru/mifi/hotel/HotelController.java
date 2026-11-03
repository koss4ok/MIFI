package ru.mifi.hotel;

import java.time.LocalDate;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class HotelController {
    public record HotelInput(@NotBlank String name, @NotBlank String address) {}
    public record RoomInput(@NotNull Long hotelId, @NotBlank String number, boolean available) {}
    private final HotelService service;
    HotelController(HotelService service) { this.service = service; }

    @GetMapping("/hotels") public List<Hotel> hotels() { return service.hotels(); }
    @GetMapping("/hotels/{id}") public Hotel hotel(@PathVariable Long id) { return service.getHotel(id); }
    @PostMapping("/hotels") @PreAuthorize("hasAuthority('SCOPE_ADMIN')")
    public Hotel createHotel(@Valid @RequestBody HotelInput input) { return service.createHotel(input.name(), input.address()); }
    @PutMapping("/hotels/{id}") @PreAuthorize("hasAuthority('SCOPE_ADMIN')")
    public Hotel updateHotel(@PathVariable Long id, @Valid @RequestBody HotelInput input) { return service.updateHotel(id, input.name(), input.address()); }
    @DeleteMapping("/hotels/{id}") @PreAuthorize("hasAuthority('SCOPE_ADMIN')")
    public void deleteHotel(@PathVariable Long id) { service.deleteHotel(id); }
    @PostMapping("/rooms") @PreAuthorize("hasAuthority('SCOPE_ADMIN')")
    public Room createRoom(@Valid @RequestBody RoomInput input) { return service.createRoom(input.hotelId(), input.number(), input.available()); }
    @PutMapping("/rooms/{id}") @PreAuthorize("hasAuthority('SCOPE_ADMIN')")
    public Room updateRoom(@PathVariable Long id, @Valid @RequestBody RoomInput input) { return service.updateRoom(id, input.hotelId(), input.number(), input.available()); }
    @DeleteMapping("/rooms/{id}") @PreAuthorize("hasAuthority('SCOPE_ADMIN')")
    public void deleteRoom(@PathVariable Long id) { service.deleteRoom(id); }
    @GetMapping("/rooms")
    public List<Room> rooms(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                            @RequestParam(required = false) Long hotelId) { return service.available(startDate, endDate, hotelId, false); }
    @GetMapping("/rooms/recommend")
    public List<Room> recommend(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                @RequestParam(required = false) Long hotelId) { return service.available(startDate, endDate, hotelId, true); }
    @GetMapping("/rooms/statistics") @PreAuthorize("hasAuthority('SCOPE_ADMIN')")
    public List<Room> statistics() { return service.statistics(); }
    @GetMapping("/rooms/{id}") public Room room(@PathVariable Long id) { return service.getRoom(id); }
}
