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
