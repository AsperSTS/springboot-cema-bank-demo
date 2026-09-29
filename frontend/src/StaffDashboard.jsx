import { useEffect, useState } from 'react';
import { api } from './api';

const money = value => new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(Number(value || 0));
const titles = { ROLE_ADMIN: 'Administración', ROLE_EXECUTIVE: 'Ejecutivo', ROLE_ANALYST: 'Análisis' };
const roleNames = { ROLE_USER: 'Cliente', ROLE_ANALYST: 'Analista', ROLE_EXECUTIVE: 'Ejecutivo', ROLE_ADMIN: 'Administrador' };

export default function StaffDashboard({ user }) {
  const [stats, setStats] = useState(null);
  const [directory, setDirectory] = useState(null);
  const [credits, setCredits] = useState([]);
  const [page, setPage] = useState(0);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [roleDraft, setRoleDraft] = useState({});
  const [audit, setAudit] = useState([]);
  const canDecide = user.role !== 'ROLE_ANALYST';
  const isAdmin = user.role === 'ROLE_ADMIN';
  async function refresh() {
    try {
      const [summary, people, pending, changes] = await Promise.all([api.staffDashboard(), api.staffCustomers(page), api.staffCredits(), isAdmin ? api.roleAudit() : Promise.resolve([])]);
      setStats(summary); setDirectory(people); setCredits(pending); setError('');
      setAudit(changes);
    } catch (e) { setError(e.message); }
  }
  useEffect(() => {
    let active = true;
    Promise.all([api.staffDashboard(), api.staffCustomers(page), api.staffCredits(), isAdmin ? api.roleAudit() : Promise.resolve([])])
      .then(([summary, people, pending, changes]) => { if (active) { setStats(summary); setDirectory(people); setCredits(pending); setAudit(changes); setError(''); } })
      .catch(e => { if (active) setError(e.message); });
    return () => { active = false; };
  }, [page, isAdmin]);
  async function updateRole(person) {
    const next = roleDraft[person.id];
    if (!next || next === person.role) return;
    if (!window.confirm(`Cambiar a ${person.username} de ${roleNames[person.role]} a ${roleNames[next]}?`)) return;
    setBusy(true);
    try { await api.changeRole(person.id, next); setRoleDraft(previous => { const copy = { ...previous }; delete copy[person.id]; return copy; }); await refresh(); }
    catch (e) { setError(e.message); }
    finally { setBusy(false); }
  }
  async function decide(id, action) {
    if (!window.confirm(`${action === 'approve' ? 'Aprobar' : 'Rechazar'} esta solicitud simulada?`)) return;
    setBusy(true);
    try { await api.staffDecideCredit(id, action); await refresh(); }
    catch (e) { setError(e.message); }
    finally { setBusy(false); }
  }
  return <div className="container admin-page staff-page"><span className="eyebrow">{titles[user.role] || 'Equipo'}</span><h1>Centro de operaciones</h1><p>Vista interna de actividad simulada. {canDecide ? 'Puedes revisar solicitudes de crédito.' : 'Tu acceso es solo de consulta.'}</p>{error && <p role="alert" className="form-error">{error}</p>}
    {stats && <div className="admin-stats"><div>Clientes <strong>{stats.totalUsers}</strong></div><div>Equipo <strong>{stats.totalStaff}</strong></div><div>Movimientos <strong>{stats.totalTransactions}</strong></div><div>Saldo total simulado <strong>{money(stats.totalMoney)}</strong></div></div>}
    <section className="admin-credits"><h2>Solicitudes pendientes</h2><p>{canDecide ? 'La aprobación abona saldo de prueba y registra el movimiento.' : 'Puedes consultar las solicitudes; las decisiones corresponden a administración y ejecutivos.'}</p>{credits.length ? <ul>{credits.map(item => <li key={item.id}><span><b>{item.customer}</b><small>{money(item.amount)} · tasa ilustrativa {Number(item.rate) * 100}%</small></span>{canDecide && <div><button className="btn btn-quiet" disabled={busy} onClick={() => decide(item.id, 'reject')}>Rechazar</button><button className="btn btn-dark" disabled={busy} onClick={() => decide(item.id, 'approve')}>Aprobar</button></div>}</li>)}</ul> : <p>No hay solicitudes pendientes.</p>}</section>
    {directory && <section className="staff-directory"><h2>Directorio de perfiles</h2><p>Información mínima para supervisar la demostración. Las contraseñas y PIN no aparecen en esta vista interna.{isAdmin && ' Los cambios de rol quedan registrados.'}</p><div className="demo-table-wrap"><table className="demo-table"><thead><tr><th>Nombre</th><th>Usuario</th><th>Rol</th><th>Nivel</th>{isAdmin && <th>Gestión de acceso</th>}</tr></thead><tbody>{directory.items.map(person => <tr key={person.username}><td>{person.name}</td><td><code>{person.username}</code></td><td>{roleNames[person.role] || person.role}</td><td>{person.tier}</td>{isAdmin && <td><div className="role-controls"><select aria-label={`Rol de ${person.username}`} disabled={busy || person.username === user.username} value={roleDraft[person.id] || person.role} onChange={e => setRoleDraft(previous => ({ ...previous, [person.id]: e.target.value }))}>{Object.entries(roleNames).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select><button className="btn btn-quiet" disabled={busy || !roleDraft[person.id] || roleDraft[person.id] === person.role} onClick={() => updateRole(person)}>Guardar</button></div></td>}</tr>)}</tbody></table></div><div className="demo-pagination"><button className="btn btn-quiet" disabled={page === 0} onClick={() => setPage(page - 1)}>Anterior</button><span>Página {page + 1} de {Math.max(1, directory.totalPages)}</span><button className="btn btn-quiet" disabled={page + 1 >= directory.totalPages} onClick={() => setPage(page + 1)}>Siguiente</button></div></section>}
    {isAdmin && <section className="staff-directory"><h2>Registro de cambios de acceso</h2>{audit.length ? <div className="demo-table-wrap"><table className="demo-table"><thead><tr><th>Fecha</th><th>Administrador</th><th>Usuario</th><th>Antes</th><th>Después</th></tr></thead><tbody>{audit.map(item => <tr key={item.id}><td>{new Date(item.changedAt).toLocaleString('es-MX')}</td><td>{item.actorUsername}</td><td>{item.targetUsername}</td><td>{roleNames[item.oldRole]}</td><td>{roleNames[item.newRole]}</td></tr>)}</tbody></table></div> : <p>Aún no hay cambios de rol registrados.</p>}</section>}
  </div>;
}
