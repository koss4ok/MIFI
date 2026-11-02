package ru.mifi.booking;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mifi.common.ApiException;

@Service
public class BookingStore {
    private static final Logger log = LoggerFactory.getLogger(BookingStore.class);
    public record View(String id, String requestId, Long userId, Long roomId, LocalDate startDate, LocalDate endDate, String status, Instant createdAt, boolean releasePending, boolean autoSelect, Long requestedHotelId) {}
    public record Created(View booking, boolean fresh) {}
    private final BookingRepository bookings;
    private final UserRepository users;
    BookingStore(BookingRepository bookings, UserRepository users) { this.bookings = bookings; this.users = users; }

    @Transactional public Created create(Long userId, String requestId, Long roomId, LocalDate start, LocalDate end, boolean autoSelect, Long hotelId) {
        User user = users.lockById(userId).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Пользователь не найден"));
        var existing = bookings.findByUserIdAndRequestId(userId, requestId);
        if (existing.isPresent()) {
            Booking b = existing.get();
            if (b.autoSelect != autoSelect || (!autoSelect && !b.roomId.equals(roomId)) || !Objects.equals(b.requestedHotelId, hotelId) || !b.startDate.equals(start) || !b.endDate.equals(end))
                throw new ApiException(HttpStatus.CONFLICT, "requestId уже использован с другими данными");
            return new Created(view(b), false);
        }
        Booking b = bookings.saveAndFlush(new Booking(UUID.randomUUID().toString(), requestId, user, roomId, start, end, autoSelect, hotelId));
        log.info("bookingId={} requestId={} status=PENDING", b.id, requestId);
        return new Created(view(b), true);
    }
    @Transactional(readOnly = true) public View byRequest(String requestId, Long userId) {
        return bookings.findByUserIdAndRequestId(userId, requestId).map(BookingStore::view).orElse(null);
    }
    @Transactional(readOnly = true) public View get(String id, Long userId) { return view(owned(id, userId)); }
    @Transactional(readOnly = true) public Page<View> history(Long userId, Pageable page) { return bookings.findByUserId(userId, page).map(BookingStore::view); }
    @Transactional public View finish(String id, boolean success) {
        Booking b = locked(id);
        if (b.status.equals("PENDING")) { b.status = success ? "CONFIRMED" : "CANCELLED"; b.releasePending = !success; }
        log.info("bookingId={} status={} releasePending={}", b.id, b.status, b.releasePending);
        return view(b);
    }
    @Transactional public View cancel(String id, Long userId) {
        Booking b = locked(id);
        if (!b.user.getId().equals(userId)) throw new ApiException(HttpStatus.NOT_FOUND, "Бронирование не найдено");
        if (b.status.equals("PENDING")) throw new ApiException(HttpStatus.CONFLICT, "Бронирование ещё обрабатывается");
        if (b.status.equals("CONFIRMED")) { b.status = "CANCELLED"; b.releasePending = true; }
        log.info("bookingId={} status={} releasePending={}", b.id, b.status, b.releasePending);
        return view(b);
    }
    @Transactional public void released(String id) { Booking b = locked(id); b.releasePending = false; log.info("bookingId={} компенсация выполнена", id); }
    @Transactional(readOnly = true) public List<View> pendingReleases() { return bookings.findByReleasePendingTrue().stream().map(BookingStore::view).toList(); }
    @Transactional(readOnly = true) public List<View> stalePending() { return bookings.findByStatusAndCreatedAtBefore("PENDING", Instant.now().minusSeconds(60)).stream().map(BookingStore::view).toList(); }
    private Booking owned(String id, Long userId) {
        Booking b = bookings.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Бронирование не найдено"));
        if (!b.user.getId().equals(userId)) throw new ApiException(HttpStatus.NOT_FOUND, "Бронирование не найдено");
        return b;
    }
    private Booking locked(String id) { return bookings.lockById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Бронирование не найдено")); }
    private static View view(Booking b) { return new View(b.id, b.requestId, b.user.getId(), b.roomId, b.startDate, b.endDate, b.status, b.createdAt, b.releasePending, b.autoSelect, b.requestedHotelId); }
}
