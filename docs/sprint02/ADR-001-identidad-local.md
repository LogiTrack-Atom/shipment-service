# ADR-001 — Pertenencia local de envíos y asociaciones inmutables

Estado: adoptada en shipment #25 y courier #15. Fecha: 2026-10-27.

## Contexto

El JWT identifica una cuenta mediante `sub`. Los UUID de cliente y repartidor son
identidades de dominio diferentes. Courier es propietario de `Repartidor.usuarioId`,
mientras shipment conserva únicamente `Envio.repartidorId`. Consultar courier en cada
lectura haría depender detalle, tracking, listados e historial de su disponibilidad.
Los clientes históricos no tienen una cuenta inferible de su email de contacto.

## Decisión

Shipment agrega `Cliente.usuarioId`, nullable y único, y una referencia administrativa
`repartidor_usuarios` con los UUID de repartidor/cuenta y fecha de comprobación. No se
replican datos de negocio del repartidor ni se agregan relaciones JPA o FK entre servicios.

ADMIN asocia un cliente existente mediante PUT `/api/v1/clientes/{id}/usuario` después
de verificar existencia y rol CLIENTE en security. CLIENTE puede crear un cliente nuevo
vinculado a su `sub` al registrar su primer envío. Un email preexistente nunca concede
propiedad: se rechaza con 409 y requiere revisión administrativa.

ADMIN asocia primero cuenta/repartidor en courier mediante su PUT de identidad. Luego
registra la referencia local con PUT `/api/v1/asociaciones/repartidores/{id}` en shipment.
Se verifica el rol en security y la asociación exacta mediante GET
`/api/v1/repartidores/{id}/usuario` en courier, exclusivamente ADMIN. Este GET es una
extensión del contrato inicial, no modifica el DTO público de dominio ni consulta security.
El null del proveedor indica que aún no existe una asociación y produce 409 en shipment.

Los GET remotos tienen timeouts finitos, sin redirecciones ni escrituras. Ocurren antes
de abrir la transacción local que guarda la asociación; no existe transacción distribuida.
Las restricciones únicas y la comprobación bajo bloqueo del cliente evitan sustituciones.
Repetir una asociación idéntica es idempotente y no requiere contactar proveedores.

## Condiciones y consecuencias

- Las APIs de courier y shipment no permiten sustituir/eliminar asociaciones. Esta
  inmutabilidad hace válida la referencia local sin TTL, sincronización periódica ni
  consultas remotas en lecturas. Una futura revinculación necesitará otro procedimiento
  coordinado; modificar una base manualmente sin ese procedimiento rompe la condición.
- Una cuenta puede estar asociada como máximo a un cliente y como máximo a un repartidor
  por servicio. Los roles del JWT siguen siendo obligatorios para usar cada asociación.
- CLIENTE consulta sus envíos; REPARTIDOR consulta sus asignaciones. Con varios roles se
  permite la unión de lecturas, pero cambiar estado exige además ser el repartidor asignado.
- Listados se filtran en SQL. Las consultas por UUID, tracking e historial también evalúan
  pertenencia. Un recurso ajeno o inexistente produce 403 para cuentas no administrativas,
  sin revelar su existencia; ADMIN conserva 404. Las rutas no declaradas se deniegan.
- No se consulta security para validar un JWT ni para leer envíos. Con proveedores caídos
  se siguen autorizando lecturas. Asignar o iniciar ruta requiere disponibilidad de courier;
  no se transforma esta referencia de identidad en una copia de su estado operativo.
- La migración 003 agrega una columna nullable y una tabla vacía. Conserva UUID, datos,
  auditoría e historial del Sprint01 y no adjudica recursos migrados automáticamente.
- Un 404 remoto indica recurso inexistente, 422 rol incompatible, 409 asociación incompatible,
  502 contrato inesperado y 503 proveedor no verificable. Ninguno guarda una asociación parcial.

## Verificación

Pruebas HTTP con JWT RS256 y MySQL real comprueban dos clientes y dos repartidores,
cuentas sin asociación, identidades de usuario/dominio diferentes, acceso alternativo por
tracking e historial, roles combinados, suplantación y rechazos sin cambios persistidos.
Los contratos administrativos se ensayan con proveedores HTTP controlados y mediante
los tres servicios reales antes de cerrar la integración.
