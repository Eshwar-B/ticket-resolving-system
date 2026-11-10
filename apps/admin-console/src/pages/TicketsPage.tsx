import React, { useEffect, useState } from 'react';
import { fetchTickets, Ticket } from '../services/api';

interface TicketsPageProps {
  tenantId: string;
}

export const TicketsPage: React.FC<TicketsPageProps> = ({ tenantId }) => {
  const [tickets, setTickets] = useState<Ticket[]>([]);
  const [loading, setLoading] = useState<boolean>(true);

  useEffect(() => {
    const load = async () => {
      setLoading(true);
      const data = await fetchTickets(tenantId);
      setTickets(data);
      setLoading(false);
    };
    load();
  }, [tenantId]);

  return (
    <div style={{ padding: '2rem' }}>
      <h1 style={{ fontSize: '1.75rem', fontWeight: 'bold', marginBottom: '1.5rem' }}>
        Support Tickets ({tickets.length})
      </h1>

      {loading ? (
        <p>Loading tickets...</p>
      ) : tickets.length === 0 ? (
        <div style={{ backgroundColor: '#1e293b', padding: '2rem', borderRadius: '8px', textAlign: 'center' }}>
          No tickets found for {tenantId}.
        </div>
      ) : (
        <table style={{ width: '100%', borderCollapse: 'collapse', backgroundColor: '#1e293b', borderRadius: '8px' }}>
          <thead>
            <tr style={{ borderBottom: '1px solid #334155', textAlign: 'left', color: '#94a3b8' }}>
              <th style={{ padding: '1rem' }}>#</th>
              <th style={{ padding: '1rem' }}>Subject</th>
              <th style={{ padding: '1rem' }}>Status</th>
              <th style={{ padding: '1rem' }}>Priority</th>
              <th style={{ padding: '1rem' }}>Category</th>
              <th style={{ padding: '1rem' }}>Created</th>
            </tr>
          </thead>
          <tbody>
            {tickets.map((t) => (
              <tr key={t.id} style={{ borderBottom: '1px solid #334155' }}>
                <td style={{ padding: '1rem', color: '#38bdf8' }}>#{t.ticketNumber || t.id.slice(0, 6)}</td>
                <td style={{ padding: '1rem', fontWeight: '500' }}>{t.subject}</td>
                <td style={{ padding: '1rem' }}>
                  <span style={{
                    padding: '0.2rem 0.5rem',
                    borderRadius: '4px',
                    fontSize: '0.75rem',
                    fontWeight: 'bold',
                    backgroundColor: t.status === 'RESOLVED' ? '#065f46' : t.status === 'PENDING' ? '#78350f' : '#1e3a8a',
                    color: '#f8fafc'
                  }}>
                    {t.status}
                  </span>
                </td>
                <td style={{ padding: '1rem' }}>{t.priority}</td>
                <td style={{ padding: '1rem', color: '#94a3b8' }}>{t.category}</td>
                <td style={{ padding: '1rem', fontSize: '0.875rem', color: '#94a3b8' }}>
                  {new Date(t.createdAt).toLocaleDateString()}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
};
