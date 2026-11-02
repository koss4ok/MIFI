package ru.mifi.booking;

import java.time.LocalDate;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.*;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {
    public record BookingInput(@NotBlank String requestId, Long roomId, Long hotelId, boolean autoSelect, @NotNull LocalDate startDate, @NotNull LocalDate endDate) {}
    private final BookingService service;
    private final BookingStore store;
    BookingController(BookingService service, BookingStore store) { this.service = service; this.store = store; }
    @PostMapping public BookingStore.View create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody BookingInput input) {
        return service.create(Long.valueOf(jwt.getSubject()), new BookingService.Request(input.requestId(), input.roomId(), input.hotelId(), input.autoSelect(), input.startDate(), input.endDate()));
    }
    @GetMapping public Page<BookingStore.View> history(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return store.history(Long.valueOf(jwt.getSubject()), PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }
    @GetMapping("/{id}") public BookingStore.View get(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) { return store.get(id, Long.valueOf(jwt.getSubject())); }
    @DeleteMapping("/{id}") public BookingStore.View cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) { return service.cancel(id, Long.valueOf(jwt.getSubject())); }
}
