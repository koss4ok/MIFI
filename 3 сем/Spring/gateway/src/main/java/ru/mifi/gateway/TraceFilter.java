package ru.mifi.gateway;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class TraceFilter implements GlobalFilter, Ordered {
    private static final Logger log = LoggerFactory.getLogger(TraceFilter.class);
    @Override public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String traceId = UUID.randomUUID().toString();
        var request = exchange.getRequest().mutate().headers(headers -> {
            headers.set("X-Trace-Id", traceId);
            headers.remove("X-Internal-Secret");
        }).build();
        exchange.getResponse().getHeaders().set("X-Trace-Id", traceId);
        return chain.filter(exchange.mutate().request(request).build()).doFinally(signal ->
                log.info("traceId={} method={} path={} status={}", traceId, request.getMethod(), request.getURI().getPath(), exchange.getResponse().getStatusCode()));
    }
    @Override public int getOrder() { return -100; }
}
