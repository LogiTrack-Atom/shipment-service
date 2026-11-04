# Preparación del Compose integrado de Sprint03

Issue [#44](https://github.com/LogiTrack-Atom/shipment-service/issues/44).
La topología está preparada para seis aplicaciones y tres MySQL. El modelo se valida
sin iniciar contenedores. Su integración y cierre requieren completar las implementaciones,
publicar las imágenes y comprobar el arranque real con Eureka y Gateway.

## Archivos y componentes

`compose.sprint03.yaml` es el archivo candidato de Sprint03. El Compose y el ejemplo
de entorno históricos de Sprint02 permanecen disponibles para su regresión reproducible.
El nombre predeterminado del nuevo proyecto es `logitrack-sprint03`; no reutilizar el
proyecto ni los volúmenes del equipo para los ensayos.

| Componente | Puerto interno | Dependencias saludables para arrancar |
| --- | --- | --- |
| configserver | 8085 | Git de configuración accesible |
| eureka-server | 8065 | configserver |
| security-service | 8082 | configserver, eureka-server, security-mysql |
| courier-service | 8081 | configserver, eureka-server, courier-mysql, security-service |
| shipment-service | 8080 | configserver, eureka-server, shipment-mysql, security-service, courier-service |
| api-gateway | 8066 | configserver, eureka-server y los tres servicios de dominio |
| shipment-mysql | 3306, sin publicación | Volumen y esquema propios |
| courier-mysql | 3306, sin publicación | Volumen y esquema propios |
| security-mysql | 3306, sin publicación | Volumen y esquema propios |

Solo Gateway publica negocio en `127.0.0.1:8066`. Los clientes anuncian los DNS internos
de sus servicios y resuelven Eureka en `http://eureka-server:8065/eureka/`.
Config Server no es un cliente Eureka. El servidor autónomo no se autorregistra ni consulta
su registro; los cuatro clientes esperados son shipment, courier, security y Gateway.
`depends_on: service_healthy` ordena el arranque. La operación posterior sigue dependiendo
de los timeouts y la resiliencia de los clientes.

## Contrato con imágenes y configuración

Las seis imágenes de aplicación deben contener su usuario sin root y un HEALTHCHECK
funcional sobre `/actuator/health` en su puerto interno. Compose hereda ese comando y
ajusta sus intervalos; no supone rutas de clases del Dockerfile futuro de Eureka.
Una imagen sin sonda no satisface la aceptación de #44 aunque el modelo sea válido.

`CONFIG_LABEL` debe ser el SHA de cuarenta caracteres del repositorio configserver-ms
que incluya las configuraciones de Sprint03. Los cinco Config Client y el backend/sonda
de Config Server usan esa misma revisión. No se admite la rama develop ni la revisión
histórica de Sprint02 para esta entrega.

La configuración integrada en configserver-ms#13 consume `EUREKA_URL` bajo la clave
`eureka.client.service-url.defaultZone`, conservando camelCase en el nombre del mapa:

```yaml
eureka:
  client:
    service-url:
      defaultZone: ${EUREKA_URL:http://localhost:8065/eureka/}
```

Compose suministra EUREKA_INSTANCE_HOSTNAME y SERVER_PORT para anunciar destinos internos,
y EUREKA_INSTANCE_PREFER_IP_ADDRESS=false. JWT, rutas Gateway y marcadores por perfil
siguen correspondiendo al repositorio de configuración. La URL fija de courier en
shipment se conserva para verificaciones administrativas de identidad; la disponibilidad
Feign usa descubrimiento.

Copiar `.env.sprint03.example` a un archivo privado fuera de Git y completar:

- Seis referencias de imágenes publicadas con versión explícita o digest; sin latest.
  Los componentes modificados necesitan imágenes Sprint03. Config Server puede conservar
  su imagen publicada si su aplicación no cambia.
- CONFIG_LABEL verificado y perfil común dev o prod.
- Seis contraseñas distintas para aplicación/root de las tres bases.
- Archivos RSA externos y KEYS_GID con acceso de lectura. Solo security recibe la privada;
  security, courier, shipment y Gateway reciben el mismo archivo público.
- Bootstrap ADMIN externo solo durante el aprovisionamiento inicial; después deshabilitarlo
  y recrear security conservando su volumen.

Los archivos privados `.env.*` quedan excluidos de Git; los tres ejemplos siguen siendo
versionables. Los binds de claves son de solo lectura y no crean rutas ausentes.
La validación de modelo comprueba rutas y montajes declarados, no el contenido RSA ni los
permisos reales de los archivos.

## Validación del modelo

Desde shipment-service, con Docker Compose instalado; no requiere Docker Engine:

```bash
python3 scripts/compose/validate_sprint03.py --env-file /ruta/privada/sprint03.env
python3 scripts/compose/validate_sprint03.py --env-file /ruta/privada/sprint03.env --diagnostics
python3 scripts/compose/validate_sprint03.py --env-file /ruta/privada/sprint03.env --build
python3 -m unittest discover -s scripts/compose/tests -p 'test_*.py' -v
```

El script solo ejecuta `docker compose config --format json`, mantiene el modelo privado
en memoria e imprime un resumen sin secretos. Valida los modos base/build/diagnóstico y
sus contratos de aislamiento, referencias y arranque. Acepta los formatos normalizados
que omiten o conservan opciones booleanas false; el valor true de create_host_path sigue
rechazándose para las claves. `--build` comprueba el override,
sin construir imágenes ni verificar la existencia de los Dockerfiles de otros checkouts.

El workflow **Validación del modelo Compose Sprint03** usa referencias sintéticas
example.invalid únicamente para comprobar sintaxis y contratos: no descarga imágenes,
no inicia contenedores ni acredita nueve componentes saludables o cuatro registros reales.

## Comprobación previa de configuración remota

Con Config Server saludable y su diagnóstico publicado en loopback, comprobar las
quince combinaciones de cinco aplicaciones y perfiles default/dev/prod:

```bash
python3 scripts/compose/verify_remote_config.py \
  --config-url http://127.0.0.1:8085 \
  --config-label <SHA-de-configserver-ms> \
  --output target/sprint03-config-preflight.json
```

El comando solo hace GET, no sigue redirecciones y devuelve código 1 si encuentra
contratos pendientes. Comprueba revisión Git, precedencia de fuentes, marcadores,
puertos, configuración del servidor autónomo, placeholder Eureka, TTL JWT y las tres
rutas explícitas de Gateway con sus prefijos y StripPrefix. Mantiene los valores del
Environment en memoria; el informe solo incluye criterios y resultados.

La prueba HTTP cubre respuestas reales de Config Server. No acredita binding ni consumo
en los clientes, registro de las cuatro aplicaciones o funcionamiento de negocio.
La suite de `scripts/compose/tests` verifica además que una fuente de mayor precedencia
puede invalidar una ruta y que el informe no copia secretos ni nombres de fuentes.

El [ensayo de infraestructura del 03/11/2026](Integracion_previa.md) documenta la revisión
probada y los bloqueos encontrados antes de construir el conjunto completo.

## Ejecución, cuando estén listas las dependencias

Con imágenes ya publicadas y configuración verificada:

```bash
docker compose --env-file /ruta/privada/sprint03.env -f compose.sprint03.yaml pull
docker compose --env-file /ruta/privada/sprint03.env -f compose.sprint03.yaml up -d --wait --wait-timeout 600
docker compose --env-file /ruta/privada/sprint03.env -f compose.sprint03.yaml ps
```

Para diagnóstico deliberado, agregar `-f compose.sprint03.diagnostics.yaml` después del
archivo base. Publica Config Server, Eureka y los tres dominios exclusivamente en loopback;
MySQL continúa sin publicación. Usar las rutas Gateway para la demostración de negocio.

Para construcción local, agregar `-f compose.sprint03.build.yaml` y ejecutar `build`
con los seis repositorios hermanos y sus implementaciones/Dockerfiles ya disponibles.
El checkout de shipment debe incluir #41/#42 y los demás componentes sus dependencias
integradas. La construcción local no sustituye la aceptación con imágenes publicadas.

Para detener conservando datos:

```bash
docker compose --env-file /ruta/privada/sprint03.env -f compose.sprint03.yaml down
```

Los volúmenes son propios del proyecto y los SQL solo inicializan volúmenes nuevos.
No se incluye borrado automático ni se aplican migraciones a bases del equipo. El reset
de datos de #43 usa por ahora la infraestructura histórica fija; su conexión con esta
topología y el entorno final de Postman se completa tras la integración.

## Cierre de #44

Completar eureka-server#1, configserver-ms#13, courier-service#17, shipment-service#40,
api-gateway#2 y shipment-service#41/#42. Construir/publicar las imágenes modificadas,
registrar sus versiones/digests y la revisión de configuración. Entonces comprobar:

1. Nueve contenedores saludables y cuatro clientes registrados con destinos alcanzables.
2. Login, roles y flujo de negocio por Gateway con disponibilidad Feign real.
3. Recreación con persistencia de las tres bases y aislamiento de usuarios/volúmenes.
4. Datos reproducibles y rutas finales de Postman con las issues #43/#45/#46.

La aceptación integral se registra en #47. Esta preparación no cierra #44.

Referencias: [orden de arranque de Docker Compose](https://docs.docker.com/compose/how-tos/startup-order/)
y [healthcheck de servicios](https://docs.docker.com/reference/compose-file/services/#healthcheck).
