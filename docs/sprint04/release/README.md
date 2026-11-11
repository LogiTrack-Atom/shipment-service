# Preparación de release del núcleo — #73

La CI frontend está integrada mediante frontend#23. La aceptación local de frontend#9
se completó como apoyo del release, conservando DiazGab como responsable. Detectó un
POST confirmado cuya respuesta perdida producía 502 y permitía repetir el alta;
frontend#24 corrige la escritura incierta. CI aprueba 204 pruebas, lint, builds y humo
Docker. Se comprobaron además 17 solicitudes de errores reales y seis cortes de
respuesta después de 201/200, recuperando cuenta/ficha/vínculos sin repetir POST.

`candidate.json` identifica las imágenes locales verificadas, IDs, plataforma,
commits de build/entrega y comparación exacta de entradas runtime. Conserva imágenes
base por digest y procedencia Sprint03. Es un candidato: publicación y G1 son estados
separados. No contiene contraseñas, cookies, JWT ni rutas privadas. Nunca publicar
runtime.env, claves o estados privados de los gestores de demostración.

## Publicación controlada

Requiere autorización para el destino Docker Hub y autenticación local previa por
el propietario. No transmitir tokens de Docker Hub por chat ni incorporarlos al repo.

```sh
python3 scripts/release/publish_sprint04.py \
  --candidate docs/sprint04/release/candidate.json \
  --output /ruta/de/evidencias/published.json \
  --tag sprint04-rc1 --namespace maxdz10
```

El publicador solo admite las tres imágenes modificadas, compara su ID con el probado,
rechaza cambiar un tag existente con contenido distinto y falla cerrado ante errores
de autenticación o de registro ambiguos. No publica Java/SQL histórico ni cambia tags
Sprint03. Registrar los digests devueltos, no deducirlos de nombres de tags. Cinco
pruebas protegen esos rechazos; CI ejecuta esas pruebas automáticamente.

Usar los tres digests publicados en SECURITY_IMAGE, GATEWAY_IMAGE y FRONTEND_IMAGE;
conservar los cuatro digests base del candidato, CONFIG_LABEL fijado y MYSQL_IMAGE con
el digest observado. Las credenciales se completan en un env externo privado, nunca en
el manifiesto. Preparar claves propias; usar SESSION_ORIGIN exacto y localhost de
prueba compatible con Secure, o HTTPS. En bases existentes aplicar 002 antes de la nueva
imagen Security, sin borrar usuarios/volúmenes.

## Cierre de G1

Después de publicar: verificar pull/identidades y levantar las referencias inmutables;
enlazar CI real y regresión afectada, y registrar reproducción independiente por otro
integrante según el criterio de #73. `publication_complete` no significa `g1_approved`.
No se atribuye una reproducción humana inexistente ni se comienza #76 antes de G1.
Las guías se preparan en paralelo; promoción a main sigue en #75 de JMatiasF.


## Imágenes públicas sprint04-rc1

`published.json` contiene los tres digests devueltos por Docker Hub, sus IDs de imagen,
commits de construcción/entrega y las referencias base que se conservan. La plantilla
`.env.sprint04.example` usa estas referencias públicas y fija también MySQL por digest.
Se verificó descarga sin credenciales mediante un directorio Docker vacío y socket
explícito de Docker Desktop; los tres IDs coinciden con el candidato aceptado.
La plataforma verificada es linux/amd64.

## Reproducción por otro integrante

Otro integrante debe partir del commit de esta entrega y seguir
[COMPOSE.md](../COMPOSE.md): copiar la plantilla a un archivo privado externo,
completar contraseñas y claves RSA propias, elegir un nombre de proyecto y puerto libres,
y ajustar SESSION_ORIGIN al mismo localhost/puerto. Para bases nuevas habilitar
bootstrap ADMIN durante la preparación y deshabilitarlo después. No reutilizar claves
GPG de Docker como claves RSA JWT de la aplicación.

Desde la raíz del repositorio:

```sh
docker compose --env-file /ruta/privada/sprint04.env -f compose.sprint04.yaml config --quiet
docker compose --env-file /ruta/privada/sprint04.env -f compose.sprint04.yaml pull
docker compose --env-file /ruta/privada/sprint04.env -f compose.sprint04.yaml up -d --wait --wait-timeout 480
# Exportar en privado S04_GATEWAY_URL, S04_ADMIN_EMAIL y S04_ADMIN_PASSWORD.
python3 scripts/compose/verify_sprint04.py prepare /ruta/privada/state.json
docker compose --env-file /ruta/privada/sprint04.env -f compose.sprint04.yaml down
docker compose --env-file /ruta/privada/sprint04.env -f compose.sprint04.yaml up -d --wait --wait-timeout 480
python3 scripts/compose/verify_sprint04.py verify /ruta/privada/state.json
```

Registrar en #73 identidad del integrante, commit, plataforma, salud de los diez
servicios, resultados prepare/verify y login/recarga/logout en navegador real.
No adjuntar env, state.json, cookies, JWT, contraseñas ni claves privadas.
`down` conserva los datos; no usar `--volumes` en una instalación existente.
Esta comprobación independiente continúa pendiente y no se sustituye por repetir
el ensayo técnico en la misma máquina.


## Disponibilidad después del arranque

La salud de procesos y la propagación del registro Eureka son comprobaciones distintas.
En el primer arranque del ensayo por digest, los diez procesos estaban saludables pero
una solicitud de alta recibió 503 durante la propagación inicial. No se repitió ese POST.
Después de comprobar mediante lecturas autenticadas que Courier y Shipment respondían
200, un escenario nuevo e independiente completó prepare: 17 solicitudes y 38 aserciones.
Esperar la disponibilidad de ambas rutas antes de iniciar una demostración; ante una
escritura 5xx conservar la política de resultado incierto y no reintentar altas a ciegas.


## Resultado técnico del release publicado — 10/11/2026

Laboratorio aislado con bases nuevas y las ocho referencias de imagen fijadas:
diez servicios saludables al arrancar y después de recrear contenedores conservando
volúmenes. Preparación: 17 solicitudes / 38 aserciones. Verificación posterior:
7 solicitudes / 18 aserciones; usuario, edición y estado Courier, envío, asignación e
historial conservados. Las rutas SPA / y /envios respondieron 200 HTML. Las tres
imágenes publicadas se descargaron sin credenciales y mantuvieron los IDs aceptados.
El manifiesto registra estos resultados separados de las 204 pruebas frontend y la
aceptación previa de navegador. El laboratorio temporal se retira tras el ensayo.

La reproducción por otro integrante sigue pendiente: #73 permanece En curso y
G1 no se declara aprobado. No hay trabajo técnico pendiente en frontend#9 para
este release. No se inicia #76 antes de resolver ese criterio de #73.


## Decisión vigente de cierre — 10/11/2026

El propietario autorizó cerrar #73 con las pruebas técnicas anteriores y retirar la
reproducción por otro integrante como requisito. No se realizó esa reproducción.
#73 está cerrada, G1 técnico aceptado y Kanban Hecho. Esta decisión sustituye las
menciones históricas a su espera. Las instrucciones de reproducción se conservan como
guía útil; #76 puede continuar y la ampliación mantiene pruebas/release propios.
