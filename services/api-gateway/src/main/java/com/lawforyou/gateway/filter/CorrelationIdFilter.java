package com.lawforyou.gateway.filter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import java.util.UUID;
@Component
public class CorrelationIdFilter implements GlobalFilter, Ordered {
    private static final String HEADER_CORRELATION_ID = "X-Correlation-ID";
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String correlationId = exchange.getRequest().getHeaders().getFirst(HEADER_CORRELATION_ID);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        final String cid = correlationId;
        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .header(HEADER_CORRELATION_ID, cid)
                .build();
        ServerWebExchange mutatedExchange = exchange.mutate().request(mutated).build();
        // beforeCommit fires before headers are locked ? safe to write here
        mutatedExchange.getResponse().beforeCommit(() -> {
            mutatedExchange.getResponse().getHeaders().set(HEADER_CORRELATION_ID, cid);
            return Mono.empty();
        });
        return chain.filter(mutatedExchange);
    }
}