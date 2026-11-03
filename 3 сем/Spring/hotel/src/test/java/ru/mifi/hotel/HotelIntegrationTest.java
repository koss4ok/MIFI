package ru.mifi.hotel;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import ru.mifi.common.ApiException;

@SpringBootTest(properties = {"eureka.client.enabled=false", "spring.cloud.discovery.enabled=false"})
@AutoConfigureMockMvc
class HotelIntegrationTest {
    @Autowired HotelService hotels;
    @Autowired SlotService slots;
    @Autowired MockMvc mvc;

    @Test void concurrentOverlappingRequestsOnlyReserveOnce() throws Exception {
        Hotel hotel = hotels.createHotel("Параллельный тест", "Москва");
        Room room = hotels.createRoom(hotel.id, "1", true);
        LocalDate start = LocalDate.now().plusDays(10), end = start.plusDays(2);
        var pool = Executors.newFixedThreadPool(2);
        var startTogether = new CountDownLatch(1);
        Callable<Boolean> attempt = () -> {
            startTogether.await();
            try { slots.confirm(room.id, UUID.randomUUID().toString(), start, end); return true; }
            catch (ApiException e) { return false; }
        };
        try {
            var first = pool.submit(attempt); var second = pool.submit(attempt); startTogether.countDown();
            assertThat((first.get() ? 1 : 0) + (second.get() ? 1 : 0)).isEqualTo(1);
            assertThat(hotels.statistics().stream().filter(r -> r.id.equals(room.id)).findFirst().orElseThrow().timesBooked).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }

    @Test void repeatedConfirmationAndReleaseAreIdempotentAndLateConfirmationIsRejected() {
        Hotel hotel = hotels.createHotel("Повтор", "Москва");
        Room room = hotels.createRoom(hotel.id, "2", true);
        LocalDate start = LocalDate.now().plusDays(1), end = start.plusDays(1);
        String id = UUID.randomUUID().toString();
        slots.confirm(room.id, id, start, end); slots.confirm(room.id, id, start, end);
        assertThat(hotels.statistics().stream().filter(r -> r.id.equals(room.id)).findFirst().orElseThrow().timesBooked).isEqualTo(1);
        slots.release(room.id, id); slots.release(room.id, id);
        assertThatThrownBy(() -> slots.confirm(room.id, id, start, end)).isInstanceOf(ApiException.class);
        assertThat(hotels.available(start, end, hotel.id, true)).extracting(r -> r.id).containsExactly(room.id);
        String late = UUID.randomUUID().toString();
        slots.release(room.id, late);
        assertThatThrownBy(() -> slots.confirm(room.id, late, start, end)).isInstanceOf(ApiException.class);
    }

    @Test void recommendationsUseCountThenId() {
        Hotel hotel = hotels.createHotel("Рекомендации", "Москва");
        Room first = hotels.createRoom(hotel.id, "1", true);
        Room second = hotels.createRoom(hotel.id, "2", true);
        Room third = hotels.createRoom(hotel.id, "3", true);
        LocalDate start = LocalDate.now().plusDays(5);
        slots.confirm(first.id, UUID.randomUUID().toString(), start, start.plusDays(1));
        assertThat(hotels.available(start.plusDays(2), start.plusDays(3), hotel.id, true)).extracting(r -> r.id)
                .containsExactly(second.id, third.id, first.id);
    }

    @Test void securityProtectsAdminAndInternalEndpoints() throws Exception {
        mvc.perform(get("/api/hotels")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/rooms/1").with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_USER"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.hotel.id").value(1));
        mvc.perform(post("/api/hotels").with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_USER")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Hotel\",\"address\":\"Moscow\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/internal/rooms/recommend").param("startDate", "2030-01-01").param("endDate", "2030-01-02"))
                .andExpect(status().isForbidden());
    }
}
