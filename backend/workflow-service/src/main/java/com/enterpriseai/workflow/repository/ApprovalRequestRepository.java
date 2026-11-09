package com.enterpriseai.workflow.repository;

import com.enterpriseai.workflow.entity.ApprovalRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, UUID> {

    List<ApprovalRequest> findByTenantIdAndTicketIdOrderByCreatedAtDesc(String tenantId, UUID ticketId);

    List<ApprovalRequest> findByTenantIdAndStatusOrderByCreatedAtDesc(String tenantId, ApprovalRequest.ApprovalStatus status);
}
