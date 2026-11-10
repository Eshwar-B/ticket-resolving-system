/**
 * API Service for Enterprise AI Platform Control Plane.
 * Communicates with backend microservices using validated tenant headers.
 */

const TICKET_SERVICE_URL = import.meta.env.VITE_TICKET_SERVICE_URL || 'http://localhost:8081';
const WORKFLOW_SERVICE_URL = import.meta.env.VITE_WORKFLOW_SERVICE_URL || 'http://localhost:8083';
const KNOWLEDGE_SERVICE_URL = import.meta.env.VITE_KNOWLEDGE_SERVICE_URL || 'http://localhost:8082';
const NOTIFICATION_SERVICE_URL = import.meta.env.VITE_NOTIFICATION_SERVICE_URL || 'http://localhost:8084';
const AUDIT_SERVICE_URL = import.meta.env.VITE_AUDIT_SERVICE_URL || 'http://localhost:8085';

export interface Ticket {
  id: string;
  ticketNumber: number;
  subject: string;
  status: string;
  priority: string;
  category: string;
  createdAt: string;
}

export interface ApprovalRequest {
  id: string;
  ticketId: string;
  actionType: string;
  status: string;
  requester: string;
  createdAt: string;
}

export interface DocumentMeta {
  id: string;
  title: string;
  filename: string;
  version: number;
  status: string;
  chunkCount: number;
  createdAt: string;
}

export interface AuditRecord {
  id: string;
  eventType: string;
  actorId: string;
  resourceType: string;
  resourceId: string;
  createdAt: string;
}

export const fetchTickets = async (tenantId: string): Promise<Ticket[]> => {
  const res = await fetch(`${TICKET_SERVICE_URL}/api/v1/tickets`, {
    headers: { 'X-Tenant-Id': tenantId }
  });
  if (!res.ok) return [];
  const data = await res.json();
  return data.content || [];
};

export const fetchPendingApprovals = async (tenantId: string): Promise<ApprovalRequest[]> => {
  const res = await fetch(`${WORKFLOW_SERVICE_URL}/api/v1/approvals/pending`, {
    headers: { 'X-Tenant-Id': tenantId }
  });
  if (!res.ok) return [];
  return await res.json();
};

export const submitApprovalDecision = async (
  tenantId: string,
  approvalId: string,
  decision: 'APPROVED' | 'REJECTED',
  approverId: string,
  note: string
): Promise<boolean> => {
  const res = await fetch(`${WORKFLOW_SERVICE_URL}/api/v1/approvals/${approvalId}/decision`, {
    method: 'POST',
    headers: {
      'X-Tenant-Id': tenantId,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ decision, approverId, note })
  });
  return res.ok;
};

export const fetchDocuments = async (tenantId: string): Promise<DocumentMeta[]> => {
  const res = await fetch(`${KNOWLEDGE_SERVICE_URL}/api/v1/knowledge/documents`, {
    headers: { 'X-Tenant-Id': tenantId }
  });
  if (!res.ok) return [];
  return await res.json();
};

export const fetchAuditEvents = async (tenantId: string): Promise<AuditRecord[]> => {
  const res = await fetch(`${AUDIT_SERVICE_URL}/api/v1/audit`, {
    headers: { 'X-Tenant-Id': tenantId }
  });
  if (!res.ok) return [];
  const data = await res.json();
  return data.content || [];
};

export const fetchNotifications = async (tenantId: string, userId: string): Promise<any[]> => {
  const res = await fetch(`${NOTIFICATION_SERVICE_URL}/api/v1/notifications?userId=${userId}`, {
    headers: { 'X-Tenant-Id': tenantId }
  });
  if (!res.ok) return [];
  const data = await res.json();
  return data.content || [];
};
