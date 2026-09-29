# CEMA Bank — prototipo bancario local

Aplicación de demostración con Spring Boot, PostgreSQL, una web React y un cajero ATM React. **No es una entidad financiera. No recibe dinero real ni aprueba créditos automáticamente.** Los saldos, depósitos, retiros e inversiones son simulados. Las transferencias funcionan solo entre cuentas CEMA de esta instalación.

## Funciones

- Registro e inicio de sesión, con contraseñas cifradas y bloqueo temporal tras cinco intentos fallidos.
- Cuentas, saldo, historial, depósitos, retiros, transferencias internas, inversiones simuladas y solicitudes de crédito pendientes.
- PIN de cajero configurado por el cliente, almacenado como hash BCrypt.
- Cajero local: saldo, últimos movimientos, depósito y retiro simulados, cambio de PIN y comprobante imprimible.
- Sesiones con token aleatorio, hash en base de datos, vencimiento de 30 minutos y cierre de sesión. Los frontends conservan el token solo en memoria.
- Modo demo opcional: 15 perfiles de equipo y 100 clientes de tres niveles, con dos años de movimientos sintéticos.
- Estado de cuenta de prueba por periodo, con paginación, saldo inicial/final, fecha, referencia, canal y detalle de cargos/abonos.
- Roles diferenciados: administración y ejecutivos revisan créditos; analistas consultan sin decidir; clientes solo ven sus cuentas.

## Requisitos

Java 17+, Maven, Docker u Orbstack, Node.js y pnpm. No se usa npm. Las instalaciones de pnpm se mantienen dentro de `frontend/` y `frontend-atm/`, con scripts automáticos desactivados mediante `.npmrc`.

## Arranque local

1. Crea un archivo `.env` en la raíz con una contraseña propia para la base de datos:

   ```dotenv
   CEMA_DB_PASSWORD=elige_una_contrasena_larga_y_unica
   CEMA_DEMO_MODE=true
   ```

2. Desde la raíz, ejecuta `docker compose up -d`.
3. En otra terminal, exporta ambas variables de `.env` y ejecuta `mvn spring-boot:run`, o usa `./start_cema.sh`, que lee solo estas dos claves del archivo como texto.
4. En `frontend/`, ejecuta `pnpm install --ignore-scripts --frozen-lockfile` y `pnpm exec vite`. Abre `http://localhost:5173`.
5. En `frontend-atm/`, ejecuta `pnpm install --ignore-scripts --frozen-lockfile` y `pnpm exec vite`. Abre `http://localhost:5174`.

`sh start_cema.sh` (o `./start_cema.sh`) recompila el backend, espera a que los datos demo estén listos e inicia web y ATM en los puertos indicados. Si un puerto está ocupado, muestra el proceso que lo usa. Al salir detiene los tres servidores iniciados por el script; PostgreSQL queda disponible. El script no ejecuta el contenido de `.env`.

La base de datos escucha únicamente en `127.0.0.1:5433`. La API escucha por defecto en `127.0.0.1:8080`. La web y el cajero usan el proxy local de Vite para `/api`.

## Primer uso

1. Crea una cuenta desde la web. La contraseña debe tener entre 12 y 128 caracteres.
2. Inicia sesión y busca tu número de cuenta en el panel.
3. Configura un PIN de 4 a 6 dígitos en la operación **Configurar PIN**.
4. Abre el cajero y entra con el número de cuenta y el PIN.

Con `CEMA_DEMO_MODE=true`, el arranque crea la población ficticia **una sola vez por base de datos**. La marca `cema-demo-2026-v1` impide duplicarla en los siguientes arranques. La web muestra **Usuarios demo** con nombre, nivel, rol, usuario, contraseña, número de cuenta interno y PIN ATM. Los accesos web y ATM también tienen un buscador que muestra esas credenciales y rellena los campos al pulsar **Usar**. Las contraseñas y PIN se guardan solo como hash en la base de datos; el catálogo público vuelve a calcular las claves de prueba. El PIN mostrado es el inicial y deja de servir si el usuario lo cambia.

Para retirar el acceso rápido, cambia `CEMA_DEMO_MODE=false` en `.env`, reinicia el backend y recarga ambas interfaces. Se ocultan el catálogo y los buscadores, y los usuarios `demo_` dejan de poder iniciar sesión o usar tokens anteriores. Sus registros siguen en la base de prueba para conservar el historial; desactivar el modo no borra datos.

La población se reparte en 2 administradores, 6 ejecutivos, 7 analistas, 60 clientes Esencial, 30 Preferente y 10 Patrimonial. Todos tienen depósitos, pagos, retiros y transferencias internas simuladas, con saldo coherente durante 24 meses. Los niveles aplican límites por transferencia de prueba: $25,000, $100,000 y $250,000 MXN por operación respectivamente. Las cuentas del equipo usan el límite de $250,000. Estos límites son reglas del prototipo, no límites legales mexicanos.

## Seguridad y límites

- Los endpoints de dinero comprueban el propietario de la cuenta e importes válidos. Las transferencias bloquean las filas de ambas cuentas y registran los dos movimientos en una transacción.
- La API limita CORS a los orígenes locales configurados por `CEMA_CORS_ORIGINS`. No usa cookies ni autenticación básica. CSRF se desactiva porque la autenticación usa el encabezado `Authorization` y los clientes no guardan sesión en cookies.
- La contraseña de PostgreSQL se configura mediante variable de entorno. `.env`, `node_modules`, `dist` y `target` se ignoran en el control de versiones.
- El catálogo muestra credenciales administrativas intencionalmente **solo** en modo demo. Es inseguro publicar este modo en Internet. La clave de la base de datos local está en `.env` con permisos `600`.
- `spring.jpa.hibernate.ddl-auto=update` sirve para desarrollo. Para producción se necesitarían migraciones versionadas, HTTPS, un proveedor de identidad, controles antifraude, límites operativos, observabilidad y pruebas de penetración. Este prototipo no debe desplegarse como banco real.

## Verificación

- Backend: `mvn test`
- Web: `cd frontend && pnpm exec vite build && pnpm audit --audit-level low`
- ATM: `cd frontend-atm && pnpm exec vite build && pnpm audit --audit-level low`

## Información mexicana y alcance de la simulación

La vista de movimientos incluye datos informativos que ayudan a leer un estado de cuenta: titular, identificador de cuenta, moneda, periodo, saldos, cargos y abonos, fecha, concepto, canal y referencia. Se inspira en las guías de [CONDUSEF sobre estados de cuenta](https://www.condusef.gob.mx/index.php?idc=2822&idcat=3&p=contenido). Los identificadores de 16 dígitos son **internos y ficticios**, no CLABE ni cuentas SPEI; [Banxico explica que una CLABE real tiene 18 dígitos](https://www.banxico.org.mx/footer-es/preguntas-frecuentes-dudas-ba.html).

Los nombres son combinaciones ficticias. No se generan RFC, CURP, domicilios, documentos de identidad, teléfonos ni correo de personas reales. Esto reduce la exposición de datos, de acuerdo con el principio de minimización de la [Ley Federal de Protección de Datos Personales en Posesión de los Particulares](https://www.diputados.gob.mx/LeyesBiblio/pdf/LFPDPPP.pdf). **El prototipo no satisface los procesos de identificación de clientes, prevención de lavado de dinero, avisos de privacidad completos, reportes regulatorios ni emisión formal de estados de cuenta de una institución autorizada.** Requiere evaluación jurídica, controles operativos y auditoría independiente antes de cualquier uso real.

## Administración de créditos en la demo

Con modo demo activado, entra con una identidad del catálogo. Administración y ejecutivos pueden aprobar o rechazar una solicitud; el analista solo consulta. Los administradores además pueden cambiar roles, excepto el suyo, y consultar el registro de cambios. Una aprobación abona saldo simulado y registra el movimiento; una solicitud ya revisada no se puede procesar de nuevo.
