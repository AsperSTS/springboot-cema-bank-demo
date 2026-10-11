let token = null;
async function request(path, method = 'GET', data) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 12000);
  try {
    const response = await fetch(`/api${path}`, { method, headers: { ...(data ? { 'Content-Type': 'application/json' } : {}), ...(token ? { Authorization: `Bearer ${token}` } : {}) }, ...(data ? { body: JSON.stringify(data) } : {}), signal: controller.signal, credentials: 'omit' });
    const result = await response.json().catch(() => ({}));
    if (response.status === 401 && token) token = null;
    if (!response.ok) { const error = new Error(result.error || 'No fue posible completar la operación.'); error.status = response.status; throw error; }
    return result;
  } catch (e) { if (e.name === 'AbortError') throw new Error('Tiempo de espera agotado. Inténtalo de nuevo.'); throw e; }
  finally { clearTimeout(timer); }
}
export const api = {
  demoCatalog: () => request('/demo/catalog'),
  async login(accountNumber, pin) { const result = await request('/atm/login', 'POST', { accountNumber, pin }); token = result.token; return result; },
  dashboard: () => request('/dashboard'),
  withdraw: (accountId, amount) => request('/atm/withdraw', 'POST', { accountId, amount }),
  deposit: (accountId, amount) => request('/atm/deposit', 'POST', { accountId, amount }),
  pin: (accountId, pin) => request('/atm/pin', 'POST', { accountId, pin }),
  async logout() { try { if (token) await request('/auth/logout', 'POST'); } finally { token = null; } },
};
