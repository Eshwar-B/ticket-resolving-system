package com.enterpriseai.common.tracing;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Optional;

/**
 * W3C Trace Context Propagator.
 * Parses, generates, and propagates standard `traceparent` headers:
 * Format: 00-{trace_id_32_hex}-{span_id_16_hex}-{flags_2_hex}
 */
public final class W3CTracePropagator {

    public static final String TRACEPARENT_HEADER = "traceparent";
    private static final SecureRandom RANDOM = new SecureRandom();

    private W3CTracePropagator() {}

    public record TraceContext(String traceId, String spanId, String flags) {
        public String toHeaderValue() {
            return String.format("00-%s-%s-%s", traceId, spanId, flags != null ? flags : "01");
        }
    }

    public static Optional<TraceContext> parse(String traceparent) {
        if (traceparent == null || traceparent.isBlank()) {
            return Optional.empty();
        }
        String[] parts = traceparent.trim().split("-");
        if (parts.length < 4 || parts[1].length() != 32 || parts[2].length() != 16) {
            return Optional.empty();
        }
        return Optional.of(new TraceContext(parts[1], parts[2], parts[3]));
    }

    public static TraceContext createNew() {
        byte[] traceBytes = new byte[16];
        byte[] spanBytes = new byte[8];
        RANDOM.nextBytes(traceBytes);
        RANDOM.nextBytes(spanBytes);

        String traceId = HexFormat.of().formatHex(traceBytes);
        String spanId = HexFormat.of().formatHex(spanBytes);
        return new TraceContext(traceId, spanId, "01");
    }

    public static TraceContext childOf(TraceContext parent) {
        byte[] spanBytes = new byte[8];
        RANDOM.nextBytes(spanBytes);
        String childSpanId = HexFormat.of().formatHex(spanBytes);
        return new TraceContext(parent.traceId(), childSpanId, parent.flags());
    }
}
