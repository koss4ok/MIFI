package ru.mifi.hotel;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mifi.common.ApiException;

@Service
public class HotelService {
    private final HotelRepository hotels;
    private final RoomRepository rooms;
    private final SlotRepository slots;
    HotelService(HotelRepository hotels, RoomRepository rooms, SlotRepository slots) { this.hotels = hotels; this.rooms = rooms; this.slots = slots; }

    @Transactional(readOnly = true) public List<Hotel> hotels() { return hotels.findAll(); }
    @Transactional public Hotel createHotel(String name, String address) { return hotels.save(new Hotel(name, address)); }
    @Transactional public Hotel updateHotel(Long id, String name, String address) { Hotel h = hotel(id); h.name = name; h.address = address; return h; }
    @Transactional public void deleteHotel(Long id) {
        if (rooms.findAll().stream().anyMatch(r -> r.hotel.getId().equals(id))) throw new ApiException(HttpStatus.CONFLICT, "В отеле есть номера");
        hotels.delete(hotel(id));
    }
    @Transactional public Room createRoom(Long hotelId, String number, boolean available) {
        if (rooms.existsByHotelIdAndNumber(hotelId, number)) throw new ApiException(HttpStatus.CONFLICT, "Номер уже существует");
        return rooms.save(new Room(hotel(hotelId), number, available));
    }
    @Transactional public Room updateRoom(Long id, Long hotelId, String number, boolean available) {
        Room r = rooms.lockById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Номер не найден"));
        if ((!r.hotel.getId().equals(hotelId) || !r.number.equals(number)) && rooms.existsByHotelIdAndNumber(hotelId, number))
            throw new ApiException(HttpStatus.CONFLICT, "Номер уже существует");
        r.hotel = hotel(hotelId); r.number = number; r.available = available; return r;
    }
    @Transactional public void deleteRoom(Long id) {
        Room r = rooms.lockById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Номер не найден"));
        if (slots.existsByRoomId(id)) throw new ApiException(HttpStatus.CONFLICT, "У номера есть история бронирований");
        rooms.delete(r);
    }
    @Transactional(readOnly = true) public List<Room> available(LocalDate start, LocalDate end, Long hotelId, boolean recommend) {
        validateDates(start, end);
        var stream = rooms.findAll().stream().filter(r -> r.available && (hotelId == null || hotelId.equals(r.hotel.getId())))
                .filter(r -> !slots.overlaps(r.id, start, end));
        if (recommend) stream = stream.sorted(Comparator.comparingLong((Room r) -> r.timesBooked).thenComparing(r -> r.id));
        return stream.toList();
    }
    @Transactional(readOnly = true) public List<Room> statistics() { return rooms.findAll(); }
    static void validateDates(LocalDate start, LocalDate end) {
        if (start == null || end == null || !start.isBefore(end)) throw new ApiException(HttpStatus.BAD_REQUEST, "Некорректный период");
    }
    Hotel hotel(Long id) { return hotels.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Отель не найден")); }
    Room room(Long id) { return rooms.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Номер не найден")); }
}
