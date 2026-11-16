import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '10s', target: 20 }, // Ramp-up to 20 virtual users
    { duration: '30s', target: 50 }, // Sustained load at 50 virtual users
    { duration: '10s', target: 0 },  // Ramp-down
  ],
  thresholds: {
    http_req_duration: ['p(95)<300', 'p(99)<500'], // 95% of requests must complete below 300ms
    http_req_failed: ['rate<0.01'],                // Error rate < 1%
  },
};

const BASE_URL = __ENV.API_BASE_URL || 'http://localhost:8081';
const TENANTS = ['tenant-alpha', 'tenant-beta', 'tenant-gamma'];

export default function () {
  const tenantId = TENANTS[Math.floor(Math.random() * TENANTS.length)];
  const idempotencyKey = `idemp-${__VU}-${__ITER}-${Date.now()}`;

  const payload = JSON.stringify({
    title: `Load Test Issue - User ${__VU} Iter ${__ITER}`,
    description: 'Automated load test verification payload for high-throughput ticket ingestion.',
    customerEmail: `customer-${__VU}@example.com`,
    customerName: `Customer ${__VU}`,
    priority: 'HIGH',
    category: 'TECHNICAL_ISSUE',
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
      'X-Tenant-Id': tenantId,
      'Idempotency-Key': idempotencyKey,
    },
  };

  const res = http.post(`${BASE_URL}/api/v1/tickets`, payload, params);

  check(res, {
    'status is 201 or 200': (r) => r.status === 201 || r.status === 200,
    'has ticket id': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.id !== undefined;
      } catch (e) {
        return false;
      }
    },
  });

  sleep(0.1);
}
