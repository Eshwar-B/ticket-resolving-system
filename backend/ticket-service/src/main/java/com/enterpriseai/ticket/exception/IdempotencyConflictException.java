package com.enterpriseai.ticket.exception;

/**
 * Thrown when an idempotency key conflict is detected.
 * HTTP 409 Conflict is mapped in the global exception handler.
 */
public class IdempotencyConflictException extends RuntimeException {

    private final String idempotencyKey;

    public IdempotencyConflictException(String idempotencyKey) {
        super("Request with Idempotency-Key already processed: " + idempotencyKey);
        this.idempotencyKey = idempotencyKey;
    }

    public String getIdempotencyKey() { return idempotencyKey; }
}

