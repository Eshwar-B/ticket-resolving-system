package com.enterpriseai.ticket.repository;

import com.enterpriseai.ticket.entity.Ticket;
import com.enterpriseai.ticket.domain.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * JPA repository for {@link Ticket}.
 *
 * <p>All queries are automatically scoped to the current tenant via PostgreSQL RLS
 * (app.tenant_id is set by TenantTransactionInterceptor before each transaction).
 * No manual {@code WHERE tenant_id = ?} clauses are needed in JPQL — RLS enforces it.
 */
@Repository
public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    Optional<Ticket> findByIdAndDeletedAtIsNull(UUID id);

    Optional<Ticket> findByIdempotencyKey(String idempotencyKey);

    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.status = :status AND t.deletedAt IS NULL")
    long countByStatus(@Param("status") TicketStatus status);

    @Query("SELECT t FROM Ticket t WHERE t.deletedAt IS NULL ORDER BY t.createdAt DESC")
    org.springframework.data.domain.Page<Ticket> findAllActive(org.springframework.data.domain.Pageable pageable);

    @Query("SELECT t FROM Ticket t WHERE t.assignedAgent = :agentId AND t.deletedAt IS NULL ORDER BY t.priority DESC, t.createdAt ASC")
    java.util.List<Ticket> findByAgent(@Param("agentId") String agentId);
}

