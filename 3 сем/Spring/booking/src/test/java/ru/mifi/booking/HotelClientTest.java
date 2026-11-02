package ru.mifi.booking;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.ExpectedCount.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import java.net.SocketTimeoutException;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import ru.mifi.common.ApiException;

class HotelClientTest {
    private final RestTemplate template = new RestTemplate();
    private final MockRestServiceServer server = MockRestServiceServer.createServer(template);
    private final HotelClient client = new HotelClient(template, "http://hotel", "secret");
    private final BookingStore.View booking = new BookingStore.View("e7e86ec5-932b-4ac6-aa3b-2b6da7e4d1ce", "1", 1L, 2L,
            LocalDate.of(2030, 1, 1), LocalDate.of(2030, 1, 2), "PENDING", Instant.now(), false, false, null);
    private final String url = "http://hotel/internal/rooms/2/confirm-availability";

    @Test void timeoutIsRetriedWithSameBookingId() {
        server.expect(requestTo(url)).andExpect(header("X-Internal-Secret", "secret")).andRespond(withException(new SocketTimeoutException()));
        server.expect(requestTo(url)).andExpect(content().json("{\"bookingId\":\"" + booking.id() + "\"}"))
                .andRespond(withSuccess());
        client.confirm(booking);
        server.verify();
    }

    @Test void timeoutStopsAfterThreeAttempts() {
        server.expect(times(3), requestTo(url)).andRespond(withException(new SocketTimeoutException()));
        assertThatThrownBy(() -> client.confirm(booking)).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        server.verify();
    }

    @Test void conflictIsNotRetried() {
        server.expect(once(), requestTo(url)).andRespond(withStatus(HttpStatus.CONFLICT));
        assertThatThrownBy(() -> client.confirm(booking)).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).status()).isEqualTo(HttpStatus.CONFLICT));
        server.verify();
    }
}
