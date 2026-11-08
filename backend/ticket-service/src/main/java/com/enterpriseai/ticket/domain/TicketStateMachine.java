package com.enterpriseai.ticket.domain;

import com.enterpriseai.ticket.exception.InvalidStateTransitionException;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Enforces the ticket state machine — the single source of valid transitions.
 *
 * <p>All status changes MUST go through {@link #transition(TicketStatus, TicketStatus)}
 * rather than setting status directly on the entity.
 *
 * <pre>
 * NEW                  → OPEN, CANCELLED
 * OPEN                 → IN_PROGRESS, WAITING_FOR_CUSTOMER, WAITING_FOR_APPROVAL, CANCELLED
 * IN_PROGRESS          → WAITING_FOR_CUSTOMER, WAITING_FOR_APPROVAL, RESOLVED, CANCELLED
 * WAITING_FOR_CUSTOMER → IN_PROGRESS, RESOLVED, CANCELLED
 * WAITING_FOR_APPROVAL → IN_PROGRESS, CANCELLED
 * RESOLVED             → CLOSED, OPEN (reopen)
 * CLOSED               → OPEN (reopen)
 * CANCELLED            → (terminal — no transitions)
 * </pre>
 */
public final class TicketStateMachine {

    private static final Map<TicketStatus, Set<TicketStatus>> ALLOWED = Map.of(
            TicketStatus.NEW,                  EnumSet.of(TicketStatus.OPEN,         TicketStatus.CANCELLED),
            TicketStatus.OPEN,                 EnumSet.of(TicketStatus.IN_PROGRESS,  TicketStatus.WAITING_FOR_CUSTOMER,
                                                          TicketStatus.WAITING_FOR_APPROVAL, TicketStatus.CANCELLED),
            TicketStatus.IN_PROGRESS,          EnumSet.of(TicketStatus.WAITING_FOR_CUSTOMER, TicketStatus.WAITING_FOR_APPROVAL,
                                                          TicketStatus.RESOLVED, TicketStatus.CANCELLED),
            TicketStatus.WAITING_FOR_CUSTOMER, EnumSet.of(TicketStatus.IN_PROGRESS,  TicketStatus.RESOLVED, TicketStatus.CANCELLED),
            TicketStatus.WAITING_FOR_APPROVAL, EnumSet.of(TicketStatus.IN_PROGRESS,  TicketStatus.CANCELLED),
            TicketStatus.RESOLVED,             EnumSet.of(TicketStatus.CLOSED,        TicketStatus.OPEN),
            TicketStatus.CLOSED,               EnumSet.of(TicketStatus.OPEN),
            TicketStatus.CANCELLED,            EnumSet.noneOf(TicketStatus.class)
    );

    private TicketStateMachine() {}

    /**
     * Validates that the transition {@code from → to} is legal.
     *
     * @throws InvalidStateTransitionException if the transition is not permitted.
     */
    public static void transition(TicketStatus from, TicketStatus to) {
        Set<TicketStatus> allowed = ALLOWED.getOrDefault(from, EnumSet.noneOf(TicketStatus.class));
        if (!allowed.contains(to)) {
            throw new InvalidStateTransitionException(from, to, allowed);
        }
    }

    /** Returns true if the transition is valid without throwing. */
    public static boolean isValid(TicketStatus from, TicketStatus to) {
        return ALLOWED.getOrDefault(from, EnumSet.noneOf(TicketStatus.class)).contains(to);
    }
}

