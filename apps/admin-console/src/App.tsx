import React, { useState } from 'react';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { NavBar } from './components/NavBar';
import { Dashboard } from './pages/Dashboard';
import { TicketsPage } from './pages/TicketsPage';
import { ApprovalsPage } from './pages/ApprovalsPage';
import { KnowledgePage } from './pages/KnowledgePage';
import { AuditPage } from './pages/AuditPage';

export const App: React.FC = () => {
  const [tenantId, setTenantId] = useState<string>('tenant-alpha');

  return (
    <BrowserRouter>
      <div style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column' }}>
        <NavBar tenantId={tenantId} onTenantChange={setTenantId} />
        <main style={{ flex: 1 }}>
          <Routes>
            <Route path="/" element={<Dashboard tenantId={tenantId} />} />
            <Route path="/tickets" element={<TicketsPage tenantId={tenantId} />} />
            <Route path="/approvals" element={<ApprovalsPage tenantId={tenantId} />} />
            <Route path="/knowledge" element={<KnowledgePage tenantId={tenantId} />} />
            <Route path="/audit" element={<AuditPage tenantId={tenantId} />} />
          </Routes>
        </main>
      </div>
    </BrowserRouter>
  );
};

export default App;
