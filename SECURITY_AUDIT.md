# Auditoría de seguridad — 28 septiembre 2026

## Resultado verificable

- Se revisó el árbol Maven resuelto (98 dependencias, incluidas las de prueba) contra la API pública de OSV. Resultado final: **0 avisos conocidos** para las versiones resueltas en la fecha de revisión.
- `pnpm audit --audit-level low` terminó sin avisos conocidos en `frontend/` y `frontend-atm/`.
- `mvn test` cubre acceso sin sesión, propiedad de la cuenta, importes inválidos, transferencias, PIN cifrado, cierre de sesión, permisos por rol, carga demo única y estado de cuenta.
- `.npmrc` en ambos frontends establece `ignore-scripts=true`; se instalaron con `pnpm install --ignore-scripts`.

## Cambios aplicados

- Spring Boot pasó de 3.3.4 a 3.5.16. Se fijaron Jackson 2.21.6, Log4j API 2.25.5, Tomcat 10.1.60 y PostgreSQL JDBC 42.7.12 porque el árbol anterior tenía avisos en OSV. Tomcat 10.1.58, indicado como primera versión corregida en algunos avisos, no estaba disponible en Maven Central para el artefacto embebido; se usó 10.1.60.
- La población de prueba solo se crea con `CEMA_DEMO_MODE=true`. Su marca persistente evita duplicados tras reiniciar. La contraseña de PostgreSQL se aporta por variable de entorno o `.env` local sin ejecutarlo como script.
- Los tokens son aleatorios, se guardan como hash y vencen tras 30 minutos. Los navegadores solo los conservan en memoria.
- El PIN se guarda con BCrypt; un PIN antiguo en texto plano ya no autentica. Las respuestas de cuenta no incluyen PIN ni contraseña. El catálogo público de demo sí devuelve claves ficticias calculadas para la demostración y desaparece si se desactiva el modo demo.
- Las operaciones validan propietario e importe. Las transferencias y aprobaciones usan transacciones y bloqueo de filas.
- El API escucha por defecto solo en `127.0.0.1`. Los analistas no pueden aprobar créditos ni cambiar roles; el cambio de rol queda registrado con actor, usuario, rol anterior, nuevo rol y fecha.
- El buscador de claves de prueba en web y ATM depende de `CEMA_DEMO_MODE`. Con el modo apagado, el catálogo desaparece y se bloquean el inicio de sesión web, el acceso ATM y los tokens de los usuarios `demo_`. El prefijo queda reservado y no se admite en registros normales.

## Límites pendientes antes de cualquier uso real

Este repositorio sigue siendo un prototipo. No incluye verificación de identidad, cumplimiento regulatorio, antifraude, límites diarios, conciliación, reversos, auditoría inmutable, protección distribuida contra abuso, HTTPS de producción, migraciones versionadas ni prueba de penetración. `ddl-auto=update` es solo para desarrollo. Los depósitos y retiros crean o restan dinero simulado; no deben conectarse a fondos reales.

Si se reutiliza una base de datos antigua, hay que invalidar sesiones, borrar PIN heredados en texto plano y ejecutar una migración revisada antes de exponer el servicio. Las cuentas antiguas deben configurar un PIN nuevo desde la web.

## Fuentes y repetición

- [Seguridad de Spring](https://spring.io/security/)
- [OSV](https://osv.dev/)
- [Maven Central](https://central.sonatype.com/)

Comandos: `mvn test`, `mvn dependency:tree`, `pnpm audit --audit-level low` en cada frontend. OSV se consultó con `POST https://api.osv.dev/v1/querybatch` para cada PURL Maven resuelto.
