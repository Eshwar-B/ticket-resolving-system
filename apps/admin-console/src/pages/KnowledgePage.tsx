import React, { useEffect, useState } from 'react';
import { fetchDocuments, DocumentMeta } from '../services/api';

interface KnowledgePageProps {
  tenantId: string;
}

export const KnowledgePage: React.FC<KnowledgePageProps> = ({ tenantId }) => {
  const [docs, setDocs] = useState<DocumentMeta[]>([]);
  const [loading, setLoading] = useState<boolean>(true);

  useEffect(() => {
    const load = async () => {
      setLoading(true);
      const data = await fetchDocuments(tenantId);
      setDocs(data);
      setLoading(false);
    };
    load();
  }, [tenantId]);

  return (
    <div style={{ padding: '2rem' }}>
      <h1 style={{ fontSize: '1.75rem', fontWeight: 'bold', marginBottom: '0.5rem' }}>
        Knowledge Management & Hybrid RAG
      </h1>
      <p style={{ color: '#94a3b8', marginBottom: '1.5rem' }}>
        Active tenant policy documents indexed in Qdrant and PostgreSQL FTS.
      </p>

      {loading ? (
        <p>Loading documents...</p>
      ) : docs.length === 0 ? (
        <div style={{ backgroundColor: '#1e293b', padding: '2rem', borderRadius: '8px', textAlign: 'center' }}>
          No documents uploaded for {tenantId}.
        </div>
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '1rem' }}>
          {docs.map((d) => (
            <div
              key={d.id}
              style={{
                backgroundColor: '#1e293b',
                padding: '1.25rem',
                borderRadius: '8px',
                border: '1px solid #334155'
              }}
            >
              <div style={{ fontWeight: 'bold', fontSize: '1.1rem', marginBottom: '0.25rem' }}>
                {d.title}
              </div>
              <div style={{ fontSize: '0.875rem', color: '#94a3b8', marginBottom: '0.5rem' }}>
                {d.filename} (v{d.version})
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: '0.75rem', color: '#64748b' }}>
                <span>{d.chunkCount} Vector Chunks</span>
                <span style={{ color: '#10b981', fontWeight: 'bold' }}>{d.status}</span>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};
