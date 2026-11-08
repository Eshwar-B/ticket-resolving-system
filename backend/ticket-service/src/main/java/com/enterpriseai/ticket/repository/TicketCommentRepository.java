package com.enterpriseai.ticket.repository;

import com.enterpriseai.ticket.entity.TicketComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TicketCommentRepository extends JpaRepository<TicketComment, UUID> {

    List<TicketComment> findByTicketIdAndDeletedAtIsNullOrderByCreatedAtAsc(UUID ticketId);
}

