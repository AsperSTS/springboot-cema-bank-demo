import { useState } from 'react';

export default function CredentialFinder({ users, onChoose }) {
  const [query, setQuery] = useState('');
  const matches = users.filter(user => `${user.name} ${user.username} ${user.accountNumber}`
    .toLocaleLowerCase('es-MX').includes(query.trim().toLocaleLowerCase('es-MX'))).slice(0, 6);

  return <section className="credential-finder" aria-label="Buscar credenciales de demostración">
    <span className="eyebrow">ACCESO RÁPIDO · DEMO</span>
    <label>Busca un usuario de prueba<input type="search" value={query} onChange={event => setQuery(event.target.value)} placeholder="Nombre o usuario" autoComplete="off"/></label>
    <div className="credential-results">{matches.map(user => <div className="credential-result" key={user.username}>
      <div><b>{user.name}</b><small>{user.username} · {user.tier}</small><small>Contraseña: <code>{user.password}</code></small><small>Cuenta: <code>{user.accountNumber}</code> · PIN inicial: <code>{user.pin}</code></small></div>
      <button type="button" onClick={() => onChoose(user)}>Usar</button>
    </div>)}{!matches.length && <p>Sin resultados. Prueba con otro nombre o usuario.</p>}</div>
    <small className="credential-note">Solo para esta demo local. El PIN mostrado es el inicial; si se cambió, usa el nuevo.</small>
  </section>;
}
