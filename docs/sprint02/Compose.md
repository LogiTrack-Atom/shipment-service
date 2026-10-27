# Arranque integrado del Sprint02

## Componentes y dependencias

| Servicio | Imagen / responsabilidad | Dependencias para arrancar |
| --- | --- | --- |
| configserver | Configuración desde Git; sin MySQL ni Config Client | Backend Git accesible |
| security-service | Usuarios y JWT; security_db/security_app | configserver y security-mysql saludables |
| courier-service | Repartidores; courier_db/courier_app | configserver, courier-mysql y security-service saludables |
| shipment-service | Envíos; shipment_db/shipment_app | configserver, shipment-mysql, security-service y courier-service saludables |

No hay un contenedor configserver-ms: es el repositorio Git consultado por Config Server.
Las aplicaciones usan el commit fijo indicado por CONFIG_LABEL. Config Server conserva su
rama inicial develop para clonar; cada cliente solicita explícitamente el commit de entrega.
Los tres volúmenes son independientes. Los SQL de courier/security se distribuyen como copias
exactas con commit y SHA-256 en docker/mysql/provider-schemas.json, para que el Compose de
entrega funcione sin clonar sus repositorios. Se actualizan explícitamente si cambia un esquema.

## Requisitos y archivos privados

Docker Engine/Compose disponibles. Copiar .env.example a un archivo privado fuera de Git y
completar los cuatro nombres de imágenes con etiquetas publicadas explícitas, las seis
contraseñas y rutas de archivos RSA externos. Evitar latest. Los valores de imágenes se
identifican en .env.example bajo el namespace maxdz10 con la versión sprint02-1.0.0.

Las claves deben corresponder al mismo par RSA. Security recibe privada y pública; courier
y shipment solo la pública. Los montajes son de lectura y rechazan una ruta inexistente.
El proceso UID 10001 recibe como grupo suplementario el KEYS_GID indicado por el operador
(por ejemplo, el resultado de id -g). Ese grupo debe poder leer los archivos: usar permisos
0640 para la privada y un directorio externo transitable por el grupo, sin lectura pública. No copiar claves,
.env ni contraseñas a los repositorios o a los contextos de build.

Para la primera cuenta ADMIN, habilitar BOOTSTRAP_ADMIN_ENABLED y proporcionar email/password
externos. Después del aprovisionamiento deshabilitarlo y recrear security con el mismo volumen.
El procedimiento es idempotente y no restablece contraseñas de cuentas existentes.

## Entrega mediante imágenes

Desde shipment-service, con imágenes ya publicadas:

```sh
docker compose --env-file /ruta/privada/logitrack.env config --quiet
docker compose --env-file /ruta/privada/logitrack.env pull
docker compose --env-file /ruta/privada/logitrack.env up -d --wait
docker compose --env-file /ruta/privada/logitrack.env ps
```

El Compose base no contiene build, rutas a JAR del host ni dependencias de los checkouts de
courier/security/configserver. Los healthchecks de las imágenes de aplicación usan únicamente
Java; los de MySQL consultan una tabla con el usuario de aplicación y esperan al final de la
inicialización. La API de Config Server debe estar saludable antes de iniciar los clientes.
Un proveedor caído después del arranque mantiene los errores de negocio ya definidos; depends_on
ordena el arranque, no sustituye los timeouts ni garantiza disponibilidad continua.

Las APIs se publican por defecto en 127.0.0.1:8080/8081/8082 y Config Server en 127.0.0.1:8085.
Dentro de la red se usan nombres DNS de servicios; localhost en un contenedor refiere al propio
contenedor y solo se utiliza para su healthcheck. MySQL no publica puertos al host. Los límites
de memoria acotan el consumo del entorno de siete contenedores.

## Construcción opcional de desarrollo

Con los cuatro repositorios hermanos y los Dockerfiles correspondientes disponibles,
completar las variables IMAGE con etiquetas candidatas locales y ejecutar:

```sh
docker compose --env-file /ruta/privada/logitrack.env -f docker-compose.yaml -f compose.build.yaml build
docker compose --env-file /ruta/privada/logitrack.env -f docker-compose.yaml -f compose.build.yaml up -d --wait
```

La construcción local sirve para desarrollo. La entrega se comprueba descargando las
cuatro imágenes publicadas, según el procedimiento de #29. Config Server dispone de su
propio Dockerfile multietapa y sonda de salud, igual que los servicios de negocio.

## Persistencia y datos existentes

Para detener sin perder datos usar stop o down sin la opción de volúmenes. Recrear aplicaciones
con up -d conserva las tres bases. No usar down -v, volume prune ni borrar volúmenes del equipo.
El proyecto nuevo y sus volúmenes shipment_mysql_data/courier_mysql_data/security_mysql_data
no sustituyen el antiguo mysql_data del Sprint01. El proyecto puede nombrarse mediante
COMPOSE_PROJECT_NAME para aislar un ensayo sin tocar contenedores ajenos.

El esquema inicial se aplica solo a volúmenes nuevos. Para datos existentes se requieren backup,
ensayo de restauración y el procedimiento de migración; cambiar la imagen no aplica DDL ni
adjudica identidades históricas automáticamente. La modalidad anterior del componente se
conserva con compose.component.yaml y .env.component.example; usarla con el nombre de proyecto
original, sin reinterpretar sus variables como las del Compose general.

## Comprobación integrada acotada

Verificar salud de los siete contenedores, login, alta de envío, asociación del repartidor,
asignación, EN_RUTA, ENTREGADO e historial. Reiniciar/recrear las aplicaciones conservando
volúmenes y confirmar los mismos UUID y datos. Comprobar que cada usuario de aplicación solo
puede acceder a su propia base. Se reutilizan las suites de seguridad, REST, configuración y
regresión ya registradas en #25/#26/#27. La descarga limpia de Docker Hub y la evidencia
académica final corresponden a #29.
