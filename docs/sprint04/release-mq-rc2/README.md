# Sprint04 MQ RC2

Esta revisión de RabbitMQ combina las imágenes de MQ RC1 con el Gateway
`sprint04-dns-rc2`. [El manifiesto](manifest.json) fija las referencias por
digest para `linux/amd64`, la configuración y las migraciones. La corrección
de Gateway está registrada en [Gateway #12](https://github.com/LogiTrack-Atom/api-gateway/issues/12).

## Arranque en un laboratorio local

1. Copiar `.env.sprint04-mq-rc2.example` como `private.env` a un directorio
   externo de modo 0700. Completar el nombre de proyecto `lt-s04-mq<identificador>`,
   credenciales ficticias, claves RSA y origen de loopback. Conservar los digests
   de la plantilla. Usar los archivos privados con modo 0600.
2. Ejecutar `python3 scripts/mq/prepare_broker.py "$private_dir/broker"`.
   Usar el mismo nombre de proyecto en `private.env` y `broker/broker.env`.
   Elegir puertos de loopback libres para la SPA y la gestión de RabbitMQ.
3. Desde la raíz de Shipment, definir el comando Compose en Bash. Incluir el
   último archivo únicamente para el ensayo de DNS. Usa la subred descartable
   `172.30.231.0/24`, que debe estar libre en el host.

```bash
compose=(docker compose --project-name "$project"
  --env-file "$private_dir/private.env"
  --env-file "$private_dir/broker/broker.env"
  --env-file "$private_dir/broker/application.env"
  -f compose.sprint04.yaml
  -f compose.sprint04-mq.yaml
  -f compose.sprint04-assignment.yaml
  -f scripts/mq/compose.dns-test.yaml
  --profile mq)
"${compose[@]}" config --quiet
"${compose[@]}" up -d --wait --wait-timeout 420
```

4. Aplicar las [tres migraciones](../release-mq/README.md#laboratorio-descartable)
   antes de habilitar el módulo. El SQL de Courier proviene de
   [su commit de build](https://github.com/LogiTrack-Atom/courier-service/blob/8a2f1e5ab97f5c81cfb071d39ee3ca1ceadb5a96/docker/mysql/mq/004-assignment-inbox-outbox.sql).
   Verificar los SHA-256 contra el manifiesto. Fijar `ASSIGNMENT_ENABLED=true` en
   `broker/application.env` y ejecutar:

```bash
"${compose[@]}" up -d --no-deps --wait shipment-service courier-service
```

5. En la SPA, entrar como ADMIN, crear o elegir un envío y un repartidor activo,
   solicitar una asignación y comprobar el estado Confirmado en el detalle.

## Ensayo integrado

Ejecutar únicamente con datos ficticios. El script detiene servicios, crea un
trigger SQL temporal y, con la opción DNS, recrea Courier y Shipment con IP
nuevas. Se comprueba que ambas lecturas recuperen HTTP 200 con los datos
esperados y que Gateway conserve su contenedor y su hora de inicio.

```bash
python3 scripts/mq/acceptance.py --project "$project" --private-dir "$private_dir" \
  --check-address-change
```

Usar un laboratorio nuevo para repetir el cambio de IP. Para ejecutar solo los
escenarios estándar, omitir el overlay al arrancar y la opción
`--check-address-change`. Al finalizar, retirar exclusivamente el laboratorio
descartable mediante `"${compose[@]}" down --volumes` y borrar sus archivos privados.
