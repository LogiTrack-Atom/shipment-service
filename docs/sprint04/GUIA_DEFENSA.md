# Sprint04 P0 · Guía de reproducción y defensa

Usar el release [sprint04-rc1](release/published.json). La plantilla `.env.sprint04.example` fija las imágenes por digest y el commit de configuración. La ampliación RabbitMQ tiene su propio manifiesto y una defensa separada.

## Preparar el entorno

1. Clonar Shipment y seleccionar el commit de entrega indicado en el manifiesto de promoción cuando esté publicado. Para el ensayo técnico P0 sirve el árbol que contiene esta guía y la plantilla P0.
2. Preparar un proyecto Compose nuevo y bases ficticias. Seguir [la preparación de Compose](COMPOSE.md) para las claves RSA externas, permisos y contraseñas. Copiar la plantilla a un archivo privado, configurar un puerto libre y usar el mismo origen exacto para el navegador y `SESSION_ORIGIN`. Crear el ADMIN inicial con el bootstrap de una base nueva.
3. Desde la raíz de Shipment, ejecutar lo siguiente con la ruta del archivo privado.

   ```sh
   env_file=/ruta/privada/p0.env
   docker compose --env-file "$env_file" config --quiet
   docker compose --env-file "$env_file" pull
   docker compose --env-file "$env_file" up -d --wait --wait-timeout 240
   docker compose --env-file "$env_file" ps
   ```

   Esperar los diez servicios saludables y comprobar el login en el navegador. Usar Chromium para el origen local de la plantilla. Si se cambia el puerto, actualizar el origen y recargar la configuración.

## Guion de demostración

| Paso | Acción | Evidencia esperada |
| --- | --- | --- |
| Cliente | Registrar una cuenta ficticia, iniciar sesión y crear un envío | El nuevo envío aparece en su listado e historial |
| ADMIN | Crear una ficha de repartidor, vincularla a una identidad REPARTIDOR y asignarla al envío | Shipment verifica disponibilidad en Courier y muestra la ficha asignada |
| Repartidor | Ingresar con la identidad vinculada y consultar el trabajo asignado | El listado muestra los envíos asociados a su ficha |
| Control de acceso | Desde una cuenta distinta, intentar consultar una ficha ajena según la matriz del contrato | El Gateway y el servicio aplican la política de acceso |
| Falla de negocio | Inactivar un repartidor e intentar asignarlo a otro envío | La solicitud se rechaza y la asignación y el historial quedan iguales |
| Sesión | Recargar tras iniciar sesión, cerrar sesión y recargar de nuevo | La sesión se restaura mediante renovación y después del logout el portal vuelve al login |

Usar el [contrato de frontend](contratos-frontend/README.md) para confirmar rutas, roles y estados. El cliente usa el access JWT en memoria y la renovación depende de una cookie HttpOnly. Los servicios validan el JWT y aplican autorización sobre sus recursos. Explicar que un access token emitido sigue sujeto a su expiración tras revocar la renovación.

Para la defensa, MaxDZ-10 conduce registro y asignación, DiazGab explica la UI y el manejo de sesión, y JMatiasF conduce fallas y recuperación. Esta es una distribución propuesta para el ensayo. Cada integrante debe poder explicar los cuatro temas, intercambiar un paso con un compañero y responder qué sucede si Courier rechaza la asignación o si el navegador pierde su sesión.

## Reproducción técnica

Con las credenciales ficticias en el entorno privado del verificador y `S04_GATEWAY_URL` apuntando al origen del portal, ejecutar:

```sh
python3 scripts/compose/verify_sprint04.py prepare /ruta/privada/p0-state.json
docker compose --env-file "$env_file" down
docker compose --env-file "$env_file" up -d --wait --wait-timeout 240
python3 scripts/compose/verify_sprint04.py verify /ruta/privada/p0-state.json
```

La recreación conserva los volúmenes. El archivo de estado contiene una contraseña ficticia y debe guardarse fuera de Git y eliminarse al terminar. Publicar solamente versión, conteos, resultados y capturas con datos de prueba. Quitar encabezados Authorization, JWT, cookies y contraseñas de cualquier evidencia.

La evidencia técnica existente de [#73](https://github.com/LogiTrack-Atom/shipment-service/issues/73) cubre arranque inmutable y persistencia. El guion requiere que otro integrante lo ejecute, que los tres ensayen y que se registre la entrega académica. Registrar la constancia con esta ficha cuando ocurra:

| Actividad | Información a registrar |
| --- | --- |
| Reproducción independiente | Integrante, fecha, plataforma, release/digest, pasos, resultado y enlace a evidencia sanitizada |
| Ensayo del equipo | Fecha, integrantes presentes, pasos explicados por cada uno, fallas discutidas y correcciones |
| Entrega académica | Fecha, canal de entrega, versión presentada y referencia del comprobante |

La [#49 de Sprint03](https://github.com/LogiTrack-Atom/shipment-service/issues/49) figuraba abierta el 10/11/2026 y conserva su seguimiento histórico.
