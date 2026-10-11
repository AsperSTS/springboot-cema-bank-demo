# Auditoría de seguridad — 28 septiembre 2026

Esta es una revisión histórica del entorno local en la fecha indicada. Sus resultados no certifican cumplimiento normativo ni describen necesariamente el estado actual de las dependencias. Vuelve a ejecutar las pruebas y auditorías antes de publicar o desplegar.

## Actualización del 11 de octubre de 2026

Una nueva ejecución de `pnpm audit --audit-level low` detectó un aviso alto en `source-map-js` 1.2.1, dependencia indirecta de Vite. Ambos frontends fijan ahora la versión 1.2.2, indicada como corregida en [GHSA-68fv-2mgg-jv7q](https://github.com/advisories/GHSA-68fv-2mgg-jv7q). Tras el cambio, las dos auditorías terminaron sin avisos conocidos.

Se volvió a consultar OSV para las 94 dependencias Maven resueltas. Jackson 2.21.6 tenía avisos de consumo excesivo de recursos; el BOM se actualizó a [2.21.7](https://github.com/advisories/GHSA-cxp5-3px4-pw24). La nueva consulta dejó **un paquete señalado**: `spring-webmvc` 6.2.19, por [CVE-2026-47890](https://spring.io/security/cve-2026-47890/) y [CVE-2026-47884](https://spring.io/security/cve-2026-47884/). La primera requiere SSE con fragmentos de vista y datos controlados por un atacante; la segunda requiere `XsltView` y una ruta comodín que renderice vistas. No se encontraron esas configuraciones en esta API REST, pero el paquete continúa en un rango afectado. Spring publica la corrección 6.2.20 solo para soporte empresarial; la corrección de código abierto está en Spring Framework 7.0.9. Una migración a la rama nueva requiere trabajo y pruebas aparte. No debe interpretarse esta evaluación de alcance como una garantía de seguridad para un despliegue público.

Las siete pruebas de integración pasaron tras retirar dos dependencias Java sin uso directo. La consulta OSV de septiembre que figura abajo es histórica; la revisión actual muestra los hallazgos anteriores.

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
