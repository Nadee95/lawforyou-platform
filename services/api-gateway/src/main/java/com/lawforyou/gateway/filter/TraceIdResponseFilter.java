package com.lawforyou.gateway.filter;

import brave.Tracer;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Writes the current Micrometer/Brave {@code traceId} into every HTTP response
 * as {@code X-Trace-ID}.
 *
 * <p>This allows API clients and HTTP test files to copy the traceId directly
 * from the response header and paste it into Jaeger UI for distributed tracing.</p>
 *
 * <p>Order {@code HIGHEST_PRECEDENCE + 5} — runs after {@link CorrelationIdFilter}
 * (HIGHEST_PRECEDENCE) but before {@link JwtAuthenticationFilter} (+10), ensuring
 * the tracing context is always written even when auth fails.</p>
 */
@Component
@RequiredArgsConstructor
public class TraceIdResponseFilter implements GlobalFilter, Ordered {

    private static final String HEADER_TRACE_ID = "X-Trace-ID";

    private final Tracer tracer;

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 5;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange).doFinally(signalType -> {
            brave.Span span = tracer.currentSpan();
            if (span != null) {
                String traceId = span.context().traceIdString();
                // setHeader — idempotent; only write once even if filter fires twice
                exchange.getResponse().getHeaders().set(HEADER_TRACE_ID, traceId);
            }
        });
    }
}

