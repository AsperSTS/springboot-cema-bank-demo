let token = null;
export const hasSession = () => Boolean(token);
export const clearSession = () => { token = null; };

async function request(path, method = 'GET', data) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 12000);
  try {
    const response = await fetch(`/api${path}`, {
      method,
      headers: { ...(data ? { 'Content-Type': 'application/json' } : {}), ...(token ? { Authorization: `Bearer ${token}` } : {}) },
      body: data ? JSON.stringify(data) : undefined,
      signal: controller.signal,
      credentials: 'omit',
    });
    const result = await response.json().catch(() => ({}));
    if (response.status === 401 && token) token = null;
    if (!response.ok) {
      const error = new Error(result.error || 'No pudimos completar la solicitud. Inténtalo de nuevo.');
      error.status = response.status;
      throw error;
    }
    return result;
  } catch (error) {
    if (error.name === 'AbortError') throw new Error('La conexión tardó demasiado. Inténtalo de nuevo.');
    throw error;
  } finally { clearTimeout(timeout); }
}
export const api = {
  async login(username, password) { const result = await request('/auth/login', 'POST', { username, password }); token = result.token; return result; },
  async logout() { try { if (token) await request('/auth/logout', 'POST'); } finally { clearSession(); } },
  register: (data) => request('/register', 'POST', data),
  me: () => request('/auth/me'),
  dashboard: () => request('/dashboard'),
  statement: (accountId, from, to, page = 0) => request(`/statement?accountId=${encodeURIComponent(accountId)}&from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}&page=${page}`),
  demoCatalog: () => request('/demo/catalog'),
  staffDashboard: () => request('/staff/dashboard'),
  staffCustomers: (page = 0) => request(`/staff/customers?page=${page}`),
  staffCredits: () => request('/staff/credits'),
  staffDecideCredit: (id, decision) => request(`/staff/credits/${id}/${decision}`, 'POST'),
  changeRole: (id, role) => request(`/admin/users/${id}/role`, 'POST', { role }),
  roleAudit: () => request('/admin/role-audit'),
  admin: () => request('/admin/dashboard'),
  pendingCredits: () => request('/admin/credits'),
  decideCredit: (id, decision) => request(`/admin/credits/${id}/${decision}`, 'POST'),
  deposit: (accountId, amount) => request('/deposit', 'POST', { accountId, amount }),
  withdraw: (accountId, amount) => request('/withdraw', 'POST', { accountId, amount }),
  transfer: (accountId, destinationAccountNumber, amount) => request('/transfer', 'POST', { accountId, destinationAccountNumber, amount }),
  invest: (accountId, amount) => request('/invest', 'POST', { accountId, amount }),
  credit: (amount) => request('/request-credit', 'POST', { amount }),
  setPin: (accountId, pin) => request('/atm/pin', 'POST', { accountId, pin }),
};
