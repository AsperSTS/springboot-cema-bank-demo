# CEMA Bank

CEMA Bank es un prototipo local de banca digital que permite explorar el recorrido de un cliente y de un equipo bancario en una misma aplicación. Reúne una web, un cajero simulado y una API con datos persistentes. **No es un banco, no procesa dinero real y no cumple los requisitos para operar como institución financiera en México.**

El proyecto está en desarrollo y forma parte de mi portafolio. Muestra cómo abordo un dominio nuevo, conecto interfaces con un backend y pruebo reglas de acceso y operaciones. La población demo permite recorrer dos años de movimientos sintéticos sin usar datos de terceros.

## Funciones actuales

- Registro, inicio y cierre de sesión; roles de cliente, analista, ejecutivo y administrador.
- Saldo, movimientos, estado de cuenta de prueba, depósitos, retiros, transferencias internas, inversiones simuladas y solicitudes de crédito.
- Cajero simulado con acceso por cuenta y PIN, consulta, depósito, retiro y comprobante imprimible.
- Revisión de créditos por personal autorizado y registro de cambios de rol.
- Modo demo opcional con 15 perfiles de equipo, 100 clientes y credenciales ficticias consultables en la interfaz. Se carga una sola vez por base de datos.

## Stack

Java 17+, Spring Boot 3.5, Maven, PostgreSQL 15, React 19, Vite 8 y pnpm. Los frontends se instalan con scripts automáticos desactivados.

## Instalación y uso local

Requiere Java 17+, Maven, Docker u OrbStack, [Node.js 22.12+](https://vite.dev/guide/), pnpm, `curl` y `lsof`. Se verificó con pnpm 12.6.

1. Copia `.env.example` a `.env`. Asigna una contraseña local larga y única a `CEMA_DB_PASSWORD`. Cambia `CEMA_DEMO_MODE=true` si quieres las cuentas de demostración.
2. Ejecuta `pnpm install --ignore-scripts --frozen-lockfile` dentro de `frontend/` y de `frontend-atm/`. Los archivos `.npmrc` también desactivan scripts automáticos.
3. Desde la raíz, ejecuta `sh start_cema.sh`.
4. Abre la web en `http://127.0.0.1:5173` y el cajero en `http://127.0.0.1:5174`. La API usa `http://127.0.0.1:8080/api`. Pulsa Ctrl+C en la terminal del script para detener los tres servidores; PostgreSQL permanece activo.

En modo demo, la web muestra **Usuarios demo** y ambos accesos tienen un buscador de credenciales. Esas claves son públicas e intencionalmente ficticias. **Activa el modo demo solo en un entorno local aislado.** Para desactivarlo, cambia `CEMA_DEMO_MODE=false`, reinicia y recarga las interfaces. Las cuentas demo dejan de autenticar, aunque su historial permanece en la base local.

Para probar una cuenta nueva, regístrala en la web, inicia sesión y configura un PIN antes de entrar al cajero. Los importes y saldos siempre son simulados.

## Estructura

- `src/main/`: API, reglas de negocio, persistencia y datos demo.
- `src/test/`: pruebas de integración.
- `frontend/`: web para clientes y personal.
- `frontend-atm/`: interfaz del cajero simulado.
- `docker-compose.yml` y `start_cema.sh`: entorno local.
- `SECURITY_AUDIT.md`: revisión de seguridad fechada y límites conocidos.

## Verificación

Ejecuta `mvn test` en la raíz y `pnpm exec vite build` en cada frontend. Para revisar dependencias JavaScript, ejecuta `pnpm audit --audit-level low` en ambos directorios. Una auditoría pasada no garantiza ausencia de vulnerabilidades futuras.

## Límites y evolución

Las cuentas de 16 dígitos son identificadores internos, no CLABE; no hay SPEI, KYC, prevención de lavado, conciliación ni emisión de estados de cuenta oficiales. La base usa `ddl-auto=update` para desarrollo. [La auditoría](SECURITY_AUDIT.md) registra avisos pendientes en un paquete Spring y explica su alcance en este código. Antes de cualquier uso real se necesitarían migraciones, controles de fraude y abuso, protección de datos, seguridad operativa y revisión legal independiente. La siguiente etapa prevista es fortalecer esas bases y ampliar las pruebas de los flujos principales.

## Autor y licencia

Yael de los Santos · 2026 · [LinkedIn](https://www.linkedin.com/in/yaelsts) · Teléfono: 2205704385.

El código propio se distribuye bajo la [licencia MIT](LICENSE). Las dependencias y marcas de terceros conservan sus condiciones respectivas.
a# CEMA Bank

Local banking prototype built with Spring Boot, PostgreSQL and React.

It simulates common banking flows for learning and portfolio purposes. It is not a real financial institution and must not be used with real money or personal financial data.

## What it includes

- Registration and login with password hashing and temporary lockout after repeated failures.
- Customer accounts, balances and transaction history.
- Simulated deposits, withdrawals and internal transfers.
- Simulated investments and credit requests.
- Role-based access for administrators, executives, analysts and customers.
- Local ATM interface with PIN authentication.
- Optional demo population with synthetic users and transactions.

## Architecture

```text
React web client ─┐
                  ├─→ Spring Boot API ─→ PostgreSQL
React ATM client ─┘
```

The web and ATM clients run separately and consume the same backend API.

## Security decisions

- BCrypt for passwords and ATM PINs.
- Random session tokens stored as hashes in the database.
- Authorization checks on protected operations.
- Internal transfers executed inside a transaction with row locking.
- PostgreSQL credentials loaded from environment variables.
- CORS restricted to configured local origins.

This is still a prototype. A production financial system would require stronger identity, fraud controls, migrations, HTTPS, observability, regulatory processes and independent security review.

## Stack

Java 17+ · Spring Boot · PostgreSQL · React · Vite · Docker · Maven · pnpm

## Run locally

Create `.env` in the project root:

```dotenv
CEMA_DB_PASSWORD=choose_a_local_password
CEMA_DEMO_MODE=true
```

Start PostgreSQL:

```bash
docker compose up -d
```

Start the backend:

```bash
mvn spring-boot:run
```

Start the web client:

```bash
cd frontend
pnpm install --ignore-scripts --frozen-lockfile
pnpm exec vite
```

Start the ATM client:

```bash
cd frontend-atm
pnpm install --ignore-scripts --frozen-lockfile
pnpm exec vite
```

## Verification

```bash
mvn test
cd frontend && pnpm exec vite build
cd frontend-atm && pnpm exec vite build
```

## Demo mode

With `CEMA_DEMO_MODE=true`, the application creates synthetic users and transaction history for testing. Demo credentials are intentionally exposed only inside the local demo interface.

Do not expose demo mode to the public Internet.

## Scope

All account identifiers, balances and transactions are fictitious. The project does not implement real banking integrations, SPEI, CLABE issuance, KYC, AML or regulatory reporting.