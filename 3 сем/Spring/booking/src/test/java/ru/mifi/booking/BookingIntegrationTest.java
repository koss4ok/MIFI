package ru.mifi.booking;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.mifi.common.ApiException;

@SpringBootTest(properties = {"eureka.client.enabled=false", "spring.cloud.discovery.enabled=false", "app.recovery-delay-ms=3600000"})
@AutoConfigureMockMvc
class BookingIntegrationTest {
    @Autowired BookingService service;
    @Autowired BookingStore store;
    @Autowired UserService users;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean HotelClient hotel;

    private BookingService.Request request() {
        return new BookingService.Request(UUID.randomUUID().toString(), 10L, null, false, LocalDate.now().plusDays(2), LocalDate.now().plusDays(3));
    }

    @Test void successAndRepeatedDeliveryKeepOneBooking() {
        Long userId = users.create("success" + UUID.randomUUID(), "password", "USER").id();
        var input = request();
        var first = service.create(userId, input);
        var second = service.create(userId, input);
        assertThat(first.status()).isEqualTo("CONFIRMED");
        assertThat(second.id()).isEqualTo(first.id());
        verify(hotel, times(1)).confirm(any());
        assertThatThrownBy(() -> service.create(userId, new BookingService.Request(input.requestId(), 11L, null, false, input.startDate(), input.endDate())))
                .isInstanceOf(ApiException.class);
        assertThat(service.cancel(first.id(), userId).status()).isEqualTo("CANCELLED");
        verify(hotel).release(any());
    }

    @Test void remoteFailureCancelsAndCompensates() {
        Long userId = users.create("failure" + UUID.randomUUID(), "password", "USER").id();
        var input = request();
        doThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "timeout")).when(hotel).confirm(any());
        assertThatThrownBy(() -> service.create(userId, input)).isInstanceOf(ApiException.class);
        var booking = store.byRequest(input.requestId(), userId);
        assertThat(booking.status()).isEqualTo("CANCELLED");
        assertThat(booking.releasePending()).isFalse();
        verify(hotel).release(any());
    }

    @Test void failedCompensationIsRetriedByRecovery() {
        Long userId = users.create("recovery" + UUID.randomUUID(), "password", "USER").id();
        var input = request();
        doThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "timeout")).when(hotel).confirm(any());
        doThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "timeout")).doNothing().when(hotel).release(any());
        assertThatThrownBy(() -> service.create(userId, input)).isInstanceOf(ApiException.class);
        assertThat(store.byRequest(input.requestId(), userId).releasePending()).isTrue();
        service.recover();
        assertThat(store.byRequest(input.requestId(), userId).releasePending()).isFalse();
    }

    @Test void loginJwtAndAuthorizationWorkOverHttp() throws Exception {
        mvc.perform(get("/api/bookings")).andExpect(status().isUnauthorized());
        String result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"student\",\"password\":\"student123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = json.readTree(result).get("token").asText();
        mvc.perform(get("/api/bookings").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mvc.perform(get("/api/users").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        mvc.perform(post("/api/bookings").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"requestId\":\"bad\"}")).andExpect(status().isBadRequest());
    }
}
