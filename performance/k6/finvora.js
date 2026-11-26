import http from 'k6/http';
import { check, sleep, fail } from 'k6';

const base = 'http://127.0.0.1:8080';
export const options = {
  stages: [
    { duration: '10s', target: 1 },
    { duration: '20s', target: 1 },
    { duration: '10s', target: 5 },
    { duration: '20s', target: 5 },
    { duration: '10s', target: 10 },
    { duration: '20s', target: 10 },
    { duration: '10s', target: 0 },
  ],
  thresholds: {
    'http_req_failed{phase:load}': ['rate<0.01'],
    'http_req_duration{phase:load}': ['p(95)<1000'],
    'checks{phase:load}': ['rate>0.99'],
  },
  summaryTrendStats: ['avg','med','p(95)','p(99)','max'],
};

export function setup() {
  const headers = { 'Content-Type': 'application/json' };
  const credentials = { correo: 'k6-qa@example.test', password: 'Only-local-QA-2026!' };
  const reg = http.post(base + '/api/auth/register', JSON.stringify({
    ...credentials, nombreCompleto: 'Usuario QA k6', rol: 'VENDEDOR'
  }), { headers });
  if (reg.status !== 201) fail('No se pudo crear usuario local: ' + reg.status);
  const login = http.post(base + '/api/auth/login', JSON.stringify(credentials), { headers });
  if (login.status !== 200 || !login.json('token')) fail('Autenticacion local fallida');
  const token = login.json('token');
  const auth = { ...headers, Authorization: 'Bearer ' + token };
  for (let i = 0; i < 30; i++) {
    for (const tipo of ['ingreso','egreso']) {
      const date = new Date(Date.now() - (i+1)*86400000).toISOString().slice(0,19);
      const res = http.post(base + '/api/caja/transacciones', JSON.stringify({
        tipo, monto: tipo === 'ingreso' ? 100+i : 60+i, categoriaGasto: 'QA k6', fechaTransaccion: date
      }), { headers: auth });
      if (res.status !== 201) fail('No se pudo preparar datos sinteticos: ' + res.status);
    }
  }
  return { token };
}

export default function (data) {
  const headers = { Authorization: 'Bearer ' + data.token };
  for (const endpoint of ['/api/caja/historico','/api/flujo-caja-ia/dashboard','/api/flujo-caja-ia/recomendaciones']) {
    const tags = { phase: 'load', name: endpoint };
    const res = http.get(base + endpoint, { headers, tags, timeout: '5s' });
    check(res, { 'HTTP 200': r => r.status === 200, 'JSON valido': r => {
      try { return typeof r.json() === 'object' && r.json() !== null; } catch (_) { return false; }
    } }, { phase: 'load', endpoint });
    sleep(1);
  }
}

export function handleSummary(data) {
  const metrics = data.metrics;
  const durations = metrics['http_req_duration{phase:load}']?.values || {};
  const failures = metrics['http_req_failed{phase:load}']?.values || {};
  const checks = metrics['checks{phase:load}']?.values || {};
  const n = v => typeof v === 'number' ? v.toFixed(2) : 'N/D';
  const lines = [
    '## Grafana k6 — rendimiento HTTP en entorno aislado',
    '',
    'Spring Boot + H2 en GitHub Actions, 60 movimientos sinteticos, hasta 10 usuarios virtuales. No mide Render ni Supabase.',
    '',
    '| Indicador | Resultado |', '|---|---:|',
    '| Latencia promedio (ms) | ' + n(durations.avg) + ' |',
    '| Latencia P95 (ms) | ' + n(durations['p(95)']) + ' |',
    '| Latencia P99 (ms) | ' + n(durations['p(99)']) + ' |',
    '| Solicitudes fallidas (%) | ' + n(typeof failures.rate === 'number' ? failures.rate*100 : null) + ' |',
    '| Verificaciones correctas (%) | ' + n(typeof checks.rate === 'number' ? checks.rate*100 : null) + ' |',
    '| Solicitudes totales (incluye preparacion) | ' + n(metrics.http_reqs?.values?.count) + ' |',
    '', '### Umbrales propuestos (no requisitos confirmados del Word)',
    '', '| Umbral | Estado |', '|---|---|',
  ];
  for (const key of ['http_req_duration{phase:load}','http_req_failed{phase:load}','checks{phase:load}']) {
    for (const [rule, result] of Object.entries(metrics[key]?.thresholds || {})) {
      lines.push('| ' + key + ': ' + rule.replace(/\|/g,'/') + ' | ' + (result.ok ? 'APROBADO' : 'FALLIDO') + ' |');
    }
  }
  return { stdout: lines.join('\n') + '\n', '/reports/summary.md': lines.join('\n') + '\n', '/reports/summary.json': JSON.stringify(data,null,2) };
}
