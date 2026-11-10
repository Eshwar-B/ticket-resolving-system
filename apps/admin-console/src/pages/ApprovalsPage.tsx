import React, { useEffect, useState } from 'react';
import { fetchPendingApprovals, submitApprovalDecision, ApprovalRequest } from '../services/api';

interface ApprovalsPageProps {
  tenantId: string;
}

export const ApprovalsPage: React.FC<ApprovalsPageProps> = ({ tenantId }) => {
  const [approvals, setApprovals] = useState<ApprovalRequest[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [actionMsg, setActionMsg] = useState<string>('');

  const loadApprovals = async () => {
    setLoading(true);
    const data = await fetchPendingApprovals(tenantId);
    setApprovals(data);
    setLoading(false);
  };

  useEffect(() => {
    loadApprovals();
  }, [tenantId]);

  const handleDecision = async (id: string, decision: 'APPROVED' | 'REJECTED') => {
    const success = await submitApprovalDecision(
      tenantId,
      id,
      decision,
      'manager-admin',
      `Decision ${decision} via Admin Console`
    );
    if (success) {
      setActionMsg(`Request ${id.slice(0, 8)} ${decision} successfully.`);
      loadApprovals();
      setTimeout(() => setActionMsg(''), 4000);
    } else {
      alert('Failed to execute decision');
    }
  };

  return (
    <div style={{ padding: '2rem' }}>
      <h1 style={{ fontSize: '1.75rem', fontWeight: 'bold', marginBottom: '0.5rem' }}>
        Pending Human Approvals
      </h1>
      <p style={{ color: '#94a3b8', marginBottom: '1.5rem' }}>
        Human-in-the-Loop governance for high-risk and financial actions.
      </p>

      {actionMsg && (
        <div style={{ backgroundColor: '#065f46', color: '#d1fae5', padding: '0.75rem 1rem', borderRadius: '6px', marginBottom: '1rem' }}>
          {actionMsg}
        </div>
      )}

      {loading ? (
        <p>Loading approval queue...</p>
      ) : approvals.length === 0 ? (
        <div style={{ backgroundColor: '#1e293b', padding: '2rem', borderRadius: '8px', textAlign: 'center' }}>
          No pending approvals for {tenantId}.
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          {approvals.map((a) => (
            <div
              key={a.id}
              style={{
                backgroundColor: '#1e293b',
                padding: '1.25rem',
                borderRadius: '8px',
                border: '1px solid #334155',
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center'
              }}
            >
              <div>
                <div style={{ fontWeight: 'bold', color: '#f59e0b', marginBottom: '0.25rem' }}>
                  {a.actionType}
                </div>
                <div style={{ fontSize: '0.875rem', color: '#94a3b8' }}>
                  Ticket ID: <code>{a.ticketId}</code> | Requester: {a.requester}
                </div>
                <div style={{ fontSize: '0.75rem', color: '#64748b', marginTop: '0.25rem' }}>
                  Created: {new Date(a.createdAt).toLocaleString()}
                </div>
              </div>

              <div style={{ display: 'flex', gap: '0.75rem' }}>
                <button
                  onClick={() => handleDecision(a.id, 'APPROVED')}
                  style={{
                    backgroundColor: '#10b981',
                    color: '#ffffff',
                    border: 'none',
                    padding: '0.5rem 1rem',
                    borderRadius: '4px',
                    fontWeight: 'bold',
                    cursor: 'pointer'
                  }}
                >
                  Approve
                </button>
                <button
                  onClick={() => handleDecision(a.id, 'REJECTED')}
                  style={{
                    backgroundColor: '#ef4444',
                    color: '#ffffff',
                    border: 'none',
                    padding: '0.5rem 1rem',
                    borderRadius: '4px',
                    fontWeight: 'bold',
                    cursor: 'pointer'
                  }}
                >
                  Reject
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};
