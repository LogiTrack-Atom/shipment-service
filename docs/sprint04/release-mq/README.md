# Aceptación de la ampliación RabbitMQ — #80

La ampliación es opt-in. Conserva el núcleo P0 `sprint04-rc1`, sus imágenes, las ramas y
las bases existentes. Este directorio identifica el release MQ aceptado y su evidencia.
`published.json` fija las cuatro imágenes públicas y los servicios reutilizados por digest.

## Laboratorio descartable

Usar un proyecto nuevo `lt-s04-mq<identificador>` y datos ficticios. No ejecutar el ensayo
sobre una instalación con información que deba conservarse: detiene broker/Courier y
crea/elimina un trigger SQL temporal. Requiere Docker y Python 3 estándar, sin pip.

1. Preparar un directorio externo 0700 compartido con Docker Desktop. Crear `private.env`
   según `.env.sprint04.example`, con contraseñas aleatorias, claves RSA externas 0640 y
   `KEYS_GID` correspondiente. Usar puerto frontend de loopback libre y SESSION_ORIGIN
   exacto. Conservar las imágenes P0 para Config/Eureka/Security y usar los candidatos
   locales de `local-images.json` para Shipment/Courier/Gateway.
2. Ejecutar `python3 scripts/mq/prepare_broker.py /ruta/privada/broker`. En `private.env`
   y `broker/broker.env`, fijar el mismo COMPOSE_PROJECT_NAME del laboratorio; elegir
   RABBITMQ_MANAGEMENT_PORT de loopback libre. Mantener archivos env/credenciales 0600.
3. Combinar los tres env (private, broker, application) y los tres Compose
   (`compose.sprint04.yaml`, `compose.sprint04-mq.yaml`, `compose.sprint04-assignment.yaml`),
   con `--profile mq up -d --wait --wait-timeout 360`. El módulo sigue apagado inicialmente.
4. Con MySQL saludable, aplicar una sola vez Shipment
   `docker/mysql/mq/004-assignment-outbox.sql` y `005-assignment-results-inbox.sql`; en
   Courier aplicar su `docker/mysql/mq/004-assignment-inbox-outbox.sql` desde el commit
   identificado. Usar `compose exec -T <servicio>-mysql sh -c
   'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --user="$MYSQL_USER" --database="$MYSQL_DATABASE"'`
   con redirección del archivo SQL. No imprimir los env ni pasar contraseñas en argumentos.
   Estas migraciones se aplican a las bases nuevas del laboratorio; una actualización de
   datos reales requiere la copia/backup y comprobaciones de los procedimientos 004/005.
5. Activar ASSIGNMENT_ENABLED=true en application.env y recrear únicamente Shipment y
   Courier con `up -d --no-deps --wait`. No regenerar usuarios de un broker existente.
6. Ejecutar desde la raíz del repositorio:

```sh
python3 scripts/mq/acceptance.py --project lt-s04-mq80 --private-dir /ruta/privada
```

El ensayo usa login por Nginx/Gateway, usuarios de aplicación restringidos y el operador
solo para inspeccionar/inyectar mensajes. La inyección de fallo SQL usa root únicamente
para CREATE/DROP TRIGGER, sin conceder privilegios nuevos a los servicios. `finally`
retira el trigger y vuelve a arrancar los servicios detenidos. No borra volúmenes:
al terminar, eliminar solo el proyecto de laboratorio identificado y sus archivos privados.

## Evidencia de backend

`backend-evidence.json`: 9 escenarios aprobados, 138 solicitudes HTTP, 181 aserciones,
11 servicios reales, MySQL y RabbitMQ; estos números no son casos unitarios Java.
Las repeticiones de transporte usan los bytes originales de la columna JSON del outbox,
no una serialización distinta que activaría la protección ante alteración del payload.

Incluye caída del broker con 202 y outbox conservado, recuperación automática sin reiniciar
apps, Courier detenido, duplicados en ambos sentidos, negativas, cancelación/reemplazo,
inválido a DLQ, fallo SQL con tres retries y DLQ, reparación y replay explícito, Feign/PUT
y reinicio de consumidores después del ACK con conteos/efectos conservados.

Courier puede consumir AMQP antes de reaparecer en los registros/cachés de descubrimiento.
El ensayo espera health y una ventana acotada de 75 s antes de probar una única escritura
Feign. No reintenta automáticamente el PUT. Un 503 aborta el ensayo y no se declara éxito.

El corte TCP después del commit y antes del ACK está probado en
[Courier PR24](https://github.com/LogiTrack-Atom/courier-service/pull/24),
`AssignmentDeliveryIT.lostAckRedeliversWithoutDuplicateAndConsumerRecoversAfterRestart`,
CI [42863525200](https://github.com/LogiTrack-Atom/courier-service/actions/runs/42863525200).
Allí se aborta la conexión nativa, se recibe redelivery y se comprueba un solo efecto SQL.
El ensayo conjunto no presenta una interrupción distinta como si repitiera ese corte.
Las imágenes locales conservan los mismos inputs de runtime de los commits identificados.

## Integración y límites

Frontend #10 llegó mediante [PR25](https://github.com/LogiTrack-Atom/logitrack-frontend/pull/25)
de DiazGab. Su integración real y los ajustes de 202/503/contraste con el envío se verifican
como continuación de esa implementación; no se reemplaza su trabajo por otra interfaz.
El release final de #80 requiere también esa aceptación UI y sus imágenes/manifiesto.

At-least-once con efecto idempotente local; sin exactly-once, reserva exclusiva ni alta
disponibilidad. Un nodo RabbitMQ con volumen persistente sigue siendo un punto único de falla.
No incluye despliegue público ni Minikube. La ampliación no bloquea la entrega del núcleo P0.

## UI y candidato listo para publicar

`ui-evidence.json` registra la prueba manual real de la continuación de PR25 mediante
[PR26](https://github.com/LogiTrack-Atom/logitrack-frontend/pull/26): solicitado→confirmado
con GET operación/envío; broker detenido→demorado y consulta manual con una sola operación
PENDING en SQL; logout real durante el seguimiento vuelve al login. Las pruebas de componente
cubren además abortar y descartar APPLIED tardío y conservar la clave ante 503.

`candidate.json` fija cuatro image IDs locales, commits fuente, argumentos públicos de
construcción, configuración y SHA-256 de migraciones. No contiene secretos. El frontend
se construye con VITE_USE_MOCKS=false y VITE_ASYNC_ASSIGNMENT=true, manteniendo P0 intacto.

Después de autorizar la publicación de las cuatro imágenes verificadas en el namespace:

```sh
python3 scripts/release/publish_mq.py --candidate docs/sprint04/release-mq/candidate.json \
  --namespace maxdz10 --tag sprint04-mq-rc1 --output /ruta/salida/published.json
```

El publicador exige aceptación backend/UI y compara las cuatro imágenes antes del primer
push. Rechaza tags P0 y cualquier tag MQ existente de contenido diferente. Conserva digests
de las imágenes publicadas; publicar no declara por sí solo aceptación del release:
restan pull público y recreación/smoke por referencias inmutables. Sus guardas se ejecutan
junto con las del publicador P0 en la CI.

## Release publicado y reproducido — 10/11/2026

Versión `sprint04-mq-rc1`, namespace `maxdz10`. Las cuatro imágenes se descargaron sin
credenciales y sus image IDs coinciden con los candidatos aceptados (`public-pull.json`).
El laboratorio se recreó usando sus referencias por digest: once servicios saludables,
conteos inbox/outbox preservados y smoke del núcleo prepare 17 solicitudes/38 aserciones,
verify 7/18; smoke MQ 27 solicitudes HTTP/26 aserciones (incluye esperas de lectura de
descubrimiento), nueva operación APPLIED, replay idempotente e historial sin eventos extra.
Son conteos de ensayo, no 26 casos unitarios. No se afirma reproducción por otro integrante.

Copiar `.env.sprint04-mq.example` fuera de Git como private.env y completar únicamente
credenciales/claves/origen. Reutilizar la preparación y las migraciones detalladas arriba;
no sustituir estas imágenes por P0 para activar MQ. `published.json` es la referencia de
la ampliación; `docs/sprint04/release/published.json` sigue identificando el núcleo P0.
La promoción a main permanece a cargo de la coordinación del equipo, sin borrar ramas.
