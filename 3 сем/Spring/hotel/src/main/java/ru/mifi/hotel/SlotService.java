package ru.mifi.hotel;

import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mifi.common.ApiException;

@Service
public class SlotService {
    private static final Logger log = LoggerFactory.getLogger(SlotService.class);
    private final RoomRepository rooms;
    private final SlotRepository slots;
    SlotService(RoomRepository rooms, SlotRepository slots) { this.rooms = rooms; this.slots = slots; }

    @Transactional
    public void confirm(Long roomId, String bookingId, LocalDate start, LocalDate end) {
        HotelService.validateDates(start, end);
        Room room = lockRoom(roomId);
        var existing = slots.findById(bookingId);
        if (existing.isPresent()) {
            RoomSlot slot = existing.get();
            if (!slot.roomId.equals(roomId) || slot.released || !start.equals(slot.startDate) || !end.equals(slot.endDate))
                throw new ApiException(HttpStatus.CONFLICT, "Запрос уже обработан с другими данными или отменён");
            return;
        }
        if (!room.available || slots.overlaps(roomId, start, end)) throw new ApiException(HttpStatus.CONFLICT, "Номер недоступен на эти даты");
        slots.save(new RoomSlot(bookingId, roomId, start, end, false));
        room.timesBooked++;
        log.info("bookingId={} roomId={} слот подтверждён", bookingId, roomId);
    }

    @Transactional
    public void release(Long roomId, String bookingId) {
        Room room = lockRoom(roomId);
        var existing = slots.findById(bookingId);
        if (existing.isEmpty()) {
            // Запись об отмене защищает от запоздавшего подтверждения после тайм-аута.
            slots.save(new RoomSlot(bookingId, roomId, null, null, true));
        } else {
            RoomSlot slot = existing.get();
            if (!slot.roomId.equals(roomId)) throw new ApiException(HttpStatus.CONFLICT, "Бронирование относится к другому номеру");
            if (!slot.released) { slot.released = true; room.timesBooked--; }
        }
        log.info("bookingId={} roomId={} слот освобождён", bookingId, roomId);
    }

    private Room lockRoom(Long id) {
        return rooms.lockById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Номер не найден"));
    }
}
