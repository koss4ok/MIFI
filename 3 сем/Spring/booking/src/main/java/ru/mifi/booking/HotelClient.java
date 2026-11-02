package ru.mifi.booking;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import org.springframework.web.util.UriComponentsBuilder;
import ru.mifi.common.ApiException;

@Component
public class HotelClient {
    private static final Logger log = LoggerFactory.getLogger(HotelClient.class);
    public record RoomInfo(Long id, long timesBooked) {}
    private final RestTemplate http;
    private final String url;
    private final String secret;
    HotelClient(RestTemplate http, @Value("${app.hotel-url}") String url, @Value("${app.internal-secret}") String secret) {
        this.http = http; this.url = url; this.secret = secret;
    }
    public List<RoomInfo> recommend(LocalDate start, LocalDate end, Long hotelId) {
        var uri = UriComponentsBuilder.fromUriString(url + "/internal/rooms/recommend").queryParam("startDate", start).queryParam("endDate", end);
        if (hotelId != null) uri.queryParam("hotelId", hotelId);
        return call("recommend", () -> http.exchange(uri.toUriString(), HttpMethod.GET, new HttpEntity<>(headers()), new ParameterizedTypeReference<List<RoomInfo>>() {}).getBody());
    }
    public void confirm(BookingStore.View booking) {
        call(booking.id(), () -> http.postForEntity(url + "/internal/rooms/" + booking.roomId() + "/confirm-availability",
                new HttpEntity<>(Map.of("bookingId", booking.id(), "startDate", booking.startDate(), "endDate", booking.endDate()), headers()), Void.class));
    }
    public void release(BookingStore.View booking) {
        call(booking.id(), () -> http.postForEntity(url + "/internal/rooms/" + booking.roomId() + "/release",
                new HttpEntity<>(Map.of("bookingId", booking.id()), headers()), Void.class));
    }
    private HttpHeaders headers() { HttpHeaders h = new HttpHeaders(); h.set("X-Internal-Secret", secret); return h; }
    private <T> T call(String bookingId, Supplier<T> action) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            try { return action.get(); }
            catch (HttpClientErrorException ex) {
                throw new ApiException(ex.getStatusCode().value() == 409 ? HttpStatus.CONFLICT : HttpStatus.BAD_GATEWAY, "Ошибка сервиса отелей: " + ex.getStatusCode());
            } catch (RestClientException ex) {
                log.warn("bookingId={} hotel attempt={} ошибка: {}", bookingId, attempt, ex.getMessage());
                if (attempt == 3) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Сервис отелей недоступен");
                try { Thread.sleep(200L << (attempt - 1)); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Запрос прерван"); }
            }
        }
        throw new IllegalStateException();
    }

    @Configuration
    static class HttpConfiguration {
        @Bean @LoadBalanced RestTemplate hotelRestTemplate(RestTemplateBuilder builder) {
            var factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(2000);
            factory.setReadTimeout(2000);
            return builder.requestFactory(() -> factory).build();
        }
    }
}
