# Sprint04 · Pruebas de referencia de siete repositorios

Issue: [shipment-service#67](https://github.com/LogiTrack-Atom/shipment-service/issues/67). Fecha: 2026-11-09, America/Argentina/Buenos_Aires. Responsable: MaxDZ-10.

Las seis aplicaciones aprobaron `bash mvnw -B -ntp -o verify javadoc:javadoc` en clones independientes de los SHA inventariados. Esta ejecución es nueva; los resultados históricos de Sprint03 se usan solo como antecedentes. No modifica fuentes de aplicación ni aprueba por sí sola G0: faltan revisión contextual #68, hallazgos #69 y revalidación #70.

## Referencias probadas

| Repositorio | SHA |
|---|---|
| shipment-service | `3b3bb57b9397d8d9c7e49d97351e0f0104cfd77a` |
| courier-service | `003003d619d0479bc0577d839a580e61e51a8f0b` |
| security-service | `37bb9e3146010fc4f459fcca8a0ae970b5ce1c64` |
| api-gateway | `283cbf80423705b3768787b77c8d35f9a0fd6305` |
| eureka-server | `49f5271e2075fc1c9b5b6192ed1f7cb0da5bf24b` |
| configserver | `4d7b1fb484e3546a853bbd1dbd2ed04e24e78bd5` |
| configserver-ms | `a08f95ee8fa0bf412fb30f68776e9a8794f94110` |

## Pruebas Java y documentación

| Aplicación | Surefire | Failsafe | Fallos | Errores | Omitidas | verify y JavaDoc |
|---|---:|---:|---:|---:|---:|---|
| shipment-service | 171 | 153 | 0 | 0 | 0 | Aprobados |
| courier-service | 69 | 115 | 0 | 0 | 0 | Aprobados |
| security-service | 91 | 91 | 0 | 0 | 0 | Aprobados |
| api-gateway | 145 | 0 | 0 | 0 | 0 | Aprobados |
| eureka-server | 3 | 0 | 0 | 0 | 0 | Aprobados |
| configserver | 1 | 0 | 0 | 0 | 0 | Aprobados |

**Total: 839 pruebas Java**, sin fallos, errores u omisiones en la ejecución con Docker disponible. JavaDoc se generó mediante la configuración existente de cada POM. La presencia o generación de documentación no acredita su corrección semántica; esa revisión corresponde a #68. `configserver-ms` no se trató como aplicación Maven.

## Python, configuración y expiración

| Suite | Casos | Resultado |
|---|---:|---|
| scripts/demo/tests | 25 | Aprobados |
| scripts/compose/tests | 24 | Aprobados |
| scripts/verificacion/tests | 2 | Aprobados |
| scripts/migracion/tests | 19 | Aprobados, incluido probe JPA |

Total Python: **70 casos**. La migración usa dos MySQL efímeros y el proveedor courier `ed47e891f262f74ff7aa7f684842b80c409f39e6`, fijado por el workflow histórico, compilado con `test-compile dependency:build-classpath`. Ese proveedor es una fixture del ensayo, no la referencia actual auditada de courier.

Los 15 archivos YAML de `configserver-ms` se analizaron con PyYAML SafeLoader y rechazo explícito de claves duplicadas. Además se verificó consumo real mediante `verify_remote_config.py`, con el label `a08f95ee8fa0bf412fb30f68776e9a8794f94110`.

En Shipment, Courier, Security y Gateway aprobó `JwtSecurityTest.aplicaSesentaSegundosDeTolerancia`: con `Clock.fixed`, un JWT cuya expiración es t+900 se acepta en t+959 y se rechaza en t+961 (skew 60 s). La suite también cubre tokens válidos y vencidos fuera de tolerancia. No se alteró el reloj del host; no se afirma una prueba adicional del instante exacto t+960 ni del flujo React, todavía no implementado.

## Laboratorio de aceptación

Se ejecutó `manage_sprint03.py prepare --release-manifest docs/sprint03/entrega/versiones.json` en un clon nuevo dentro de `laboratorios/sprint04-67/shipment-service`. El directorio privado de estado pertenece exclusivamente a ese clon. Puertos dinámicos, claves y credenciales efímeras generados por la herramienta existente; no se copiaron los del equipo.

Después se ejecutaron `verify_remote_config.py`, `sprint03_acceptance.py --reset-after`, `sprint03_databases.py`, `security_fixture.py`, ambas colecciones Postman de Sprint03 con Newman 6.2.3 y `newman_summary.py`. El `destroy` final retiró solo el proyecto propio, con los controles de propiedad del CLI. Los reportes completos Newman y los archivos de claves/entorno permanecieron fuera del entregable público.

La topología/SQL se cargó desde el `source_commit` del manifiesto de entrega; no se confundió ese SHA con el HEAD auditado. Las seis imágenes se usaron con referencias inmutables y plataforma inspeccionada. Se contrastaron `src/main`, `pom.xml` y `Dockerfile` entre cada commit de construcción documentado y el SHA auditado: no hubo diferencias. Para Config Server, el commit de origen procede del manifiesto Sprint02; su imagen no declara revisión OCI. Esta equivalencia de rutas no es una reconstrucción reproducible binaria ni una atestación de cadena de suministro.

## Imágenes utilizadas

| Servicio | Plataforma | Referencia inmutable |
|---|---|---|
| configserver | linux/amd64 | `maxdz10/configserver:sprint02-1.0.0@sha256:1658bc0e413f5519087ab00ef0e170de31cb20a17b6b84aef08ca17acf19874b` |
| shipment-service | linux/amd64 | `maxdz10/shipment-service:sprint03-1.0.0@sha256:aa85d72dbb97bf0cc6c1c81d261fa892f312db922cee21e6cee086a8d4f81595` |
| courier-service | linux/amd64 | `maxdz10/courier-service:sprint03-1.0.0@sha256:a226ba795cea75de3343db6b5a0a0a053b36262c7b6c1290e485e55c6e4abf3f` |
| security-service | linux/amd64 | `maxdz10/security-service:sprint03-1.0.0@sha256:12353ae6ff6cdafc5f3b27de40da66587e5eaee893664a732e1a828e2c8095b8` |
| eureka-server | linux/amd64 | `maxdz10/eureka-server:sprint03-1.0.0@sha256:70a31b326a036de44fcd57238b3bf65f9f1a26e11735108cce1e944fea9dcdcb` |
| api-gateway | linux/amd64 | `maxdz10/api-gateway:sprint03-1.0.0@sha256:5c9496b6311b60f88382785093175ba92255a405f7a1036b8f1843f4e33663cf` |

## Evidencia pública detallada

### entorno.json

```json
{
  "java": [
    "openjdk version \"25.0.4.1\" 2026-08-18",
    "OpenJDK Runtime Environment (build 25.0.4.1+1-1-24.04.4-Ubuntu)",
    "OpenJDK 64-Bit Server VM (build 25.0.4.1+1-1-24.04.4-Ubuntu, mixed mode, sharing)"
  ],
  "maven": [
    "Apache Maven 3.10.0 (c43a36b8d67be7e0805a411bc0898af1a51f5472)",
    "Maven home: /home/maxdz/.m2/wrapper/dists/apache-maven-3.10.0/dae9a06d",
    "Java version: 25.0.4.1, vendor: Ubuntu, runtime: /usr/lib/jvm/java-25-openjdk-amd64",
    "Default locale: en, platform encoding: UTF-8, time zone: America/Argentina/Buenos_Aires"
  ],
  "python": [
    "Python 3.12.3"
  ],
  "node": [
    "v24.15.0"
  ],
  "docker": [
    "29.8.0"
  ],
  "mysql:8.4": "sha256:85b9bf2e29cf836ecb8c2a15a935d4ba0c606631dff1dd79531a11983c638f2a [\"mysql@sha256:85b9bf2e29cf836ecb8c2a15a935d4ba0c606631dff1dd79531a11983c638f2a\"]",
  "mysql:8.4.11": "sha256:0f51e63e537a1f176423fa03ef559aabe6fa4372a53160a44bc7e75da7d15364 [\"mysql@sha256:0f51e63e537a1f176423fa03ef559aabe6fa4372a53160a44bc7e75da7d15364\"]"
}
```

### topologia-equivalencia.json

```json
{
  "source_commit": "5b52e886a597f1c34927c99bf9a4435882a14f8f",
  "snapshot": "3b3bb57b9397d8d9c7e49d97351e0f0104cfd77a",
  "rutas_modificadas": []
}
```

### equivalencia-imagenes.json

```json
[
  {
    "repo": "shipment-service",
    "image_commit": "b2454e916332e4b0ec388782047fc63215f53da9",
    "snapshot_commit": "3b3bb57b9397d8d9c7e49d97351e0f0104cfd77a",
    "runtime_changed_paths": []
  },
  {
    "repo": "courier-service",
    "image_commit": "1cd721bea9c9897951c0b41fcceeb93ee8d88f59",
    "snapshot_commit": "003003d619d0479bc0577d839a580e61e51a8f0b",
    "runtime_changed_paths": []
  },
  {
    "repo": "security-service",
    "image_commit": "37bb9e3146010fc4f459fcca8a0ae970b5ce1c64",
    "snapshot_commit": "37bb9e3146010fc4f459fcca8a0ae970b5ce1c64",
    "runtime_changed_paths": []
  },
  {
    "repo": "eureka-server",
    "image_commit": "b3795f7f5486e5b11667e5e77ba559bc1958a010",
    "snapshot_commit": "49f5271e2075fc1c9b5b6192ed1f7cb0da5bf24b",
    "runtime_changed_paths": []
  },
  {
    "repo": "api-gateway",
    "image_commit": "736e69331e5faba956773d52f699b37847ac29f0",
    "snapshot_commit": "283cbf80423705b3768787b77c8d35f9a0fd6305",
    "runtime_changed_paths": []
  },
  {
    "repo": "configserver",
    "image_commit": "8e5cc47470279a90687a4901b149ccda6957971b",
    "snapshot_commit": "4d7b1fb484e3546a853bbd1dbd2ed04e24e78bd5",
    "runtime_changed_paths": [],
    "procedencia": "docs/sprint02/entrega/versiones.json"
  }
]
```

### aceptacion.json

```json
{
  "fecha_utc": "2026-11-09T06:45:36.809210+00:00",
  "verificador_sha256": "41980965f2be4f895d896ac191386e32ab13c7089701935e7b17a65a213dd560",
  "alcance": "demo Sprint03 propia; negocio por Gateway, Eureka y Feign reales",
  "config_label": "a08f95ee8fa0bf412fb30f68776e9a8794f94110",
  "imagenes": {
    "configserver": "maxdz10/configserver:sprint02-1.0.0@sha256:1658bc0e413f5519087ab00ef0e170de31cb20a17b6b84aef08ca17acf19874b",
    "shipment-service": "maxdz10/shipment-service:sprint03-1.0.0@sha256:aa85d72dbb97bf0cc6c1c81d261fa892f312db922cee21e6cee086a8d4f81595",
    "courier-service": "maxdz10/courier-service:sprint03-1.0.0@sha256:a226ba795cea75de3343db6b5a0a0a053b36262c7b6c1290e485e55c6e4abf3f",
    "security-service": "maxdz10/security-service:sprint03-1.0.0@sha256:12353ae6ff6cdafc5f3b27de40da66587e5eaee893664a732e1a828e2c8095b8",
    "eureka-server": "maxdz10/eureka-server:sprint03-1.0.0@sha256:70a31b326a036de44fcd57238b3bf65f9f1a26e11735108cce1e944fea9dcdcb",
    "api-gateway": "maxdz10/api-gateway:sprint03-1.0.0@sha256:5c9496b6311b60f88382785093175ba92255a405f7a1036b8f1843f4e33663cf"
  },
  "aprobado": true,
  "escenarios": [
    {
      "escenario": "prepare repetido conserva las identidades",
      "aprobado": true
    },
    {
      "escenario": "diagnóstico consumido prod",
      "aprobado": true
    },
    {
      "escenario": "identidades persistidas en prod",
      "aprobado": true
    },
    {
      "escenario": "diagnóstico consumido dev",
      "aprobado": true
    },
    {
      "escenario": "identidades persistidas en dev",
      "aprobado": true
    },
    {
      "escenario": "cuatro clientes registrados en Eureka",
      "aprobado": true
    },
    {
      "escenario": "nueve contenedores saludables",
      "aprobado": true
    },
    {
      "escenario": "courier detenido devuelve ApiError 503",
      "aprobado": true
    },
    {
      "escenario": "caída no modifica asignación, fechas, estado ni historial",
      "aprobado": true
    },
    {
      "escenario": "caída también impide EN_RUTA sin mutación",
      "aprobado": true
    },
    {
      "escenario": "Feign descubre una instancia con otro DNS y puerto",
      "aprobado": true
    },
    {
      "escenario": "Shipment conserva contenedor, imagen y proceso",
      "aprobado": true
    },
    {
      "escenario": "asignación persistida sin cambiar PENDIENTE",
      "aprobado": true
    },
    {
      "escenario": "EN_RUTA vuelve a consultar disponibilidad mediante Feign",
      "aprobado": true
    },
    {
      "escenario": "flujo completo e historial",
      "aprobado": true
    },
    {
      "escenario": "recrear contenedores conserva envío e historial",
      "aprobado": true
    },
    {
      "escenario": "tres bases conservan identidades",
      "aprobado": true
    },
    {
      "escenario": "seed rechaza reparar datos modificados implícitamente",
      "aprobado": true
    },
    {
      "escenario": "reset explícito rota claves y credenciales",
      "aprobado": true
    },
    {
      "escenario": "reset repone catálogo inicial sin duplicados",
      "aprobado": true
    }
  ],
  "limites": "500, timeout y contratos HTTP malformados se verifican en las suites Java de Feign con HTTP real y MySQL."
}
```

### bases.json

```json
{
  "aprobado": true,
  "alcance": "tres conexiones propias y seis rechazos MySQL 1045 entre contenedores",
  "resultados": [
    {
      "origen": "shipment",
      "destino": "shipment",
      "resultado": "esquema propio accesible"
    },
    {
      "origen": "shipment",
      "destino": "courier",
      "resultado": "autenticación cruzada rechazada"
    },
    {
      "origen": "shipment",
      "destino": "security",
      "resultado": "autenticación cruzada rechazada"
    },
    {
      "origen": "courier",
      "destino": "courier",
      "resultado": "esquema propio accesible"
    },
    {
      "origen": "courier",
      "destino": "shipment",
      "resultado": "autenticación cruzada rechazada"
    },
    {
      "origen": "courier",
      "destino": "security",
      "resultado": "autenticación cruzada rechazada"
    },
    {
      "origen": "security",
      "destino": "security",
      "resultado": "esquema propio accesible"
    },
    {
      "origen": "security",
      "destino": "shipment",
      "resultado": "autenticación cruzada rechazada"
    },
    {
      "origen": "security",
      "destino": "courier",
      "resultado": "autenticación cruzada rechazada"
    }
  ]
}
```

### postman-publico.json

```json
{
  "aprobado": true,
  "resultados": [
    {
      "coleccion": "LogiTrack Sprint03 — Seguridad y negocio",
      "pedidos": 48,
      "aserciones": 87,
      "fallos": 0,
      "aprobado": true,
      "ejecuciones": [
        {
          "nombre": "Login ADMIN",
          "http": 200
        },
        {
          "nombre": "Login CLIENTE",
          "http": 200
        },
        {
          "nombre": "Login REPARTIDOR",
          "http": 200
        },
        {
          "nombre": "Guardar envío antes de rechazos",
          "http": 200
        },
        {
          "nombre": "Guardar historial antes de rechazos",
          "http": 200
        },
        {
          "nombre": "Guardar beforeOther envío",
          "http": 200
        },
        {
          "nombre": "Guardar beforeOther historial",
          "http": 200
        },
        {
          "nombre": "Guardar beforeUnassignable envío",
          "http": 200
        },
        {
          "nombre": "Guardar beforeUnassignable historial",
          "http": 200
        },
        {
          "nombre": "Anónimo sin acceso",
          "http": 401
        },
        {
          "nombre": "Anónimo sin acceso / verificar envío",
          "http": 200
        },
        {
          "nombre": "Anónimo sin acceso / verificar historial",
          "http": 200
        },
        {
          "nombre": "CLIENTE no asigna repartidor",
          "http": 403
        },
        {
          "nombre": "CLIENTE no asigna repartidor / verificar envío",
          "http": 200
        },
        {
          "nombre": "CLIENTE no asigna repartidor / verificar historial",
          "http": 200
        },
        {
          "nombre": "CLIENTE no lee envío ajeno",
          "http": 403
        },
        {
          "nombre": "CLIENTE no lee envío ajeno / verificar envío",
          "http": 200
        },
        {
          "nombre": "CLIENTE no lee envío ajeno / verificar historial",
          "http": 200
        },
        {
          "nombre": "CLIENTE no administra Courier",
          "http": 403
        },
        {
          "nombre": "CLIENTE no administra Courier / verificar envío",
          "http": 200
        },
        {
          "nombre": "CLIENTE no administra Courier / verificar historial",
          "http": 200
        },
        {
          "nombre": "CLIENTE sin diagnóstico",
          "http": 403
        },
        {
          "nombre": "CLIENTE sin diagnóstico / verificar envío",
          "http": 200
        },
        {
          "nombre": "CLIENTE sin diagnóstico / verificar historial",
          "http": 200
        },
        {
          "nombre": "REPARTIDOR sin diagnóstico",
          "http": 403
        },
        {
          "nombre": "REPARTIDOR sin diagnóstico / verificar envío",
          "http": 200
        },
        {
          "nombre": "REPARTIDOR sin diagnóstico / verificar historial",
          "http": 200
        },
        {
          "nombre": "Repartidor inexistente",
          "http": 404
        },
        {
          "nombre": "Repartidor inexistente / verificar envío",
          "http": 200
        },
        {
          "nombre": "Repartidor inexistente / verificar historial",
          "http": 200
        },
        {
          "nombre": "Repartidor INACTIVO",
          "http": 422
        },
        {
          "nombre": "Repartidor INACTIVO / verificar envío",
          "http": 200
        },
        {
          "nombre": "Repartidor INACTIVO / verificar historial",
          "http": 200
        },
        {
          "nombre": "Transición no permitida",
          "http": 422
        },
        {
          "nombre": "Transición no permitida / verificar envío",
          "http": 200
        },
        {
          "nombre": "Transición no permitida / verificar historial",
          "http": 200
        },
        {
          "nombre": "JWT manipulado",
          "http": 401
        },
        {
          "nombre": "JWT manipulado / verificar envío",
          "http": 200
        },
        {
          "nombre": "JWT manipulado / verificar historial",
          "http": 200
        },
        {
          "nombre": "JWT vencido con firma válida",
          "http": 401
        },
        {
          "nombre": "JWT vencido con firma válida / verificar envío",
          "http": 200
        },
        {
          "nombre": "JWT vencido con firma válida / verificar historial",
          "http": 200
        },
        {
          "nombre": "Envío CANCELADO no asignable",
          "http": 422
        },
        {
          "nombre": "Envío CANCELADO no asignable / verificar envío",
          "http": 200
        },
        {
          "nombre": "Envío CANCELADO no asignable / verificar historial",
          "http": 200
        },
        {
          "nombre": "Diagnóstico no expone Environment",
          "http": 200
        },
        {
          "nombre": "Envío sin mutaciones",
          "http": 200
        },
        {
          "nombre": "Historial sin mutaciones",
          "http": 200
        }
      ]
    },
    {
      "coleccion": "LogiTrack Sprint03 — Flujo por Gateway",
      "pedidos": 17,
      "aserciones": 30,
      "fallos": 0,
      "aprobado": true,
      "ejecuciones": [
        {
          "nombre": "01 - Login ADMIN",
          "http": 200
        },
        {
          "nombre": "02 - Registro CLIENTE",
          "http": 201
        },
        {
          "nombre": "03 - Login CLIENTE",
          "http": 200
        },
        {
          "nombre": "04 - Alta usuario REPARTIDOR",
          "http": 201
        },
        {
          "nombre": "05 - Login REPARTIDOR",
          "http": 200
        },
        {
          "nombre": "06 - Alta repartidor",
          "http": 201
        },
        {
          "nombre": "07 - Vincular identidad en courier",
          "http": 200
        },
        {
          "nombre": "08 - Registrar asociación verificada en shipment",
          "http": 200
        },
        {
          "nombre": "09 - CLIENTE registra envío",
          "http": 201
        },
        {
          "nombre": "10 - ADMIN asigna repartidor",
          "http": 200
        },
        {
          "nombre": "10b - Confirmar asignación persistida",
          "http": 200
        },
        {
          "nombre": "11 - REPARTIDOR actualiza a EN_RUTA",
          "http": 200
        },
        {
          "nombre": "12 - REPARTIDOR actualiza a ENTREGADO",
          "http": 200
        },
        {
          "nombre": "13 - CLIENTE consulta historial",
          "http": 200
        },
        {
          "nombre": "14 - Acceso anónimo rechazado",
          "http": 401
        },
        {
          "nombre": "15 - CLIENTE no administra asignaciones",
          "http": 403
        },
        {
          "nombre": "16 - Configuración consumida ADMIN",
          "http": 200
        }
      ]
    }
  ]
}
```

## Incidencias del entorno y alcance pendiente

Antes del reinicio, Docker estaba detenido: las integraciones fallaron al no encontrar un entorno Docker válido. Los primeros archivos exportados mediante `git archive` carecían de metadatos Git requeridos por pruebas Python; el sandbox también impedía abrir sockets locales. Se repitieron esas suites en clones Git independientes con los permisos de ejecución necesarios y aprobaron.

El primer intento de aceptación desde `/tmp` falló porque Docker Desktop no podía montar sus bind sources. Se retiraron los recursos de ese intento con el CLI y se repitió desde la carpeta de trabajo compartida; la ejecución final aprobó. Estos fallos no se clasifican como defectos del backend. Se conservan los logs locales de ambos intentos para trazabilidad; no se ocultan con exclusiones de pruebas o cambios funcionales.

Los siete checkouts originales conservan SHA y estado versionado limpio. La demo preexistente del Sprint03 no fue objetivo de las llamadas, resets ni limpieza; se comprobó que su proceso Gateway conservó identidad y fecha de arranque durante el ensayo. Esto no es una comparación exhaustiva de todos sus datos persistidos.

La evidencia valida los escenarios ejecutados y la base técnica actual. No demuestra ausencia de vulnerabilidades, revisión completa de JavaDoc, ausencia de secretos históricos ni cumplimiento de React/RabbitMQ futuros. Esos puntos permanecen en #68 y en las issues funcionales correspondientes. #66/PR #82 y esta issue continúan sujetas a revisión e integración; #70 decide G0.

Los logs locales, resultados JUnit y JSON de apoyo están en `output/sprint04-67` del espacio de trabajo. No son enlaces a artefactos públicos de CI ni se atribuyen a una corrida remota. Este informe incorpora los resúmenes públicos para que la PR pueda revisarse por sí sola.
