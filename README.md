# CEMA Bank

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
