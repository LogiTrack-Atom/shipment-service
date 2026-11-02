# Sprint03 — Descubrimiento, Gateway y comunicación resiliente

Fecha de inicio: 2 de noviembre de 2026.

## Objetivo

Incorporar Eureka y API Gateway, migrar la consulta de disponibilidad a OpenFeign y
mantener las reglas de negocio, JWT, historial y persistencia de Sprint02.

El [Project común](https://github.com/orgs/LogiTrack-Atom/projects/1) conserva Sprint02
y agrega las vistas Sprint03 — Planificación y Sprint03 — Kanban. Las milestones
de los repositorios participantes identifican la misma entrega.

## Flujo de trabajo

1. Tomar la issue asignada y comprobar sus dependencias.
2. Crear una rama desde develop con el número real de issue: feature/<numero>-<descripcion>.
3. Pasar la tarjeta a En curso y desarrollar con pruebas y JavaDoc en cada archivo Java nuevo.
4. Abrir una PR hacia develop, enlazar la issue y registrar la validación efectivamente ejecutada.
5. Pasar a En revisión cuando la PR esté preparada. Resolver observaciones y completar checks.
6. Integrar en develop y comprobar criterios antes de cerrar la issue y pasar a Hecho.

Las ramas de corrección usan fix/<numero>-<descripcion>. Una issue coordinadora puede
enlazar PR de varios repositorios; usar referencias completas para evitar confundir números.
No se modifica main ni se cambian los tags de Sprint02. Una PR de avance documental
no cierra una issue si quedan criterios funcionales sin acreditar.

## Responsabilidades

| Integrante | Issues | Bloques principales |
| --- | --- | --- |
| MaxDZ-10 | 9 | Contratos, base y seguridad de Gateway, Feign, resiliencia, datos, integración y cierre |
| JMatiasF | 6 | Registro de courier, rutas, diagnóstico, Postman y ensayo |
| DiazGab | 5 | Eureka, configuración, registro de shipment/security, verificación y documentación |

## Secuencia y dependencias

La base y los contratos preceden a configuración y descubrimiento. Eureka y los clientes
habilitan la integración de Gateway/Feign. Luego se completa Compose y la aceptación.
Las dependencias reales se registran en GitHub, además del campo Dependencias del Project.
Las preparaciones aisladas pueden realizarse con fixtures; no acreditan integración real.

## Backlog

| ID | Issue | Trabajo | Responsable | Prioridad |
| --- | --- | --- | --- | --- |
| S03-01 | [shipment-service#39](https://github.com/LogiTrack-Atom/shipment-service/issues/39) | docs: verificar la base y acordar contratos del Sprint03 | MaxDZ-10 | Alta |
| S03-02 | [eureka-server#1](https://github.com/LogiTrack-Atom/eureka-server/issues/1) | feat: implementar Eureka Server con configuración remota y Docker | DiazGab | Alta |
| S03-03 | [courier-service#17](https://github.com/LogiTrack-Atom/courier-service/issues/17) | feat: registrar courier-service en Eureka | JMatiasF | Alta |
| S03-04 | [shipment-service#40](https://github.com/LogiTrack-Atom/shipment-service/issues/40) | feat: incorporar Eureka a shipment-service y security-service | DiazGab | Alta |
| S03-05 | [configserver-ms#13](https://github.com/LogiTrack-Atom/configserver-ms/issues/13) | feat: centralizar propiedades de Eureka y API Gateway | DiazGab | Alta |
| S03-06 | [api-gateway#1](https://github.com/LogiTrack-Atom/api-gateway/issues/1) | feat: crear la base WebFlux de API Gateway | MaxDZ-10 | Alta |
| S03-07 | [api-gateway#2](https://github.com/LogiTrack-Atom/api-gateway/issues/2) | feat: enrutar las APIs públicas mediante Eureka | JMatiasF | Alta |
| S03-08 | [api-gateway#3](https://github.com/LogiTrack-Atom/api-gateway/issues/3) | feat: validar JWT y autorizar operaciones en Gateway | MaxDZ-10 | Alta |
| S03-09 | [shipment-service#41](https://github.com/LogiTrack-Atom/shipment-service/issues/41) | refactor: consultar disponibilidad de courier con OpenFeign | MaxDZ-10 | Alta |
| S03-10 | [shipment-service#42](https://github.com/LogiTrack-Atom/shipment-service/issues/42) | feat: controlar errores y fallas de transporte de OpenFeign | MaxDZ-10 | Alta |
| S03-11 | [shipment-service#43](https://github.com/LogiTrack-Atom/shipment-service/issues/43) | feat: preparar datos reproducibles y restablecimiento de la demo | MaxDZ-10 | Alta |
| S03-12 | [courier-service#18](https://github.com/LogiTrack-Atom/courier-service/issues/18) | feat: consultar configuración remota mediante diagnóstico ADMIN | JMatiasF | Media |
| S03-13 | [shipment-service#44](https://github.com/LogiTrack-Atom/shipment-service/issues/44) | chore: integrar seis aplicaciones y tres MySQL en Compose | MaxDZ-10 | Alta |
| S03-14 | [shipment-service#45](https://github.com/LogiTrack-Atom/shipment-service/issues/45) | test: adaptar el flujo integrado Postman al Gateway | JMatiasF | Alta |
| S03-15 | [shipment-service#46](https://github.com/LogiTrack-Atom/shipment-service/issues/46) | test: completar rechazos de seguridad y negocio en Postman | JMatiasF | Alta |
| S03-16 | [shipment-service#47](https://github.com/LogiTrack-Atom/shipment-service/issues/47) | test: ejecutar aceptación integral de Sprint03 | MaxDZ-10 | Alta |
| S03-17 | [configserver-ms#14](https://github.com/LogiTrack-Atom/configserver-ms/issues/14) | test: verificar perfiles y revisión Git de configuración | DiazGab | Media |
| S03-18 | [shipment-service#48](https://github.com/LogiTrack-Atom/shipment-service/issues/48) | docs: actualizar arquitectura y guía de operación de Sprint03 | DiazGab | Media |
| S03-19 | [shipment-service#49](https://github.com/LogiTrack-Atom/shipment-service/issues/49) | docs: coordinar ensayo y video de Sprint03 | JMatiasF | Media |
| S03-20 | [shipment-service#50](https://github.com/LogiTrack-Atom/shipment-service/issues/50) | chore: cerrar entrega técnica y registrar versiones de Sprint03 | MaxDZ-10 | Alta |

## Base comprobada

Las pruebas unitarias y JavaDoc se ejecutaron sobre los commits identificados en
[base_verificada.json](base_verificada.json): shipment 132, courier 69, security 91
y Config Server 1; total 293, sin fallos, errores ni omisiones.

La integración con siete contenedores se ejecutó en GitHub Actions, porque Docker Engine
no está disponible localmente. La [evidencia integrada](base_integrada.json) registra
34 solicitudes aprobadas contra servicios reales, rechazos sin mutación, recuperación
de courier y persistencia tras recrear el stack. La auditoría del 27/10 permanece como
antecedente histórico separado.

Las suites completas de Java de Sprint02 también aprobaron en ejecuciones nuevas:
[shipment: 283](https://github.com/LogiTrack-Atom/shipment-service/actions/runs/41541364294),
[courier: 183](https://github.com/LogiTrack-Atom/courier-service/actions/runs/41541368729) y
[security: 182](https://github.com/LogiTrack-Atom/security-service/actions/runs/41541373459).
Con la prueba local de Config Server, son 649 pruebas Java. La validación corresponde
a los commits y escenarios identificados; no acredita una entrega de Sprint03 completa.

La comprobación entre servicios reales se ejecuta con
`python3 scripts/verificacion/sprint02_baseline.py`, también disponible en el workflow
**Regresión integrada de la base Sprint02**. Requiere Docker Compose y OpenSSL.
Descarga las cuatro imágenes históricas y usa Compose/esquemas del commit fijo
938496bae144d559187fa59368e155eea2525af9, aunque la infraestructura actual evolucione.
Si falta ese objeto Git localmente, recuperarlo con
`git fetch --no-tags --depth=1 origin 938496bae144d559187fa59368e155eea2525af9`.
Crea un proyecto con siete contenedores,
claves y credenciales efímeras. Comprueba asignación, inexistencia/inactividad,
caída y recuperación de courier, roles/pertenencia, entrega e historial persistente
tras recrear el stack. Solo retira los volúmenes creados para ese proyecto de prueba.
El informe sanitizado se guarda en target/sprint02-baseline.json y en el log del job.

## Contratos y aceptación

Los acuerdos técnicos figuran en [Contratos_tecnicos.md](Contratos_tecnicos.md).
Los criterios de cada issue son verificables y forman parte de su cierre. El ensayo
y la constancia de entrega académica se registran separados del cierre técnico.
