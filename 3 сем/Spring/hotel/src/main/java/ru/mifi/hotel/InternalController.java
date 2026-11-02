package ru.mifi.hotel;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.mifi.common.ApiException;

@RestController
@RequestMapping("/internal/rooms")
public class InternalController {
    public record SlotInput(@NotBlank @Pattern(regexp = "[0-9a-fA-F-]{36}") String bookingId, @NotNull LocalDate startDate, @NotNull LocalDate endDate) {}
    public record ReleaseInput(@NotBlank @Pattern(regexp = "[0-9a-fA-F-]{36}") String bookingId) {}
    private final SlotService slots;
    private final HotelService hotels;
    private final byte[] secret;
    InternalController(SlotService slots, HotelService hotels, @Value("${app.internal-secret}") String secret) {
        this.slots = slots; this.hotels = hotels; this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }
    private void check(String supplied) {
        if (!MessageDigest.isEqual(secret, supplied.getBytes(StandardCharsets.UTF_8))) throw new ApiException(HttpStatus.FORBIDDEN, "Нет доступа");
    }
    @PostMapping("/{id}/confirm-availability")
    public void confirm(@RequestHeader(value = "X-Internal-Secret", defaultValue = "") String key, @PathVariable Long id, @Valid @RequestBody SlotInput input) {
        check(key); slots.confirm(id, input.bookingId(), input.startDate(), input.endDate());
    }
    @PostMapping("/{id}/release")
    public void release(@RequestHeader(value = "X-Internal-Secret", defaultValue = "") String key, @PathVariable Long id, @Valid @RequestBody ReleaseInput input) {
        check(key); slots.release(id, input.bookingId());
    }
    @GetMapping("/recommend")
    public List<Room> recommend(@RequestHeader(value = "X-Internal-Secret", defaultValue = "") String key,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long hotelId) {
        check(key); return hotels.available(startDate, endDate, hotelId, true);
    }
}
