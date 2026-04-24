package com.lawforyou.gateway.filter;

import io.micrometer.context.ContextSnapshot;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Writes the current Micrometer traceId into every HTTP response as {@code X-Trace-ID}.
 *
 * <p>Uses {@link Mono#deferContextual} + {@link ContextSnapshot} to restore the Micrometer
 * trace context across Netty thread boundaries before {@code beforeCommit} fires — solving
 * the ThreadLocal-loss problem in reactive/WebFlux pipelines.</p>
 *
 * <p>After restarting the gateway every response includes:</p>
 * <pre>
 * X-Trace-ID: 64f3a1c2b8e900001a2b3c4d
 * </pre>
 * Paste this value into Jaeger UI → "Lookup by Trace ID" to find the distributed trace.
 */
@Component
@RequiredArgsConstructor
public class TraceIdResponseFilter implements GlobalFilter, Ordered {

    private static final String HEADER_TRACE_ID = "X-Trace-ID";

    /** Use Micrometer's abstraction — not brave.Tracer directly — for reactive context support. */
    private final Tracer tracer;

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 5;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return Mono.deferContextual(contextView -> {
            // Capture full Reactor context (includes Micrometer observation/span) now,
            // before the pipeline goes async across Netty threads.
            ContextSnapshot snapshot = ContextSnapshot.captureFrom(contextView);

            exchange.getResponse().beforeCommit(() -> {
                // Restore the captured context on this thread so currentSpan() is non-null.
                try (ContextSnapshot.Scope ignored = snapshot.setThreadLocals()) {
                    io.micrometer.tracing.Span span = tracer.currentSpan();
                    if (span != null) {
                        exchange.getResponse().getHeaders()
                                .set(HEADER_TRACE_ID, span.context().traceId());
                    }
                }
                return Mono.empty();
            });

            return chain.filter(exchange);
        });
    }
}
