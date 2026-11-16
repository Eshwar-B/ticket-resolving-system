import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '10s', target: 15 },
    { duration: '30s', target: 30 },
    { duration: '10s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<400'], // P95 RAG search latency < 400ms
    http_req_failed: ['rate<0.01'],
  },
};

const BASE_URL = __ENV.KNOWLEDGE_BASE_URL || 'http://localhost:8082';
const QUERIES = [
  'empty package received refund policy',
  'how to return damaged hardware item',
  'international shipping tracking guidelines',
  'billing discrepancy invoice dispute',
  'software license activation failure',
];

export default function () {
  const query = QUERIES[Math.floor(Math.random() * QUERIES.length)];
  const tenantId = 'tenant-alpha';

  const params = {
    headers: {
      'X-Tenant-Id': tenantId,
    },
  };

  const res = http.get(
    `${BASE_URL}/api/v1/knowledge/search?query=${encodeURIComponent(query)}&limit=5`,
    params
  );

  check(res, {
    'status is 200': (r) => r.status === 200,
    'results is array': (r) => {
      try {
        const body = JSON.parse(r.body);
        return Array.isArray(body) || (body.results && Array.isArray(body.results));
      } catch (e) {
        return false;
      }
    },
  });

  sleep(0.2);
}
