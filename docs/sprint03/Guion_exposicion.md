# Guion de exposición Sprint03 — 12 a 15 minutos

Estado: material de preparación. El ensayo, el video y su entrega no están acreditados.

| Tiempo | Presentador propuesto | Demostración |
| --- | --- | --- |
| 0:00–1:30 | Max | Kanban, estado real de issues, ramas por issue y PR hacia develop; deuda de Sprint02 y pendientes académicos |
| 1:30–4:00 | Gabriel | Diagrama, Config Server, revisión Git, perfiles, salud de Eureka y cuatro clientes; explicar por qué Eureka no transporta negocio |
| 4:00–6:00 | Matías | Rutas del Gateway y StripPrefix, login ADMIN y diagnóstico ADMIN de configuración; rechazo anónimo y rol CLIENTE |
| 6:00–10:00 | Max | Alta de envío, asignación, lectura persistida, Feign por nombre, EN_RUTA, ENTREGADO e historial |
| 10:00–12:00 | Matías | Repartidor INACTIVO/inexistente, usuario sin permiso, snapshots sin mutación; distinguir 401, 403, 404 y 422 |
| 12:00–14:00 | Gabriel y Max | Caída de Courier con 503 sin mutación, recuperación y evidencia de cambio de instancia/persistencia |
| 14:00–15:00 | Los tres | Versiones, imágenes y resultados; señalar límites y cierre técnico separado de entrega académica |

Los presentadores propuestos siguen las responsabilidades del tablero. Cada integrante
debe poder ejecutar y explicar todo el flujo, independientemente de quién escribió un cambio.

## Preparación de pantalla

- Abrir Project en la vista Sprint03 — Kanban y conservar visibles los estados reales.
- Tener a mano la guía de operación y la evidencia final; usar la misma revisión que se muestra.
- Preparar una demo inicial y cargar su entorno privado en Postman.
- Ejecutar los rechazos antes del flujo que crea y modifica recursos.
- Abrir solo el código relevante: autorización de Gateway, cliente Feign/decoder,
  servicio de asignación y respuesta de disponibilidad de Courier.
- Mostrar salud y cuatro registros; no abrir archivos de claves, variables completas,
  reportes Newman privados o credenciales del registro de imágenes.

## Preguntas que deben poder responder los tres

1. ¿Qué cambia entre autenticación, autorización por rol y pertenencia al recurso?
2. ¿Dónde se persiste la asignación y por qué no hay una relación JPA entre servicios?
3. ¿Por qué una asignación no cambia el envío a EN_RUTA ni agrega un evento de estado?
4. ¿Cómo descubre Feign una instancia nueva sin recompilar Shipment?
5. ¿Qué diferencia hay entre ErrorDecoder y un fallo de transporte sin respuesta HTTP?
6. ¿Qué se conserva cuando Courier falla y cómo lo comprobamos?
7. ¿Cómo demuestran el perfil consumido sin exponer secretos?
8. ¿Qué hace reset y por qué no puede usarse sobre otro entorno?
9. ¿Qué significa disponible y qué reglas no están definidas para tarifas?
10. ¿Qué pruebas fueron ejecutadas y cuáles son sus límites?

## Checklist académico

- [ ] Los tres reejecutaron el flujo completo sin depender del autor de una implementación.
- [ ] Se realizó un ensayo conjunto con datos y versiones finales.
- [ ] Se grabó el video con participación y explicación de los tres.
- [ ] Se revisó que la grabación no publique claves o credenciales.
- [ ] Se registraron enlace del video y constancia de entrega.

Estos puntos permanecen pendientes hasta realizarse. La existencia del guion no cierra
la issue de ensayo y video ni demuestra participación individual.
