# Núcleo Sprint04 — issue #72

`compose.sprint04.yaml` levanta frontend Nginx, Config Server, Eureka, Security,
Courier, Shipment, API Gateway y tres MySQL. Es independiente del Compose Sprint03:
no lo modifica, no comparte nombres fijos de contenedor y usa volúmenes por proyecto.
No requiere RabbitMQ, Minikube ni proveedores externos para arrancar el núcleo.

## Preparación

Clonar los repositorios de la organización como carpetas hermanas. El frontend debe
incorporar las PR #12 y #16–#21, integradas en `develop` el 10/11/2026.
Construirlo con el modo real; `VITE_*` son argumentos públicos de build, no variables
que puedan cambiarse después en el runtime:

```sh
docker build --build-arg VITE_USE_MOCKS=false \
  -t logitrack-frontend:sprint04-session-local ../logitrack-frontend
```

Copiar `.env.sprint04.example` a un archivo privado ignorado por Git, por ejemplo
`.env.sprint04.local`. Completar las seis contraseñas de bases, claves RSA externas
y `KEYS_GID` con el grupo que puede leerlas. Mantener las claves fuera del repositorio,
con archivos 0640 y directorio 0700. Solo Security recibe la clave privada.
No imprimir `docker compose config` sin `--quiet` ni publicar el env resuelto.

Las imágenes backend de la plantilla están fijadas por digest y usan el commit de
configuración indicado en `CONFIG_LABEL`. El Config Server necesita salida HTTPS
a GitHub para obtener ese commit. No sustituirlo por una rama móvil sin verificar.
Para otro frontend publicado, fijar también su digest en `FRONTEND_IMAGE`.

En una base vacía puede habilitarse `BOOTSTRAP_ADMIN_ENABLED=true` con correo y
contraseña privados para el primer administrador. Desactivarlo después conservando
el volumen de Security; no se usa como mecanismo para cambiar contraseñas existentes.

## Arranque y parada

Desde la raíz de shipment-service:

```sh
docker compose --env-file .env.sprint04.local config --quiet
docker compose --env-file .env.sprint04.local up -d --wait --wait-timeout 240
docker compose --env-file .env.sprint04.local ps
```

La plantilla define `COMPOSE_FILE=compose.sprint04.yaml`; no depende de seleccionar
el archivo antiguo por accidente. También admite `up` en primer plano:

```sh
docker compose --env-file .env.sprint04.local up
```

El frontend escucha por defecto en `http://localhost:8080`. Solo ese puerto se
publica y queda ligado a loopback. Gateway y bases permanecen en la red de Compose.
Nginx conserva `/applications/...`, añade el proxy al Gateway y sirve fallback SPA
solo para páginas: un error API no se convierte en `index.html`.
Frontend no-root, filesystem de solo lectura, `/tmp` efímero, capacidades retiradas
y `no-new-privileges`. Cada aplicación conserva su healthcheck y usuario de imagen.

`COMPOSE_PROJECT_NAME` debe ser único para cada instalación. No reutilizar el nombre
ni los volúmenes de Sprint03. Para staging/hosting, conservar loopback y colocar un
reverse proxy con TLS delante; host, dominio, límites y acceso remoto siguen pendientes.
Los límites de memoria del archivo son techos, no una garantía de capacidad del host.

## Persistencia

```sh
docker compose --env-file .env.sprint04.local down
docker compose --env-file .env.sprint04.local up -d --wait --wait-timeout 240
```

`down` conserva los tres volúmenes: usuarios, fichas, vínculos, envíos e historial
persisten al recrear los contenedores. `down --volumes` borra esos datos; reservarlo
exclusivamente para un proyecto de pruebas identificado como descartable.
Las claves externas deben mantenerse entre recreaciones para no invalidar los JWT
por un cambio accidental de par criptográfico. El frontend conserva JWT solo en
memoria de la pestaña: la persistencia de base no implica sesión tras recargar.

## Verificación

La prueba del 10/11/2026 usa el proyecto descartable `lt-s04-72`, puerto 28080,
imagen frontend construida desde `develop@ff9c7bc9ff93` con mocks desactivados y
las referencias backend de la plantilla. Se comprueba el modelo con `config --quiet` y la salud de los diez contenedores.
La regresión del frontend `scripts/check_gateway.py` ejecuta 38 solicitudes con 57
aserciones a través de Nginx (8 escenarios); sus JWT no se guardan.

El verificador de este repositorio permite reproducir el ensayo de persistencia:

```sh
# Exportar S04_GATEWAY_URL, S04_ADMIN_EMAIL y S04_ADMIN_PASSWORD en privado.
python3 scripts/compose/verify_sprint04.py prepare /ruta/privada/state.json
docker compose --env-file .env.sprint04.local down
docker compose --env-file .env.sprint04.local up -d --wait --wait-timeout 240
python3 scripts/compose/verify_sprint04.py verify /ruta/privada/state.json
```

`prepare` crea usuarios y recursos ficticios, verifica edición/estado Courier,
rechazo de asignación a INACTIVO sin mutar el envío/historial, reactivación,
asignación y rechazo de lectura de ficha ajena. El archivo nuevo se crea en modo
0600 y contiene credenciales ficticias: elegir un directorio privado fuera de Git,
no publicarlo y eliminarlo después de la prueba. `verify` comprueba que persistan
usuario, ficha editada, estado, envío, asignación e historial. Resultado: preparación **17 requests / 38 aserciones**; verificación tras recreación
**7 requests / 18 aserciones**. Los usuarios, edición de ficha, estado, envío,
asignación e historial se conservaron. El navegador confirmó login CLIENTE y
listado propio después de recrear. También se verificó `up` en primer plano,
frontend no-root/read-only y publicación exclusiva del frontend en loopback.
Estos resultados se informan separados de las 188 pruebas unitarias/de componentes
del frontend ya aprobadas sobre el mismo árbol de código; no se repitieron suites
Java ni auditorías.
No acredita hosting público, despliegue en Kubernetes ni aceptación completa de #9.


## Actualización de sesión renovable — security-service#11

Durante el ensayo inicial se construyeron imágenes locales de Security, Gateway y
Frontend. Para reproducir el release usar ahora las imágenes públicas por digest de
`.env.sprint04.example` y el [manifiesto de #73](release/published.json).
Los siguientes comandos documentan la construcción del candidato, que no es necesaria
para descargar el release:

```sh
docker build -t logitrack-security-service:sprint04-session-local ../security-service
docker build -t logitrack-api-gateway:sprint04-session-local ../api-gateway
docker build --build-arg VITE_USE_MOCKS=false -t logitrack-frontend:sprint04-session-local ../logitrack-frontend
```

Los tres repositorios deben incluir las PR de security-service#11 / frontend#3.
`SESSION_ORIGIN` debe coincidir exactamente con la URL pública del navegador, incluido
puerto si corresponde. La plantilla usa http://localhost:8080 para la excepción local
Secure de Chromium; si cambiás FRONTEND_HOST_PORT, actualizar también SESSION_ORIGIN.
Otros navegadores pueden requerir HTTPS local. En despliegue, usar HTTPS; no se quita
Secure de la cookie. CORS_ALLOWED_ORIGINS del Gateway toma ese mismo origen.

En bases nuevas se aplica 002-refresh-sessions.sql después de 001. En una base ya
existente, aplicar la migración 002 explícitamente mediante conexión privada antes de
actualizar Security; conservar usuarios, volumen y claves. No usar down --volumes.
El SQL de Compose coincide byte a byte con el de Security. Las imágenes anteriores
no ofrecen las nuevas rutas de sesión: no combinarlas con el frontend renovable.

El JWT queda en memoria; solo se conserva la cookie HttpOnly de renovación. React
puede guardar un marcador sin credenciales para un logout pendiente. Recarga restaura
por renovación y consulta me. Logout revoca renovación; un Bearer copiado no se revoca
antes de exp. Ver BROWSER_SESSION.md de Security y docs/SESION_RENOVABLE.md del frontend.

El ensayo candidato usa diez servicios saludables, backend/Gateway nuevos y navegador
real: login → recarga autenticada → logout → recarga en login. La regresión Bearer de
negocio se mantiene: 38 solicitudes / 57 aserciones. Las tres imágenes modificadas ya están publicadas con digests en #73.
El cierre técnico de G1 se registró con el arranque por digest y las pruebas de #73.
Una reproducción independiente sigue siendo recomendable para preparar la demo.
La [decisión de cierre de #73](https://github.com/LogiTrack-Atom/shipment-service/issues/73)
detalla los criterios aplicados. Para RabbitMQ, seguir la [revisión MQ RC2](release-mq-rc2/README.md).
