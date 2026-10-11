#!/bin/sh
set -eu
cd "$(dirname "$0")"

# Lee solo dos claves; nunca ejecuta el archivo .env.
if [ -f .env ]; then
  while IFS='=' read -r key value || [ -n "$key" ]; do
    case "$key" in
      CEMA_DB_PASSWORD) if [ -z "${CEMA_DB_PASSWORD:-}" ]; then CEMA_DB_PASSWORD=$value; export CEMA_DB_PASSWORD; fi ;;
      CEMA_DEMO_MODE) if [ -z "${CEMA_DEMO_MODE:-}" ]; then CEMA_DEMO_MODE=$value; export CEMA_DEMO_MODE; fi ;;
    esac
  done < .env
fi
if [ -z "${CEMA_DB_PASSWORD:-}" ]; then
  echo 'Configura CEMA_DB_PASSWORD en .env o en el entorno antes de iniciar.' >&2
  exit 1
fi
if ! command -v lsof >/dev/null 2>&1; then
  echo 'Falta lsof, necesario para comprobar los puertos antes de iniciar.' >&2
  exit 1
fi

# Evita mostrar una dirección que en realidad usa otro servidor.
for port in 8080 5173 5174; do
  if lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then
    echo "El puerto $port ya está ocupado. Cierra ese servidor y vuelve a ejecutar el script." >&2
    lsof -nP -iTCP:"$port" -sTCP:LISTEN >&2
    exit 1
  fi
done

BACKEND_PID=
WEB_PID=
ATM_PID=
cleanup() {
  for pid in "$ATM_PID" "$WEB_PID" "$BACKEND_PID"; do
    if [ -n "$pid" ]; then kill "$pid" 2>/dev/null || true; fi
  done
}
trap cleanup EXIT
trap 'exit 0' INT TERM

echo 'Preparando backend con los cambios actuales…'
if ! mvn -q -DskipTests package > backend-build.log 2>&1; then
  tail -n 30 backend-build.log >&2
  exit 1
fi
docker compose up -d
if [ -n "${JAVA_HOME:-}" ]; then
  JAVA_BIN=$JAVA_HOME/bin/java
else
  MAVEN_JAVA_HOME=$(mvn -v | sed -n 's/^Java version:.*runtime: //p' | head -n 1)
  JAVA_BIN=$MAVEN_JAVA_HOME/bin/java
fi
if [ ! -x "$JAVA_BIN" ]; then
  echo 'Maven no informó una instalación de Java utilizable.' >&2
  exit 1
fi
"$JAVA_BIN" -jar target/cema-bank-0.0.1-SNAPSHOT.jar > backend.log 2>&1 &
BACKEND_PID=$!

echo 'Esperando a que el backend y los datos demo estén listos…'
attempt=0
while [ "$attempt" -lt 180 ]; do
  if ! kill -0 "$BACKEND_PID" 2>/dev/null; then
    echo 'El backend se detuvo durante el arranque:' >&2
    tail -n 30 backend.log >&2
    exit 1
  fi
  if [ "${CEMA_DEMO_MODE:-false}" = true ]; then
    status=$(curl -s -o /dev/null -w '%{http_code}' --max-time 2 http://127.0.0.1:8080/api/demo/catalog || true)
    if [ "$status" = 200 ]; then break; fi
  else
    status=$(curl -s -o /dev/null -w '%{http_code}' --max-time 2 http://127.0.0.1:8080/api/auth/me || true)
    if [ "$status" = 401 ]; then break; fi
  fi
  attempt=$((attempt + 1))
  sleep 1
done
if [ "$attempt" -ge 180 ]; then
  echo 'El backend no estuvo listo a tiempo. Revisa backend.log.' >&2
  exit 1
fi

(cd frontend && exec ./node_modules/.bin/vite --host 127.0.0.1) > frontend.log 2>&1 &
WEB_PID=$!
(cd frontend-atm && exec ./node_modules/.bin/vite --host 127.0.0.1) > frontend-atm.log 2>&1 &
ATM_PID=$!

for spec in "5173:$WEB_PID:frontend.log" "5174:$ATM_PID:frontend-atm.log"; do
  port=${spec%%:*}
  rest=${spec#*:}
  pid=${rest%%:*}
  log=${rest#*:}
  attempt=0
  while [ "$attempt" -lt 30 ]; do
    if ! kill -0 "$pid" 2>/dev/null; then
      echo "El servidor del puerto $port se detuvo:" >&2
      tail -n 30 "$log" >&2
      exit 1
    fi
    status=$(curl -s -o /dev/null -w '%{http_code}' --max-time 2 "http://127.0.0.1:$port/" || true)
    if [ "$status" = 200 ]; then break; fi
    attempt=$((attempt + 1))
    sleep 1
  done
  if [ "$attempt" -ge 30 ]; then
    echo "El servidor del puerto $port no estuvo listo. Revisa $log." >&2
    exit 1
  fi
done

echo 'Listo: Web http://127.0.0.1:5173 | ATM http://127.0.0.1:5174 | API http://127.0.0.1:8080/api'
echo 'Pulsa Ctrl+C para detener web, ATM y backend.'
while :; do
  for pid in "$BACKEND_PID" "$WEB_PID" "$ATM_PID"; do
    if ! kill -0 "$pid" 2>/dev/null; then
      echo 'Un servidor se detuvo. Revisa los archivos .log del proyecto.' >&2
      exit 1
    fi
  done
  sleep 2
done
