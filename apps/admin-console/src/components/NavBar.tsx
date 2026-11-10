import React from 'react';
import { Link, useLocation } from 'react-router-dom';

interface NavBarProps {
  tenantId: string;
  onTenantChange: (tenant: string) => void;
}

export const NavBar: React.FC<NavBarProps> = ({ tenantId, onTenantChange }) => {
  const location = useLocation();

  const navLinks = [
    { path: '/', label: 'Dashboard' },
    { path: '/tickets', label: 'Tickets' },
    { path: '/approvals', label: 'Approvals' },
    { path: '/knowledge', label: 'Knowledge Base' },
    { path: '/audit', label: 'Audit Log' },
  ];

  return (
    <nav style={{
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'space-between',
      padding: '1rem 2rem',
      backgroundColor: '#1e293b',
      borderBottom: '1px solid #334155'
    }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '2rem' }}>
        <span style={{ fontWeight: 'bold', fontSize: '1.25rem', color: '#38bdf8' }}>
          Enterprise AI Console
        </span>
        <div style={{ display: 'flex', gap: '1rem' }}>
          {navLinks.map((link) => (
            <Link
              key={link.path}
              to={link.path}
              style={{
                color: location.pathname === link.path ? '#38bdf8' : '#94a3b8',
                textDecoration: 'none',
                fontWeight: location.pathname === link.path ? '600' : 'normal',
                padding: '0.25rem 0.5rem',
                borderRadius: '4px'
              }}
            >
              {link.label}
            </Link>
          ))}
        </div>
      </div>

      <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
        <span style={{ fontSize: '0.875rem', color: '#94a3b8' }}>Tenant:</span>
        <select
          value={tenantId}
          onChange={(e) => onTenantChange(e.target.value)}
          style={{
            backgroundColor: '#0f172a',
            color: '#f8fafc',
            border: '1px solid #475569',
            padding: '0.35rem 0.75rem',
            borderRadius: '4px'
          }}
        >
          <option value="tenant-alpha">tenant-alpha (Enterprise Corp)</option>
          <option value="tenant-beta">tenant-beta (FinTech Global)</option>
        </select>
      </div>
    </nav>
  );
};
