import React, { useEffect, useState } from 'react';
import { fetchTickets, fetchPendingApprovals, fetchDocuments } from '../services/api';

interface DashboardProps {
  tenantId: string;
}

export const Dashboard: React.FC<DashboardProps> = ({ tenantId }) => {
  const [ticketCount, setTicketCount] = useState<number>(0);
  const [pendingApprovals, setPendingApprovals] = useState<number>(0);
  const [docCount, setDocCount] = useState<number>(0);
  const [loading, setLoading] = useState<boolean>(true);

  useEffect(() => {
    const loadStats = async () => {
      setLoading(true);
      try {
        const [tickets, approvals, docs] = await Promise.all([
          fetchTickets(tenantId),
          fetchPendingApprovals(tenantId),
          fetchDocuments(tenantId),
        ]);
        setTicketCount(tickets.length);
        setPendingApprovals(approvals.length);
        setDocCount(docs.length);
      } catch (err) {
        console.error('Failed to load dashboard metrics', err);
      } finally {
        setLoading(false);
      }
    };
    loadStats();
  }, [tenantId]);

  return (
    <div style={{ padding: '2rem' }}>
      <h1 style={{ fontSize: '1.75rem', fontWeight: 'bold', marginBottom: '0.5rem' }}>
        Operational Overview
      </h1>
      <p style={{ color: '#94a3b8', marginBottom: '2rem' }}>
        Real-time telemetry and operational status for <strong>{tenantId}</strong>
      </p>

      {loading ? (
        <p>Loading metrics...</p>
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: '1.5rem' }}>
          <div style={{ backgroundColor: '#1e293b', padding: '1.5rem', borderRadius: '8px', border: '1px solid #334155' }}>
            <span style={{ fontSize: '0.875rem', color: '#94a3b8' }}>Total Active Tickets</span>
            <div style={{ fontSize: '2.25rem', fontWeight: 'bold', color: '#38bdf8', marginTop: '0.5rem' }}>
              {ticketCount}
            </div>
          </div>

          <div style={{ backgroundColor: '#1e293b', padding: '1.5rem', borderRadius: '8px', border: '1px solid #334155' }}>
            <span style={{ fontSize: '0.875rem', color: '#94a3b8' }}>Pending Human Approvals</span>
            <div style={{ fontSize: '2.25rem', fontWeight: 'bold', color: pendingApprovals > 0 ? '#f59e0b' : '#10b981', marginTop: '0.5rem' }}>
              {pendingApprovals}
            </div>
          </div>

          <div style={{ backgroundColor: '#1e293b', padding: '1.5rem', borderRadius: '8px', border: '1px solid #334155' }}>
            <span style={{ fontSize: '0.875rem', color: '#94a3b8' }}>Indexed RAG Documents</span>
            <div style={{ fontSize: '2.25rem', fontWeight: 'bold', color: '#a855f7', marginTop: '0.5rem' }}>
              {docCount}
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
