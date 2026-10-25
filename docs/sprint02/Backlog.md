# Backlog y ejecución del Sprint 02

Estado: planificación publicada; implementación funcional pendiente.

[Project común](https://github.com/orgs/LogiTrack-Atom/projects/1) · [Contratos técnicos](Contratos_tecnicos.md)

Las issues de esta tabla son las mismas que muestra el Project. Cada una contiene alcance, aceptación, pruebas, documentación y dependencias de bloqueo. No se duplican issues entre repositorios. Los milestones individuales son opcionales y no se crearon para iniciar este sprint.

## Distribución

| Responsable | Issues | Responsabilidad |
| --- | --- | --- |
| MaxDZ-10 | 13 | Arquitectura, shipment, security, migración, integración y entrega |
| JMatiasF | 6 | Implementación, seguridad, configuración y verificación de courier |
| DiazGab | 2 | Repositorio de configuración y aplicación Config Server |

La cantidad de issues no representa una estimación de horas. Cada responsable aporta pruebas y documentación de su componente; las integraciones se coordinan mediante contratos y dependencias. La prioridad Alta identifica acuerdos y habilitadores iniciales, seguridad, separación e integración de entrega; Media agrupa la consolidación posterior de configuración, pruebas e imágenes. Todas las tareas son necesarias para completar el alcance, incluida la prioridad Media.

## Issues publicadas

| Issue | Trabajo | Responsable | Prioridad | Depende de |
| --- | --- | --- | --- | --- |
| [shipment-service#21](https://github.com/LogiTrack-Atom/shipment-service/issues/21) | docs: acordar contratos técnicos y planificación del Sprint 02 | MaxDZ-10 | Alta | — |
| [courier-service#1](https://github.com/LogiTrack-Atom/courier-service/issues/1) | chore: inicializar courier-service y su modelo de repartidor | JMatiasF | Alta | [shipment-service#21](https://github.com/LogiTrack-Atom/shipment-service/issues/21) |
| [courier-service#2](https://github.com/LogiTrack-Atom/courier-service/issues/2) | feat: registrar y consultar repartidores mediante API REST | JMatiasF | Alta | [courier-service#1](https://github.com/LogiTrack-Atom/courier-service/issues/1) |
| [courier-service#3](https://github.com/LogiTrack-Atom/courier-service/issues/3) | feat: actualizar repartidores y consultar disponibilidad | JMatiasF | Alta | [courier-service#2](https://github.com/LogiTrack-Atom/courier-service/issues/2) |
| [security-service#1](https://github.com/LogiTrack-Atom/security-service/issues/1) | feat: crear usuarios, roles y registro seguro | MaxDZ-10 | Alta | [shipment-service#21](https://github.com/LogiTrack-Atom/shipment-service/issues/21) |
| [security-service#2](https://github.com/LogiTrack-Atom/security-service/issues/2) | feat: autenticar usuarios y emitir JWT RS256 | MaxDZ-10 | Alta | [security-service#1](https://github.com/LogiTrack-Atom/security-service/issues/1) |
| [security-service#3](https://github.com/LogiTrack-Atom/security-service/issues/3) | feat: proteger la administración de usuarios y consultas de identidad | MaxDZ-10 | Alta | [security-service#2](https://github.com/LogiTrack-Atom/security-service/issues/2) |
| [configserver-ms#1](https://github.com/LogiTrack-Atom/configserver-ms/issues/1) | feat: preparar configuraciones centralizadas por aplicación y ambiente | DiazGab | Alta | [shipment-service#21](https://github.com/LogiTrack-Atom/shipment-service/issues/21) |
| [configserver#1](https://github.com/LogiTrack-Atom/configserver/issues/1) | feat: implementar y contenerizar Spring Cloud Config Server | DiazGab | Alta | [configserver-ms#1](https://github.com/LogiTrack-Atom/configserver-ms/issues/1) |
| [shipment-service#22](https://github.com/LogiTrack-Atom/shipment-service/issues/22) | feat: preparar migración verificable de repartidores y asignaciones | MaxDZ-10 | Alta | [shipment-service#21](https://github.com/LogiTrack-Atom/shipment-service/issues/21), [courier-service#1](https://github.com/LogiTrack-Atom/courier-service/issues/1) |
| [shipment-service#23](https://github.com/LogiTrack-Atom/shipment-service/issues/23) | refactor: separar repartidores del dominio de envíos | MaxDZ-10 | Alta | [shipment-service#22](https://github.com/LogiTrack-Atom/shipment-service/issues/22) |
| [shipment-service#24](https://github.com/LogiTrack-Atom/shipment-service/issues/24) | feat: integrar CourierClient con timeouts y errores controlados | MaxDZ-10 | Alta | [shipment-service#23](https://github.com/LogiTrack-Atom/shipment-service/issues/23), [courier-service#3](https://github.com/LogiTrack-Atom/courier-service/issues/3) |
| [courier-service#4](https://github.com/LogiTrack-Atom/courier-service/issues/4) | feat: proteger courier con JWT y pertenencia de repartidores | JMatiasF | Alta | [courier-service#3](https://github.com/LogiTrack-Atom/courier-service/issues/3), [security-service#2](https://github.com/LogiTrack-Atom/security-service/issues/2), [security-service#3](https://github.com/LogiTrack-Atom/security-service/issues/3) |
| [shipment-service#25](https://github.com/LogiTrack-Atom/shipment-service/issues/25) | feat: proteger envíos por rol y pertenencia al usuario autenticado | MaxDZ-10 | Alta | [shipment-service#23](https://github.com/LogiTrack-Atom/shipment-service/issues/23), [security-service#2](https://github.com/LogiTrack-Atom/security-service/issues/2), [security-service#3](https://github.com/LogiTrack-Atom/security-service/issues/3) |
| [courier-service#5](https://github.com/LogiTrack-Atom/courier-service/issues/5) | feat: consumir configuración remota en courier-service | JMatiasF | Media | [courier-service#1](https://github.com/LogiTrack-Atom/courier-service/issues/1), [configserver-ms#1](https://github.com/LogiTrack-Atom/configserver-ms/issues/1), [configserver#1](https://github.com/LogiTrack-Atom/configserver/issues/1) |
| [shipment-service#26](https://github.com/LogiTrack-Atom/shipment-service/issues/26) | feat: incorporar Config Client en shipment-service | MaxDZ-10 | Media | [shipment-service#23](https://github.com/LogiTrack-Atom/shipment-service/issues/23), [configserver-ms#1](https://github.com/LogiTrack-Atom/configserver-ms/issues/1), [configserver#1](https://github.com/LogiTrack-Atom/configserver/issues/1) |
| [security-service#4](https://github.com/LogiTrack-Atom/security-service/issues/4) | test: completar configuración, pruebas y Docker de security-service | MaxDZ-10 | Media | [security-service#3](https://github.com/LogiTrack-Atom/security-service/issues/3), [configserver-ms#1](https://github.com/LogiTrack-Atom/configserver-ms/issues/1), [configserver#1](https://github.com/LogiTrack-Atom/configserver/issues/1) |
| [courier-service#6](https://github.com/LogiTrack-Atom/courier-service/issues/6) | test: completar pruebas, OpenAPI y Docker de courier-service | JMatiasF | Media | [courier-service#3](https://github.com/LogiTrack-Atom/courier-service/issues/3), [courier-service#4](https://github.com/LogiTrack-Atom/courier-service/issues/4), [courier-service#5](https://github.com/LogiTrack-Atom/courier-service/issues/5) |
| [shipment-service#27](https://github.com/LogiTrack-Atom/shipment-service/issues/27) | test: adaptar regresión e integración de envíos al Sprint 02 | MaxDZ-10 | Media | [shipment-service#22](https://github.com/LogiTrack-Atom/shipment-service/issues/22), [shipment-service#24](https://github.com/LogiTrack-Atom/shipment-service/issues/24), [shipment-service#25](https://github.com/LogiTrack-Atom/shipment-service/issues/25), [shipment-service#26](https://github.com/LogiTrack-Atom/shipment-service/issues/26) |
| [shipment-service#28](https://github.com/LogiTrack-Atom/shipment-service/issues/28) | chore: orquestar cuatro servicios y tres MySQL con Docker Compose | MaxDZ-10 | Alta | [shipment-service#24](https://github.com/LogiTrack-Atom/shipment-service/issues/24), [shipment-service#25](https://github.com/LogiTrack-Atom/shipment-service/issues/25), [shipment-service#26](https://github.com/LogiTrack-Atom/shipment-service/issues/26), [security-service#4](https://github.com/LogiTrack-Atom/security-service/issues/4), [courier-service#6](https://github.com/LogiTrack-Atom/courier-service/issues/6), [configserver#1](https://github.com/LogiTrack-Atom/configserver/issues/1) |
| [shipment-service#29](https://github.com/LogiTrack-Atom/shipment-service/issues/29) | chore: coordinar publicación, validación y entrega del Sprint 02 | MaxDZ-10 | Alta | [shipment-service#21](https://github.com/LogiTrack-Atom/shipment-service/issues/21), [courier-service#1](https://github.com/LogiTrack-Atom/courier-service/issues/1), [courier-service#2](https://github.com/LogiTrack-Atom/courier-service/issues/2), [courier-service#3](https://github.com/LogiTrack-Atom/courier-service/issues/3), [security-service#1](https://github.com/LogiTrack-Atom/security-service/issues/1), [security-service#2](https://github.com/LogiTrack-Atom/security-service/issues/2), [security-service#3](https://github.com/LogiTrack-Atom/security-service/issues/3), [configserver-ms#1](https://github.com/LogiTrack-Atom/configserver-ms/issues/1), [configserver#1](https://github.com/LogiTrack-Atom/configserver/issues/1), [shipment-service#22](https://github.com/LogiTrack-Atom/shipment-service/issues/22), [shipment-service#23](https://github.com/LogiTrack-Atom/shipment-service/issues/23), [shipment-service#24](https://github.com/LogiTrack-Atom/shipment-service/issues/24), [courier-service#4](https://github.com/LogiTrack-Atom/courier-service/issues/4), [shipment-service#25](https://github.com/LogiTrack-Atom/shipment-service/issues/25), [courier-service#5](https://github.com/LogiTrack-Atom/courier-service/issues/5), [shipment-service#26](https://github.com/LogiTrack-Atom/shipment-service/issues/26), [security-service#4](https://github.com/LogiTrack-Atom/security-service/issues/4), [courier-service#6](https://github.com/LogiTrack-Atom/courier-service/issues/6), [shipment-service#27](https://github.com/LogiTrack-Atom/shipment-service/issues/27), [shipment-service#28](https://github.com/LogiTrack-Atom/shipment-service/issues/28) |

## Orden de trabajo

1. Revisar e integrar el PR de contratos en develop. Ninguna implementación funcional se considera ya realizada por publicar su planificación.
2. En paralelo, preparar el esquema y proyecto de courier, los usuarios de security y los YAML de configserver-ms.
3. Completar API y disponibilidad de courier, login/administración de security y la prueba aislada de Config Server. Ensayar la migración en bases de copia cuando esté definido el esquema destino.
4. Separar repartidores de shipment e integrar el cliente REST, la pertenencia de recursos y la validación JWT local. Se puede preparar trabajo con dobles HTTP mientras se terminan los proveedores.
5. Incorporar Config Client en cada aplicación después de comprobar el servidor. Completar pruebas, documentación e imagen Docker de cada componente.
6. Integrar las cuatro aplicaciones y tres MySQL en Compose, verificar red, salud y persistencia.
7. Publicar imágenes con versión y validar la entrega desde descarga limpia, fijando también la revisión de configuración.

La integración de Config Client de shipment y security corresponde a MaxDZ-10; la de courier, a JMatiasF. DiazGab entrega el repositorio y el servidor verificados. No es necesario esperar a terminar los servicios para preparar y probar Config Server.

## Coordinación y estados

La coordinadora [shipment-service#29](https://github.com/LogiTrack-Atom/shipment-service/issues/29) enlaza las otras veinte tareas como subissues y concentra el cierre integrado. No sustituye sus criterios de aceptación ni sus responsables.

Las dependencias están registradas mediante relaciones nativas de bloqueo, referencias en las issues y el campo Dependencias del Project. No hay ciclos en la planificación. Una dependencia no impide preparar pruebas o código con un contrato; sí exige que el proveedor esté disponible para verificar la integración y cerrar la tarea.

Todas las tareas comienzan Pendiente; la documentación pasa a En revisión al tener PR revisable. En curso indica trabajo iniciado, En revisión indica trabajo listo para revisar y Hecho exige aceptación, pruebas y PR integrado. El cierre es manual al integrar en develop mientras main siga como rama predeterminada.

No se fija una fecha de entrega ni una estimación temporal sin el calendario confirmado de la materia. La evidencia histórica del Sprint 01 se conserva como antecedente y no se presenta como una ejecución del Sprint 02.

## Trazabilidad de requisitos

| Requisito | Issues principales |
| --- | --- |
| Separación de repartidores y migración preservando UUID | [shipment-service#22](https://github.com/LogiTrack-Atom/shipment-service/issues/22), [shipment-service#23](https://github.com/LogiTrack-Atom/shipment-service/issues/23), [courier-service#1](https://github.com/LogiTrack-Atom/courier-service/issues/1) |
| Registro, consultas y actualización de repartidores | [courier-service#1](https://github.com/LogiTrack-Atom/courier-service/issues/1), [courier-service#2](https://github.com/LogiTrack-Atom/courier-service/issues/2), [courier-service#3](https://github.com/LogiTrack-Atom/courier-service/issues/3) |
| ACTIVO/INACTIVO y disponibilidad REST | [courier-service#3](https://github.com/LogiTrack-Atom/courier-service/issues/3), [shipment-service#24](https://github.com/LogiTrack-Atom/shipment-service/issues/24) |
| Reglas de envíos, historial y manejo de fallos remotos | [shipment-service#23](https://github.com/LogiTrack-Atom/shipment-service/issues/23), [shipment-service#24](https://github.com/LogiTrack-Atom/shipment-service/issues/24), [shipment-service#27](https://github.com/LogiTrack-Atom/shipment-service/issues/27) |
| Registro seguro, roles, login, JWT y usuario autenticado | [security-service#1](https://github.com/LogiTrack-Atom/security-service/issues/1), [security-service#2](https://github.com/LogiTrack-Atom/security-service/issues/2), [security-service#3](https://github.com/LogiTrack-Atom/security-service/issues/3), [security-service#4](https://github.com/LogiTrack-Atom/security-service/issues/4) |
| Autorización por rol y pertenencia; validación JWT local | [shipment-service#25](https://github.com/LogiTrack-Atom/shipment-service/issues/25), [courier-service#4](https://github.com/LogiTrack-Atom/courier-service/issues/4), [security-service#2](https://github.com/LogiTrack-Atom/security-service/issues/2) |
| Git de configuración y prueba independiente del servidor | [configserver-ms#1](https://github.com/LogiTrack-Atom/configserver-ms/issues/1), [configserver#1](https://github.com/LogiTrack-Atom/configserver/issues/1) |
| Propiedades de puerto, nombre, MySQL, URLs, logging y seguridad no sensible | [configserver-ms#1](https://github.com/LogiTrack-Atom/configserver-ms/issues/1), [courier-service#5](https://github.com/LogiTrack-Atom/courier-service/issues/5), [shipment-service#26](https://github.com/LogiTrack-Atom/shipment-service/issues/26), [security-service#4](https://github.com/LogiTrack-Atom/security-service/issues/4) |
| Perfiles dev/prod y consumo real al arrancar | [configserver#1](https://github.com/LogiTrack-Atom/configserver/issues/1), [courier-service#5](https://github.com/LogiTrack-Atom/courier-service/issues/5), [shipment-service#26](https://github.com/LogiTrack-Atom/shipment-service/issues/26), [security-service#4](https://github.com/LogiTrack-Atom/security-service/issues/4) |
| Cuatro Dockerfiles multietapa Java 25 e imágenes independientes | [configserver#1](https://github.com/LogiTrack-Atom/configserver/issues/1), [courier-service#6](https://github.com/LogiTrack-Atom/courier-service/issues/6), [security-service#4](https://github.com/LogiTrack-Atom/security-service/issues/4), [shipment-service#28](https://github.com/LogiTrack-Atom/shipment-service/issues/28) |
| Compose completo, tres MySQL y comunicación DNS | [shipment-service#28](https://github.com/LogiTrack-Atom/shipment-service/issues/28) |
| Docker Hub con versiones y arranque sin build local | [shipment-service#29](https://github.com/LogiTrack-Atom/shipment-service/issues/29) |
| Documentación, regresión, pruebas integradas y evidencia final | [shipment-service#21](https://github.com/LogiTrack-Atom/shipment-service/issues/21), [shipment-service#27](https://github.com/LogiTrack-Atom/shipment-service/issues/27), [courier-service#6](https://github.com/LogiTrack-Atom/courier-service/issues/6), [security-service#4](https://github.com/LogiTrack-Atom/security-service/issues/4), [shipment-service#29](https://github.com/LogiTrack-Atom/shipment-service/issues/29) |
