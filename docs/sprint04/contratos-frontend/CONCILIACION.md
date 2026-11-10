# Conciliación con el núcleo integrado — 10/11/2026

Este registro actualiza el contrato técnico de #71 con la implementación usada por
las pantallas. No cambia DTO, rutas, autorización, estado del dominio ni los ejemplos
estáticos originales. Los archivos `modelos.schema.json` y `escenarios.mock.json`
se conservan sin modificaciones: 25 definiciones y 25 escenarios, con 24 payloads
tipados válidos mediante JSON Schema 2020-12, FormatChecker y decimales exactos.
Esto verifica fixtures, no valida por sí mismo cada respuesta recibida del servidor.

## Versiones y procedencia

- Frontend: `ff9c7bc9ff93ba7a79b4e80602d7d1e2e51d4adb` de develop, integrado
  mediante PR #12 y #16–#21. Código de las pantallas/cliente idéntico al que aprobó
  188 pruebas en 17 archivos, lint y build; la comparación excluyó solo Docker/docs.
- Núcleo: Compose de shipment-service#72, integrado mediante PR #89 como
  `2f5ce6336a52d1f9e55927e225ed40f01dd4db30`. No modifica Java ni SQL de Sprint03.
- Imagen frontend real: `sha256:401ce967c222c8cbd27f40d85640110f00ee21752004ac7d039ac93b0ef41e2c`.
- Imágenes backend/MySQL y configuración realmente ejecutadas: `integracion-verificada.json`.
  Se registran identidades de imagen observadas; no se inventan build commits a partir
  del nombre de un tag. Los SHA de fuentes inspeccionadas están en la tabla del README.
- CORS: api-gateway PR #9 integrada como
  `2b8bb5c5780e9783f2b8c8e3e365337859a3468f`, código probado `62918f8`.
  Esta integración no publica una nueva imagen: el Compose de #72 usó Gateway Sprint03
  y mismo origen. El JAR CORS se probó por separado en frontend#9 y en sus 162 pruebas.

## Matriz de consumo y evidencia

Los prefijos y DTO del README coinciden con los adapters de las pantallas. Una operación
inspeccionada o probada con transporte simulado no se presenta como aceptación remota.

| Rol / acción | Contrato consumido | Evidencia efectiva |
|---|---|---|
| Público: registro y login | POST security/auth/register 201 Usuario; login 200 Token | Navegador real y regresión API por Nginx; login inválido 401 |
| A/Cl/R: identidad actual | GET security/auth/me 200 Usuario | Sesión real para los tres roles; respuesta vinculada a sub del JWT |
| ADMIN: cuenta y consulta de identidad | POST usuarios 201; GET usuarios/{id} 200 | Alta y GET de confirmación en navegador/API; ID de usuario separado de ficha |
| CLIENTE: alta y lectura de envío | POST envios 201; GET detalle/lista 200 | Alta, consulta posterior y persistencia después de recrear contenedores |
| CLIENTE ajeno: consulta | GET detalle y tracking | 403 y listado sin recursos ajenos; navegador cierra sesión ante 403 |
| ADMIN: asignación | PUT envios/{id}/repartidor 200 | PENDIENTE e historial de un evento; INACTIVO devuelve 422 sin mutación ni evento |
| REPARTIDOR: avance e historial | PATCH estado 200; GET historial 200 | PENDIENTE → EN_RUTA → ENTREGADO y tres eventos; lecturas posteriores CLIENTE |
| ADMIN: gestión Courier | POST 201; PUT edición 200; PATCH estado 200; GET 200 | Alta, edición, INACTIVO/ACTIVO y lecturas posteriores; edición persiste al recrear |
| ADMIN: vínculo Courier | PUT /usuario 200; GET /usuario 200 | Confirmación por lectura; rechazo de ficha ajena a otro REPARTIDOR |
| ADMIN: referencia Shipment | PUT asociaciones/repartidores/{id} 200 | Ambos IDs confirmados; mismo PUT idéntico repetido conserva asociación |
| REPARTIDOR: ficha/disponibilidad | GET propio 200 | Navegador/API; ACTIVO indica disponible, sin reservar capacidad |
| CLIENTE: acceso Courier | GET repertorio protegido | 403 real y menú oculto; guard probado en componentes |
| ADMIN: cancelación de envío | PATCH cancelacion sin cuerpo 200 | Contrato de fuente y pruebas de UI/mocks; no se añade aceptación real nueva aquí |
| ADMIN: vínculo opcional de cliente | PUT clientes/{id}/usuario 200 | Contrato de fuente y pruebas del coordinador/mocks; sin GET de asociación inventado |
| ADMIN: configuración diagnóstica | GET admin/configuracion | Contrato de fuente; no es una pantalla de negocio ni aceptación remota nueva |

Repartidor, usuario y cliente conservan UUID diferentes; no se infiere pertenencia por
correo. Courier puede estar vinculado sin referencia Shipment: no existe una transacción
que abarque ambos servicios. PUT idéntico de una referencia no acredita idempotencia de
POST ni de las transiciones. No se agrega DELETE, listado de usuarios/clientes, refresh,
logout remoto ni operación asíncrona inexistente.

## Pruebas y límites

Por Nginx, la regresión del frontend registró **8 escenarios / 38 solicitudes / 57
aserciones**. El verificador del Compose registró **17 solicitudes / 38 aserciones**
al preparar, y **7 solicitudes / 18 aserciones** tras recrear conservando volúmenes.
Son conteos separados de las 188 pruebas unitarias/de componentes y de los fixtures JSON.
La CI de PR #89 aprobó verificar (Java/Javadoc), migracion y modelo-compose.

Errores de transporte, respuesta perdida y concurrencia entre sesiones se inducen en las
pruebas del cliente/coordinador. No se afirma que se hayan inyectado todos esos fallos en
los servicios reales. La aceptación ampliada pendiente permanece en frontend#9.

## Actualización: ADR de sesión renovable adoptado

El equipo decidió el 10/11/2026 que la recarga debe recuperar la sesión sin guardar
JWT en Web Storage. La arquitectura y nuevas rutas están detalladas en ADR-001 del
README y en las cuatro PR de security-service#11. Esta decisión satisface la
interpretación más exigente del texto de persistencia de la consigna; no se atribuye
una aprobación individual del profesor.

Se probó con las imágenes multietapa finales, sin montajes de JAR: login, recarga
autenticada, tres pestañas con sesión, logout propagado a las otras dos y recarga
posterior anónima. API de renovación: 11 solicitudes y 17 aserciones. La regresión
Bearer de negocio conserva 8 escenarios, 38 solicitudes y 57 aserciones. Reaplicar
el SQL aditivo conservó las cuentas; copias SQL de ambos repositorios idénticas.
Pruebas: frontend 196; Security 198 Java; Gateway 165 Java; Javadoc/doclint aprobados.

La renovación expira de forma absoluta y rota con bloqueo por familia. Reutilizar
un token consumido revoca la familia; logout revoca renovación, sin prometer
revocación inmediata de JWT copiado. Se validan Origin y cabecera no simple para
CSRF. Cookie HttpOnly/Secure/Strict, HTTPS en despliegue y proxy del mismo origen.
No se habilitan cookies cross-origin ni reintentos de negocio tras 401/403.

Las versiones e imágenes reales están en el bloque `sesion_renovable` de
integracion-verificada.json. La identificación del núcleo anterior se conserva como
historia; no se atribuyen sus pruebas a imágenes nuevas por similitud. Los tags
nuevos son locales; publicación de digests y manifiesto de release permanece en #73.
