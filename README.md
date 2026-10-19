# shipment-service
Microservicio de gestión y seguimiento de envíos de LogiTrack Atom - Sprint 01 (DAAS).

## Base de datos y esquema inicial

Requisitos: Docker con Compose. Para ejecutar Maven fuera de Docker, Java 25.

```sh
cp .env.example .env
# Completar las contraseñas en .env antes de iniciar.
docker compose up --build -d
docker compose logs -f shipment-service
```

La API queda en `http://localhost:8080` y MySQL en `localhost:3307`, salvo cambios
en `APP_PORT` y `MYSQL_HOST_PORT`. La aplicación usa el usuario `MYSQL_USER`.
Las credenciales de `.env` son locales y no se suben a Git.

`docker/mysql/init/001-schema.sql` es el esquema inicial del Sprint 01. Compose
lo monta en `/docker-entrypoint-initdb.d`, y la imagen MySQL lo ejecuta sobre la
base indicada por `MYSQL_DATABASE` **solo cuando inicializa almacenamiento nuevo**.
No contiene `CREATE DATABASE`, usuarios ni credenciales; tampoco borra tablas.

Hibernate usa `ddl-auto=validate` tanto dentro como fuera de Compose: verifica
el mapeo sin crear ni modificar tablas. Si tu `.env` anterior tiene
`JPA_DDL_AUTO=update`, cambiarlo a `JPA_DDL_AUTO=validate`. No usar `update`,
`create` o `create-drop` para verificar este SQL, porque podrían alterar el
esquema y ocultar diferencias. Para cambios posteriores se necesita una
migración SQL explícita y coordinada con los cambios en JPA.

### Contrato del esquema

- Cuatro tablas InnoDB: `clientes`, `repartidores`, `envios` e `historial_estados_envio`.
- UUID como `BINARY(16)` en PK y FK; generados por JPA.
- Auditoría `created_at`, `updated_at` y `fecha_cambio` como `DATETIME(6)`;
  escrita por la aplicación, con conexión en UTC.
- Cadenas `VARCHAR(255)` y estados `ENUM`, acordes al mapeo Hibernate/MySQL.
- Peso `DECIMAL(10,3)` y dimensiones `DECIMAL(10,2)`, según la validación de #8.
- Email y código de seguimiento únicos; tres FKs sin borrado en cascada.
- `telefono`, `repartidor_id` y `estado_anterior` admiten nulos; el resto de
  las columnas es obligatorio. Las direcciones se guardan en columnas embebidas.
- Colación `utf8mb4_0900_ai_ci`, igual al esquema generado por Hibernate en
  MySQL 8.4: las comparaciones de texto, incluida la unicidad, ignoran mayúsculas
  y acentos.

### Verificación y almacenamiento existente

Para consultar el esquema, ingresar la contraseña de `MYSQL_USER` cuando se solicite:

```sh
docker compose exec mysql sh -c 'exec mysql -u"$MYSQL_USER" -p "$MYSQL_DATABASE"'
```

```sql
SHOW TABLES;
SHOW CREATE TABLE envios;
SHOW CREATE TABLE historial_estados_envio;
```

Para comprobar el primer arranque sin tocar datos del equipo, se puede levantar
solo una MySQL temporal con puertos y almacenamiento independientes. Desde la
raíz del repositorio, elegir un nombre que no exista:

```sh
docker run --detach --name shipment-schema-check --env-file .env \
  --publish 127.0.0.1::3306 \
  --mount type=volume,source=shipment_schema_check_data,target=/var/lib/mysql \
  --mount "type=bind,source=$(pwd)/docker/mysql/init,target=/docker-entrypoint-initdb.d,readonly" \
  mysql:8.4
docker logs -f shipment-schema-check
# Esperar a que finalice la inicialización y el servidor esté listo.
docker port shipment-schema-check 3306/tcp
```

Para comprobar el arranque contra esa instancia manual, usar el puerto que devuelve
Docker y las credenciales de `.env`:

```sh
DB_URL='jdbc:mysql://127.0.0.1:<puerto>/shipment_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC' \
DB_USERNAME='<MYSQL_USER>' DB_PASSWORD='<MYSQL_PASSWORD>' JPA_DDL_AUTO=validate \
sh ./mvnw spring-boot:run
```

Si `MYSQL_DATABASE` es distinto de `shipment_db`, ajustar también la URL. Al
terminar, detener la instancia temporal con `docker stop shipment-schema-check`.
El contenedor y su volumen de prueba quedan separados de los de Compose.

Con almacenamiento existente, reiniciar MySQL **no vuelve a ejecutar** el SQL.
Si esa base fue creada mediante `update`, respaldarla y comparar su esquema
antes de aplicar cambios. Si falta una tabla o existe una diferencia incompatible,
`validate` debe impedir el arranque; no borrar el volumen del equipo ni activar
`update` para evitar el error.

Referencia: [inicialización de la imagen oficial de MySQL](https://hub.docker.com/_/mysql).


## Pruebas unitarias y de integración

Desde la raíz del repositorio, con Java 25:

```sh
# Surefire: pruebas *Test / *Tests, sin Docker ni base de datos.
sh ./mvnw test

# Surefire + Failsafe: todas las pruebas, incluidas las *IT contra MySQL real.
sh ./mvnw clean verify
```

La suite completa requiere Docker disponible (`docker info`) y acceso a las
imágenes `mysql:8.4` y al recolector Ryuk de Testcontainers. La primera ejecución
puede demorar mientras las descarga. Si Docker no está disponible, las
integraciones fallan; no se omiten silenciosamente. No hace falta levantar
Compose, crear `.env`, configurar DB_URL ni preparar una base manual.

`MySqlIntegrationSupport` inicia una única MySQL temporal por JVM de Failsafe,
con puerto aleatorio, credenciales de prueba y base `shipment_test`. Copia el
SQL de `docker/mysql/init/001-schema.sql` al entrypoint, sin duplicarlo entre los
recursos de tests, y arranca Spring con `ddl-auto=validate`. Las propiedades
dinámicas reemplazan cualquier conexión externa de desarrollo. No reutiliza
contenedores ni volúmenes de Compose; Ryuk elimina los recursos al terminar.

Las integraciones corren secuencialmente y limpian solo esa base antes de cada
caso, respetando el orden de las FKs. No llevan `@Transactional` en la clase ni
en los métodos: las llamadas al servicio realizan sus commits reales.

- `ShipmentPersistenceIT`: UUID, auditoría, relaciones y direcciones después de
  flush/clear; consultas por UUID/tracking/estado; unicidad, FKs y orden cronológico.
- `ShipmentTransactionIT`: un trigger temporal de MySQL rechaza el INSERT del
  historial. Se verifica el error real y se consulta desde una transacción nueva
  que no se modificaron estado/historial ni quedaron altas parciales. El trigger
  se crea con root dentro del contenedor de prueba y se elimina en finally;
  la aplicación mantiene su usuario habitual de la base temporal.
- `ShipmentApiIT`: MockMvc con el contexto real de Spring, sin mocks de servicios
  o repositorios; flujo de entrega y cancelación, errores 400/404/422/409 y regresión
  decimal de #8 con comprobación de filas/valores en MySQL. El caso 409 provoca
  una violación real de longitud de columna, no un conflicto simulado de email
  (el servicio reutiliza clientes existentes por email).

El antiguo `contextLoads` queda cubierto por el arranque real de estas clases
`@SpringBootTest`; ya no obliga a conectar con MySQL durante `mvn test`.
Los informes XML quedan en `target/surefire-reports` y `target/failsafe-reports`.
Para diagnosticar solo el rollback se puede ejecutar
`sh ./mvnw -Dit.test=ShipmentTransactionIT verify`; esa ejecución selectiva no
reemplaza `clean verify` para validar una entrega.

Este entorno comprueba la aplicación con MySQL y el SQL versionado. La prueba
de despliegue completo de Compose se realiza además al preparar la entrega.

Referencias: [MySQL en Testcontainers](https://java.testcontainers.org/modules/databases/mysql/)
y [Maven Failsafe](https://maven.apache.org/surefire/maven-failsafe-plugin/).

## Documentación de API y trazas

Con la aplicación iniciada (puerto predeterminado 8080):

- Swagger UI: <http://localhost:8080/swagger-ui/index.html>.
- Especificación OpenAPI: <http://localhost:8080/v3/api-docs>.
- Contratos Java: `ShipmentService` y `RepartidorService` describen precondiciones,
  excepciones, consultas y efectos transaccionales.

OpenAPI documenta las 11 operaciones, sus respuestas exitosas y los errores
400/404/422/409 pertinentes mediante `ApiError` y ejemplos. Los esquemas de
solicitud y respuesta explican kg/cm, límites y pasos decimales de #8. Un 409
corresponde a una restricción real de datos; un email existente se reutiliza
sin sobrescribir al cliente. Las transiciones permitidas son
PENDIENTE → EN_RUTA/CANCELADO y EN_RUTA → ENTREGADO. EN_RUTA requiere un repartidor
asignado y ACTIVO; ENTREGADO/CANCELADO son terminales. Asignar no cambia el estado
ni agrega un evento de historial; cancelar solo admite PENDIENTE.

Los logs INFO de `BusinessLog` registran UUID y estados después del commit:
registro de envío/repartidor, asignación y cambio de estado (incluida cancelación).
Si hay rollback, no se imprime un éxito. Se capturan valores inmutables, no
entidades JPA, y no se registran cuerpos, nombres, emails, teléfonos, direcciones
ni credenciales. Los servicios usan WARN para rechazos de reglas de negocio y
DEBUG para consultas por estado. Para habilitar estas últimas sin activar SQL
ni cuerpos HTTP, establecer
`LOGGING_LEVEL_COM_LOGITRACK_ATOM_SHIPMENT_SERVICE_SERVICE_IMPL=DEBUG`.

`OpenApiIT` verifica las operaciones, respuestas, referencias y ejemplos de
error, precisión y recursos de Swagger UI. `BusinessLogIT` comprueba que los
éxitos solo aparecen tras confirmar una transacción y no exponen los datos de
las solicitudes. Se ejecutan con `sh ./mvnw clean verify`, junto con las pruebas
de persistencia, API y rollback de #10.
