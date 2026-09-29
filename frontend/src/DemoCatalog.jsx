import { useEffect, useMemo, useState } from 'react';
import { api } from './api';

const roles = { ROLE_ADMIN: 'Administración', ROLE_EXECUTIVE: 'Ejecutivos', ROLE_ANALYST: 'Analistas', ROLE_USER: 'Clientes' };

export default function DemoCatalog({ onLogin }) {
  const [catalog, setCatalog] = useState(null);
  const [error, setError] = useState('');
  const [query, setQuery] = useState('');
  const [role, setRole] = useState('all');
  const [page, setPage] = useState(0);
  useEffect(() => { api.demoCatalog().then(setCatalog).catch(e => setError(e.message)); }, []);
  const filtered = useMemo(() => (catalog?.users || []).filter(user =>
    (role === 'all' || user.role === role) && `${user.name} ${user.username} ${user.tier}`.toLocaleLowerCase('es-MX').includes(query.toLocaleLowerCase('es-MX'))
  ), [catalog, query, role]);
  const totalPages = Math.max(1, Math.ceil(filtered.length / 20));
  const shown = filtered.slice(page * 20, page * 20 + 20);

  return <div className="container demo-page">
    <span className="eyebrow">ENTORNO DE PRUEBA</span>
    <h1>Personas y accesos de demostración</h1>
    <p>115 perfiles ficticios: 2 de administración, 6 ejecutivos, 7 analistas y 100 clientes. Cada cuenta incluye dos años de movimientos simulados.</p>
    <div className="demo-alert" role="note">Estas claves son públicas porque este entorno es una demostración local. No uses aquí datos personales ni contraseñas reales. Las cuentas y operaciones no pertenecen al sistema bancario mexicano.</div>
    <p className="demo-tier-note">Niveles de prueba por transferencia: Esencial $25,000 · Preferente $100,000 · Patrimonial $250,000 MXN. Son límites internos de esta simulación.</p>
    {error && <p className="form-error" role="alert">{error}</p>}
    {!catalog && !error && <p role="status">Cargando perfiles…</p>}
    {catalog && <>
      <div className="demo-toolbar"><label>Buscar perfil<input value={query} onChange={e => { setQuery(e.target.value); setPage(0); }} placeholder="Nombre, usuario o nivel"/></label><label>Tipo de acceso<select value={role} onChange={e => { setRole(e.target.value); setPage(0); }}><option value="all">Todos</option>{Object.entries(roles).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label><button className="btn btn-dark" onClick={onLogin}>Ir al acceso</button></div>
      <p className="demo-count">{filtered.length} perfiles · página {page + 1} de {totalPages}</p>
      <div className="demo-table-wrap"><table className="demo-table"><thead><tr><th>Persona</th><th>Rol / nivel</th><th>Usuario</th><th>Contraseña</th><th>Cuenta</th><th>PIN ATM</th></tr></thead><tbody>{shown.map(user => <tr key={user.username}><td><b>{user.name}</b></td><td>{roles[user.role]}<small>{user.tier}</small></td><td><code>{user.username}</code></td><td><code>{user.password}</code></td><td><code>{user.accountNumber}</code></td><td><code>{user.pin}</code></td></tr>)}</tbody></table></div>
      <div className="demo-pagination"><button className="btn btn-quiet" disabled={page === 0} onClick={() => setPage(page - 1)}>Anterior</button><button className="btn btn-quiet" disabled={page + 1 >= totalPages} onClick={() => setPage(page + 1)}>Siguiente</button></div>
    </>}
  </div>;
}
