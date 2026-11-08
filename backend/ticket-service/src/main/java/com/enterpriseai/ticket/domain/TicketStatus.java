package com.enterpriseai.ticket.domain;

/**
 * Ticket status state machine.
 *
 * <pre>
 *                      ┌──────────────────────────────────────┐
 *                      ▼                                      │ reopen
 *  NEW ──► OPEN ──► IN_PROGRESS ──► RESOLVED ──► CLOSED      │
 *            │           │                                    │
 *            │     WAITING_FOR_CUSTOMER ◄──────────────────── │
 *            │     WAITING_FOR_APPROVAL ◄──────────────────── │
 *            │                                                │
 *            └──────────────────────── CANCELLED             │
 * </pre>
 *
 * Valid transitions are enforced in {@link com.enterpriseai.ticket.domain.TicketStateMachine}.
 */
public enum TicketStatus {
    NEW,
    OPEN,
    IN_PROGRESS,
    WAITING_FOR_CUSTOMER,
    WAITING_FOR_APPROVAL,
    RESOLVED,
    CLOSED,
    CANCELLED
}

