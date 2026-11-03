package ru.mifi.booking;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ru.mifi.common.ApiException;

@Service
public class BookingService {
    private static final Logger log = LoggerFactory.getLogger(BookingService.class);
    public record Request(String requestId, Long roomId, Long hotelId, boolean autoSelect, LocalDate startDate, LocalDate endDate) {}
    private final BookingStore store;
    private final HotelClient hotel;
    BookingService(BookingStore store, HotelClient hotel) { this.store = store; this.hotel = hotel; }

    public BookingStore.View create(Long userId, Request request) {
        try {
            if (!UUID.fromString(request.requestId()).toString().equalsIgnoreCase(request.requestId())) throw new IllegalArgumentException();
        }
        catch (Exception e) { throw new ApiException(HttpStatus.BAD_REQUEST, "requestId должен быть UUID"); }
        if (request.startDate() == null || request.endDate() == null || request.startDate().isBefore(LocalDate.now()) || !request.startDate().isBefore(request.endDate()))
            throw new ApiException(HttpStatus.BAD_REQUEST, "Некорректные даты бронирования");
        if (!request.autoSelect() && request.roomId() == null) throw new ApiException(HttpStatus.BAD_REQUEST, "Укажите номер");
        var previous = previous(userId, request);
        if (previous != null) return previous;
        Long roomId = request.roomId();
        if (request.autoSelect()) {
            var rooms = hotel.recommend(request.startDate(), request.endDate(), request.hotelId());
            if (rooms == null || rooms.isEmpty()) {
                // Пока выполнялся подбор, такой же запрос мог уже занять последний номер.
                previous = previous(userId, request);
                if (previous != null) return previous;
                throw new ApiException(HttpStatus.CONFLICT, "Свободных номеров нет");
            }
            roomId = rooms.get(0).id();
        }
        var created = store.create(userId, request.requestId(), roomId, request.startDate(), request.endDate(), request.autoSelect(), request.hotelId());
        if (!created.fresh()) return created.booking();
        var booking = created.booking();
        try {
            hotel.confirm(booking);
            BookingStore.View result = store.finish(booking.id(), true);
            if (result.releasePending()) release(result);
            return result;
        } catch (RuntimeException ex) {
            var cancelled = store.finish(booking.id(), false);
            release(cancelled);
            throw ex;
        }
    }
    public BookingStore.View cancel(String id, Long userId) {
        var booking = store.cancel(id, userId);
        if (booking.releasePending()) release(booking);
        return store.get(id, userId);
    }
    private BookingStore.View previous(Long userId, Request request) {
        var previous = store.byRequest(request.requestId(), userId);
        if (previous != null && (previous.autoSelect() != request.autoSelect() || (!request.autoSelect() && !previous.roomId().equals(request.roomId())) ||
                !Objects.equals(previous.requestedHotelId(), request.hotelId()) || !previous.startDate().equals(request.startDate()) || !previous.endDate().equals(request.endDate())))
            throw new ApiException(HttpStatus.CONFLICT, "requestId уже использован с другими данными");
        return previous;
    }
    private void release(BookingStore.View booking) {
        try { hotel.release(booking); store.released(booking.id()); }
        catch (RuntimeException ex) { log.warn("bookingId={} компенсация не выполнена, будет повтор: {}", booking.id(), ex.getMessage()); }
    }
    @Scheduled(fixedDelayString = "${app.recovery-delay-ms:30000}")
    public void recover() {
        for (var pending : store.stalePending()) store.finish(pending.id(), false);
        for (var booking : store.pendingReleases()) release(booking);
    }
}
