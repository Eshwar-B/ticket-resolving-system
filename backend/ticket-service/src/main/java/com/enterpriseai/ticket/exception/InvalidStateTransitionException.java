package com.enterpriseai.ticket.exception;

import com.enterpriseai.ticket.domain.TicketStatus;
import java.util.Set;

/**
 * Thrown when a requested ticket status transition is not allowed by the state machine.
 */
public class InvalidStateTransitionException extends RuntimeException {

    private final TicketStatus from;
    private final TicketStatus to;
    private final Set<TicketStatus> allowed;

    public InvalidStateTransitionException(TicketStatus from, TicketStatus to, Set<TicketStatus> allowed) {
        super(String.format("Invalid transition %s -> %s. Allowed: %s", from, to, allowed));
        this.from = from;
        this.to = to;
        this.allowed = allowed;
    }

    public TicketStatus getFrom() { return from; }
    public TicketStatus getTo() { return to; }
    public Set<TicketStatus> getAllowed() { return allowed; }
}

