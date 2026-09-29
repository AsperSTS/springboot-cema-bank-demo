import { useEffect, useState } from 'react';
import { api } from './api';

const money = value => value == null ? 'No disponible' : new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(Number(value));
const localDate = date => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
const now = new Date();
const today = localDate(now);
const twoYearsAgo = localDate(new Date(now.getFullYear() - 2, now.getMonth(), now.getDate()));
const debit = new Set(['WITHDRAWAL', 'TRANSFER_OUT', 'PAYMENT', 'INVESTMENT']);

export default function StatementView({ accountId }) {
  const [from, setFrom] = useState(twoYearsAgo);
  const [to, setTo] = useState(today);
  const [page, setPage] = useState(0);
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');
  useEffect(() => {
    let active = true;
    api.statement(accountId, from, to, page).then(data => { if (active) { setResult(data); setError(''); } })
      .catch(e => { if (active) setError(e.message); });
    return () => { active = false; };
  }, [accountId, from, to, page]);
  return <section className="statement-panel" aria-labelledby="statement-title">
    <div className="statement-head"><div><span className="eyebrow">ESTADO DE CUENTA DE PRUEBA</span><h2 id="statement-title">Historial por periodo</h2><p>Movimientos internos de CEMA, expresados en pesos mexicanos. No es un documento bancario oficial.</p></div><div className="statement-filters"><label>Desde<input type="date" value={from} max={to} onChange={e => { setFrom(e.target.value); setPage(0); }}/></label><label>Hasta<input type="date" value={to} min={from} max={today} onChange={e => { setTo(e.target.value); setPage(0); }}/></label></div></div>
    {error && <p className="form-error" role="alert">{error}</p>}
    {result && <><div className="statement-summary"><div><span>Titular</span><strong>{result.holder}</strong></div><div><span>Cuenta interna</span><strong>{result.accountNumber}</strong></div><div><span>Saldo inicial</span><strong>{money(result.openingBalance)}</strong></div><div><span>Saldo al cierre</span><strong>{money(result.closingBalance)}</strong></div></div><p className="statement-count">{result.totalMovements} movimientos · {result.from} al {result.to}</p><div className="demo-table-wrap"><table className="demo-table statement-table"><thead><tr><th>Fecha</th><th>Concepto</th><th>Referencia</th><th>Canal</th><th>Cargo / abono</th><th>Saldo</th></tr></thead><tbody>{result.movements.map(tx => <tr key={tx.id}><td>{new Date(tx.date).toLocaleString('es-MX')}</td><td>{tx.detail || tx.type}</td><td><code>{tx.reference || '—'}</code></td><td>{tx.channel || '—'}</td><td className={debit.has(tx.type) ? 'negative' : 'positive'}>{debit.has(tx.type) ? '−' : '+'}{money(tx.amount)}</td><td>{money(tx.balanceAfter)}</td></tr>)}</tbody></table></div>{!result.movements.length && <p>No hay movimientos en este periodo.</p>}<div className="demo-pagination"><button className="btn btn-quiet" disabled={page === 0} onClick={() => setPage(page - 1)}>Anterior</button><span>Página {page + 1} de {Math.max(1, result.totalPages)}</span><button className="btn btn-quiet" disabled={page + 1 >= result.totalPages} onClick={() => setPage(page + 1)}>Siguiente</button></div></>}
  </section>;
}
