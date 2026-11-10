# Broker RabbitMQ — #77

RabbitMQ oficial 4.4.0-management, fijado por digest en `compose.sprint04-mq.yaml`.
El archivo base del núcleo no cambia ni depende de RabbitMQ. La ampliación exige opt-in
`--profile mq`; no se configura aún la conexión de los servicios Java, trabajo de
#78 y Courier#21. El ADR/topología de #76 sigue siendo el contrato compartido.

## Preparar y arrancar

```sh
python3 scripts/mq/prepare_broker.py /ruta/privada/nueva
# Preparar también el env privado del núcleo según docs/sprint04/COMPOSE.md.
docker compose --env-file /ruta/privada/core.env --env-file /ruta/privada/nueva/broker.env \
  -f compose.sprint04.yaml -f compose.sprint04-mq.yaml --profile mq up -d --wait --wait-timeout 480
```

El directorio debe estar vacío y fuera del repositorio. Se generan contraseñas aleatorias
independientes para lt_operator, lt_shipment y lt_courier, sin imprimirlas. Definitions
incluye hashes salados SHA-256 y también es privado: 0640 con grupo suplementario de
lectura del contenedor; credenciales/env 0600 y directorio 0700. No subir ninguno a Git.
Usar el mismo COMPOSE_PROJECT_NAME al combinar ambos env; el último env prevalece.

El proceso arranca como rabbitmq (sin root), conservando el grupo suplementario
para leer las definiciones 0640 también en Docker Linux nativo.
Management queda en 127.0.0.1:15672 (puerto configurable), AMQP no se publica al host.
Los servicios de la red Docker acceden por rabbitmq:5672 al vhost logitrack-s04.
lt_operator administra; usuarios de aplicación no pueden declarar/borrar recursos,
consumir la cola ajena ni publicar al exchange de resultados/comandos de otro servicio.
Se permite retry compartido según ADR; no se incluyen datos personales en sus mensajes.
No hay usuario guest ni vhost `/` inicial en un volumen nuevo importado con definiciones.

Seis colas quorum durables, cuatro exchanges direct y cuatro políticas DLX. Retry usa
TTL de 5 s; dead lettering at-least-once y overflow reject-publish evitan confirmar un
salto DLX que todavía no llegó al destino. Los reintentos de negocio siguen limitados a
3 por el consumidor futuro; el broker limita redeliveries a 10. Ningún DLQ se reinyecta
sin intervención. Un nodo y un volumen no representan alta disponibilidad.

La importación inicial no reemplaza usuarios/definiciones ya existentes: rotaciones y
cambios en un volumen previo requieren administración explícita y comprobación de
políticas. No regenerar credenciales para un broker existente esperando que cambien al
reiniciar. Nunca borrar el volumen para resolver una rotación o migración.

## Verificación real reproducible

```sh
python3 -m venv /tmp/logitrack-mq-client
/tmp/logitrack-mq-client/bin/pip install -r scripts/mq/requirements.txt
/tmp/logitrack-mq-client/bin/python scripts/mq/check_broker.py
```

En Docker Desktop, si su daemon no comparte el temporal del sistema, indicar
`--private-root /ruta/externa/compartida/con/docker`; no usar una carpeta del repo.
Se puede preparar el venv con uv si el equipo no tiene ensurepip.
El ensayo crea un proyecto único y credenciales propias, publica un mensaje persistente
con publisher confirm, reinicia conservando volumen, abre conexión nueva y lo consume.
Luego verifica retry diferido y rechazo a DLQ, tres permisos denegados, seis colas quorum,
políticas efectivas, ausencia de guest y exposición del Compose entregable solo en loopback.
AMQP se publica únicamente en un override temporal de prueba a un puerto local efímero.
El proyecto ficticio y sus volúmenes se eliminan en finally; no toca el núcleo existente.
La CI `Broker RabbitMQ Sprint04` ejecuta exactamente ese ensayo con Docker real.

Resultado manual en el laboratorio lt-s04-mq77: prepare 1 aserción/1 confirm;
verify después del reinicio 12 aserciones/1 confirm. Conteos de transporte, no pruebas
Java ni escenarios de asignación. El ensayo automatizado añade 4 verificaciones de
configuración efectiva. Una conexión nueva después de reiniciar no acredita por sí sola
reconexión automática de los futuros servicios: #80 comprobará caída/recuperación con
outbox y consumidores de #78/Courier#21/#79 implementados. Este reparto elimina la
circularidad de exigir consumidores Java en una issue que debe entregar primero el broker.

## Operación y límites

768 MiB de límite del contenedor; watermark absoluto 384 MiB y mínimo de disco libre
1 GiB. Ante alarmas, revisar capacidad y backlog: no desactivar las protecciones para
forzar publicación. El hostname/nodename fijo conserva la identidad al recrear con el
mismo volumen. Mantener nombres de proyecto distintos para instalaciones aisladas.
El broker no exige cambios en configserver-ms; conexión/credenciales y feature flag de
las aplicaciones se añaden externamente en las issues de integración, sin secretos en
configuración pública. No activar consumidores antes de aplicar sus migraciones.

Ver [importación oficial](https://www.rabbitmq.com/docs/definitions),
[permisos](https://www.rabbitmq.com/docs/access-control) y
[quorum queues](https://www.rabbitmq.com/docs/quorum-queues).
