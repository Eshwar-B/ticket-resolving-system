import React, { useEffect, useState } from 'react';
import { fetchAuditEvents, AuditRecord } from '../services/api';

interface AuditPageProps {
  tenantId: string;
}

export const AuditPage: React.FC<AuditPageProps> = ({ tenantId }) => {
  const [events, setEvents] = useState<AuditRecord[]>([]);
  const [loading, setLoading] = useState<boolean>(true);

  useEffect(() => {
    const load = async () => {
      setLoading(true);
      const data = await fetchAuditEvents(tenantId);
      setEvents(data);
      setLoading(false);
    };
    load();
  }, [tenantId]);

  return (
    <div style={{ padding: '2rem' }}>
      <h1 style={{ fontSize: '1.75rem', fontWeight: 'bold', marginBottom: '0.5rem' }}>
        Append-Only Audit Trail
      </h1>
      <p style={{ color: '#94a3b8', marginBottom: '1.5rem' }}>
        Immutable compliance log recording all system events for {tenantId}.
      </p>

      {loading ? (
        <p>Loading audit trail...</p>
      ) : events.length === 0 ? (
        <div style={{ backgroundColor: '#1e293b', padding: '2rem', borderRadius: '8px', textAlign: 'center' }}>
          No audit events recorded yet for {tenantId}.
        </div>
      ) : (
        <table style={{ width: '100%', borderCollapse: 'collapse', backgroundColor: '#1e293b', borderRadius: '8px' }}>
          <thead>
            <tr style={{ borderBottom: '1px solid #334155', textAlign: 'left', color: '#94a3b8' }}>
              <th style={{ padding: '1rem' }}>Timestamp</th>
              <th style={{ padding: '1rem' }}>Event Type</th>
              <th style={{ padding: '1rem' }}>Actor</th>
              <th style={{ padding: '1rem' }}>Resource Type</th>
              <th style={{ padding: '1rem' }}>Resource ID</th>
            </tr>
          </thead>
          <tbody>
            {events.map((e) => (
              <tr key={e.id} style={{ borderBottom: '1px solid #334155' }}>
                <td style={{ padding: '1rem', fontSize: '0.875rem', color: '#94a3b8' }}>
                  {new Date(e.createdAt).toLocaleString()}
                </td>
                <td style={{ padding: '1rem', fontWeight: 'bold', color: '#38bdf8' }}>
                  {e.eventType}
                </td>
                <td style={{ padding: '1rem' }}>{e.actorId}</td>
                <td style={{ padding: '1rem' }}>{e.resourceType}</td>
                <td style={{ padding: '1rem', fontFamily: 'monospace', fontSize: '0.875rem' }}>
                  {e.resourceId}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
};
