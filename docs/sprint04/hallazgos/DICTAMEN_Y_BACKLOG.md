# Dictamen y backlog de correcciones justificadas · Sprint04

[S04-00-D #69](https://github.com/LogiTrack-Atom/shipment-service/issues/69) · 2026-11-09 · Responsable: MaxDZ-10 · Organización: [LogiTrack-Atom](https://github.com/LogiTrack-Atom).

El diagnóstico produce **ocho hallazgos y nueve tareas propuestas**: cuatro FIX, dos VAL y tres DEBT; no se justifica ninguna REF. Este entregable consolida decisiones para revisión y no ejecuta correcciones. El dictamen no aprueba G0 ni declara integradas las PR anteriores. No se identificó un hallazgo crítico P0/P1 confirmado; las alertas de dependencias conservan incertidumbre de exposición y requieren evaluación antes de decidir su aceptación.

## Base, evidencia y límites

Se reutilizan el [inventario #66](https://github.com/LogiTrack-Atom/shipment-service/pull/82), las [pruebas #67](https://github.com/LogiTrack-Atom/shipment-service/pull/83) y la [auditoría #68](https://github.com/LogiTrack-Atom/shipment-service/pull/84). La auditoría se fija al commit `9f9594fbd9f5420c335cb7d40acf24687d2948f2`, con [informe](https://github.com/LogiTrack-Atom/shipment-service/blob/9f9594fbd9f5420c335cb7d40acf24687d2948f2/docs/sprint04/auditoria-contextual/INFORME.md), [451 filas de matriz](https://github.com/LogiTrack-Atom/shipment-service/blob/9f9594fbd9f5420c335cb7d40acf24687d2948f2/docs/sprint04/auditoria-contextual/MATRIZ.csv) y [evidencias sanitizadas](https://github.com/LogiTrack-Atom/shipment-service/blob/9f9594fbd9f5420c335cb7d40acf24687d2948f2/docs/sprint04/auditoria-contextual/EVIDENCIAS.json). Las ubicaciones siguientes enlazan fuentes con SHA inmutable; no dependen del avance futuro de develop.

En #67 aprobaron 839 casos Java, 70 Python, 65 peticiones Postman y 117 aserciones. No se repitieron suites ni scanners en #69. La aprobación de una suite no demuestra que cada ensayo alcance el escenario anunciado, como muestra AUD-68-08. Doclint de #67 cubrió producción; no JavaDoc de tests. La auditoría contó 159 tipos de producción documentados y 82 de 83 tipos de pruebas documentados.

Los resultados de secretos no confirmaron credenciales reales: tres coincidencias eran SHA Git públicos verificados. No se reproducen valores sensibles ni se presume vigencia por presencia histórica. La búsqueda se limitó a refs locales alcanzables y reglas de Gitleaks 8.30.1; no acredita ausencia universal. Dependencias: 243 coordenadas compile/runtime consultadas en OSV a las 09:56:04 UTC del 2026-11-09; ocho coordenadas y 14 avisos únicos coincidentes, sin retirada ni paginación pendiente en esa consulta. No cubre pruebas, plugins, SO/JDK, bases Docker, catálogo completo de fabricantes ni alcanzabilidad completa de terceros.

## Dictamen de los siete repositorios

| Repositorio / SHA auditado | Dictamen acotado y acciones |
| --- | --- |
| [shipment-service](https://github.com/LogiTrack-Atom/shipment-service/tree/3b3bb57b9397d8d9c7e49d97351e0f0104cfd77a) · `3b3bb57b9397d8d9c7e49d97351e0f0104cfd77a` | Defecto confirmado del ensayo de migración (FIX-01), JavaDoc de fixture (FIX-03), alerta transversal (VAL-01), contrato de longitudes ya manejado (VAL-02) y checksum opcional (DEBT-01). No se confirmó un nuevo fallo de rollback del dominio Java. |
| [courier-service](https://github.com/LogiTrack-Atom/courier-service/tree/003003d619d0479bc0577d839a580e61e51a8f0b) · `003003d619d0479bc0577d839a580e61e51a8f0b` | Defecto de referencia OpenAPI (FIX-02), precisión documental (FIX-04), alerta transversal (VAL-01) y checksum opcional (DEBT-01). El endpoint administrativo sigue protegido; no se demostró bypass. |
| [security-service](https://github.com/LogiTrack-Atom/security-service/tree/37bb9e3146010fc4f459fcca8a0ae970b5ce1c64) · `37bb9e3146010fc4f459fcca8a0ae970b5ce1c64` | Sin corrección funcional específica justificada por la lectura actual. Alerta transversal VAL-01 y checksum opcional DEBT-01. Esto no acredita protección universal ni revocación inmediata. |
| [api-gateway](https://github.com/LogiTrack-Atom/api-gateway/tree/283cbf80423705b3768787b77c8d35f9a0fd6305) · `283cbf80423705b3768787b77c8d35f9a0fd6305` | Sin corrección funcional específica justificada por la lectura actual. Alerta transversal VAL-01 y checksum opcional DEBT-01. No se propone cambiar rutas o filtros sin un defecto reproducible. |
| [eureka-server](https://github.com/LogiTrack-Atom/eureka-server/tree/49f5271e2075fc1c9b5b6192ed1f7cb0da5bf24b) · `49f5271e2075fc1c9b5b6192ed1f7cb0da5bf24b` | Ajuste documental FIX-04, alerta transversal VAL-01, checksum opcional DEBT-01 y bases por digest opcionales DEBT-02. Sonda 8065 coherente con configuración actual. |
| [configserver](https://github.com/LogiTrack-Atom/configserver/tree/4d7b1fb484e3546a853bbd1dbd2ed04e24e78bd5) · `4d7b1fb484e3546a853bbd1dbd2ed04e24e78bd5` | Sin corrección funcional específica justificada por la lectura actual. Alerta transversal VAL-01 y checksum opcional DEBT-01; distinguir Jackson 3 y no aplicar todos los avisos a todos los repositorios. |
| [configserver-ms](https://github.com/LogiTrack-Atom/configserver-ms/tree/a08f95ee8fa0bf412fb30f68776e9a8794f94110) · `a08f95ee8fa0bf412fb30f68776e9a8794f94110` | Ajuste de tabla documental FIX-04. Sin cambio justificado de propiedades funcionales. N/A Maven Wrapper y grafo de ejecución propio: es repositorio de configuración, no aplicación Maven. |

## Hallazgos verificables

P2/P3 son prioridades internas del proyecto, no CVSS. Un defecto es una contradicción confirmada entre comportamiento/artefacto y su contrato; una alerta de dependencia confirma versión en un rango, no explotación; una mejora o deuda no constituye un defecto funcional por sí sola.

### AUD-68-08 · Defecto de prueba · P2 · FIX-01

Ubicación: [shipment-service/scripts/migracion/tests/test_migracion.py:213](https://github.com/LogiTrack-Atom/shipment-service/blob/3b3bb57b9397d8d9c7e49d97351e0f0104cfd77a/scripts/migracion/tests/test_migracion.py#L213); rechazo de triggers en [shipment-service/scripts/migracion/repartidores.py:183](https://github.com/LogiTrack-Atom/shipment-service/blob/3b3bb57b9397d8d9c7e49d97351e0f0104cfd77a/scripts/migracion/repartidores.py#L183) y orden guard/apply en [shipment-service/scripts/migracion/repartidores.py:340](https://github.com/LogiTrack-Atom/shipment-service/blob/3b3bb57b9397d8d9c7e49d97351e0f0104cfd77a/scripts/migracion/repartidores.py#L340).

El ensayo instala un trigger que pretende fallar en el segundo INSERT y llama al CLI esperando error. El CLI rechaza el trigger antes de `apply`; destino y registro vacíos también satisfacen las aserciones sin ejecutar el lote. Impacto confirmado: falta evidencia del rollback intermedio anunciado; no se confirmó corrupción de datos. La auditoría ejecutó el guard real con respuestas sintéticas y observó consultas de tablas/triggers y rechazo, sin un nuevo ensayo MySQL. La prueba Java de transacciones sí fuerza SQL 1644 y no presenta este problema.

Corrección mínima: conservar el rechazo del CLI productivo y cambiar exclusivamente la preparación/inyección del ensayo para alcanzar `apply` después de validar el esquema. Una alternativa a revisar es invocar la función de aplicación sobre el entorno de ensayo validado e instalar allí el fallo controlado. No deshabilitar guardas ni limitarse a renombrar el test. Verificar primer INSERT alcanzado, fallo SQL posterior específico, filas y ledger revertidos desde otra conexión, origen intacto y cleanup propio.

### AUD-68-07 · Defecto de contrato documental · P2 · FIX-02

Ubicación: [courier-service/docs/openapi.json:884](https://github.com/LogiTrack-Atom/courier-service/blob/003003d619d0479bc0577d839a580e61e51a8f0b/docs/openapi.json#L884) frente a [courier-service/docs/openapi.json:1129](https://github.com/LogiTrack-Atom/courier-service/blob/003003d619d0479bc0577d839a580e61e51a8f0b/docs/openapi.json#L1129). La referencia `bearerAuth` no coincide con `BearerAuth`. Impacto: consumidores/generadores pueden interpretar incorrectamente la seguridad de esa operación. Código y pruebas exigen ADMIN; no hay bypass demostrado.

Corrección mínima: alinear el nombre del esquema en la fuente que produce el contrato y regenerar el JSON si corresponde. Si el origen ya es correcto, corregir solo el artefacto versionado. Aceptación: todas las referencias de seguridad resuelven al esquema declarado y continúan los casos 401/403/200 del endpoint; no se modifica autorización. El validador acotado de #68 verifica referencias locales y nombres, no todo el estándar OpenAPI.

### AUD-68-03 · Alerta de dependencias · P2 de mantenimiento · VAL-01 + DEBT-03

Ubicaciones: seis POM indicados abajo y árboles efectivos preservados en EVIDENCIAS.json. Los avisos afectan versiones resueltas de HttpClient, Jackson 2/3, Tomcat, FreeMarker y Guava según aplicación. Ejemplos: HttpClient 4.5.3 por Eureka Client 2.0.6; Tomcat 11.0.24 en MVC, no Gateway/Netty; FreeMarker 2.3.34 y Guava 33.0.0-jre en Eureka. No extrapolar una coincidencia a todos los repositorios.

- [shipment-service/pom.xml:1](https://github.com/LogiTrack-Atom/shipment-service/blob/3b3bb57b9397d8d9c7e49d97351e0f0104cfd77a/pom.xml#L1); versión efectiva y cadena: `verificaciones.coordenadas_y_arboles` en EVIDENCIAS.json de #68.
- [courier-service/pom.xml:1](https://github.com/LogiTrack-Atom/courier-service/blob/003003d619d0479bc0577d839a580e61e51a8f0b/pom.xml#L1); versión efectiva y cadena: `verificaciones.coordenadas_y_arboles` en EVIDENCIAS.json de #68.
- [security-service/pom.xml:1](https://github.com/LogiTrack-Atom/security-service/blob/37bb9e3146010fc4f459fcca8a0ae970b5ce1c64/pom.xml#L1); versión efectiva y cadena: `verificaciones.coordenadas_y_arboles` en EVIDENCIAS.json de #68.
- [api-gateway/pom.xml:1](https://github.com/LogiTrack-Atom/api-gateway/blob/283cbf80423705b3768787b77c8d35f9a0fd6305/pom.xml#L1); versión efectiva y cadena: `verificaciones.coordenadas_y_arboles` en EVIDENCIAS.json de #68.
- [eureka-server/pom.xml:1](https://github.com/LogiTrack-Atom/eureka-server/blob/49f5271e2075fc1c9b5b6192ed1f7cb0da5bf24b/pom.xml#L1); versión efectiva y cadena: `verificaciones.coordenadas_y_arboles` en EVIDENCIAS.json de #68.
- [configserver/pom.xml:1](https://github.com/LogiTrack-Atom/configserver/blob/4d7b1fb484e3546a853bbd1dbd2ed04e24e78bd5/pom.xml#L1); versión efectiva y cadena: `verificaciones.coordenadas_y_arboles` en EVIDENCIAS.json de #68.

Impacto potencial condicionado por uso/configuración; no se realizó explotación ni se descartaron exhaustivamente recorridos internos de terceros. Las APIs propias usan Bearer; no se observaron configuración propia FORM/DIGEST, ObjectInputStream ni tipado polimórfico habilitado. Esa ausencia no prueba mitigación universal.

Corrección todavía no elegida. VAL-01 debe contrastar cada aviso con fuente primaria vigente y grafo, evaluar condiciones, compatibilidad Boot/Cloud/BOM y decidir actualizar o diferir con causa verificable. No inventar CVE ni fijar una versión solo por ser el umbral de un aviso anterior. La actualización queda en DEBT-03 como trabajo condicionado a ese dictamen; no es permiso de cambiar seis POM automáticamente.

### AUD-68-01 · Mejora de contrato / decisión · P3 · VAL-02

Ubicaciones: [shipment-service/src/main/java/com/logitrack_atom/shipment_service/dto/request/ClienteRequest.java:23](https://github.com/LogiTrack-Atom/shipment-service/blob/3b3bb57b9397d8d9c7e49d97351e0f0104cfd77a/src/main/java/com/logitrack_atom/shipment_service/dto/request/ClienteRequest.java#L23), [shipment-service/src/main/java/com/logitrack_atom/shipment_service/dto/request/DireccionRequest.java:16](https://github.com/LogiTrack-Atom/shipment-service/blob/3b3bb57b9397d8d9c7e49d97351e0f0104cfd77a/src/main/java/com/logitrack_atom/shipment_service/dto/request/DireccionRequest.java#L16) y regresión existente [shipment-service/src/test/java/com/logitrack_atom/shipment_service/integration/ShipmentApiIT.java:157](https://github.com/LogiTrack-Atom/shipment-service/blob/3b3bb57b9397d8d9c7e49d97351e0f0104cfd77a/src/test/java/com/logitrack_atom/shipment_service/integration/ShipmentApiIT.java#L157). Bean Validation admite 256 caracteres, pero la prueba existente aprobada devuelve 409 sin altas parciales. No se confirmó 500 ni ausencia de manejo transaccional. Se reemplaza la clasificación preliminar P2 del primer comentario de avance de #68 por este dictamen P3 basado en evidencia más completa; no se borra ese antecedente fechado.

Decisión mínima: conservar el contrato vigente con su justificación o adoptar validación anticipada (por ejemplo, @Size y 400) alineando API, OpenAPI y pruebas. Si se adopta, probar 255/256 caracteres en campos afectados y ausencia de escrituras parciales. No cambiar silenciosamente 409 por 400 como si fuera una corrección sin impacto externo.

### AUD-68-02 · Documentación de pruebas · P3 · FIX-03

Ubicación: [shipment-service/src/test/java/com/logitrack_atom/shipment_service/integration/IntegrationFixtures.java:12](https://github.com/LogiTrack-Atom/shipment-service/blob/3b3bb57b9397d8d9c7e49d97351e0f0104cfd77a/src/test/java/com/logitrack_atom/shipment_service/integration/IntegrationFixtures.java#L12). Falta JavaDoc de clase en el fixture. Impacto: incumple la uniformidad documental solicitada; no modifica comportamiento. Corrección mínima: documentar responsabilidad y consumo, sin inventar autoría ni alterar datos. Aceptación: 83/83 tipos de tests documentados y diff solo documental; no repetir integración SQL por añadir un comentario.

### AUD-68-04 · Documentación desactualizada · P3 · FIX-04

Ubicaciones: [eureka-server/README.md:13](https://github.com/LogiTrack-Atom/eureka-server/blob/49f5271e2075fc1c9b5b6192ed1f7cb0da5bf24b/README.md#L13), [configserver-ms/README.md:11](https://github.com/LogiTrack-Atom/configserver-ms/blob/a08f95ee8fa0bf412fb30f68776e9a8794f94110/README.md#L11) y [courier-service/src/main/java/com/logitrack_atom/courier_service/service/impl/RepartidorServiceImpl.java:22](https://github.com/LogiTrack-Atom/courier-service/blob/003003d619d0479bc0577d839a580e61e51a8f0b/src/main/java/com/logitrack_atom/courier_service/service/impl/RepartidorServiceImpl.java#L22). README de Eureka aún lo declara preparado para desarrollar; tabla de configuración omite Gateway/Eureka; JavaDoc del servicio de repartidores menciona altas/lecturas y omite operaciones implementadas.

Impacto: guía operativa y responsabilidades incompletas. Corregir esos textos vigentes y contrastar con configuración/métodos actuales. No reescribir evidencia histórica ni cambiar código por uniformidad de estilo. Aceptación: tabla con aplicaciones realmente presentes, guía coherente y JavaDoc del servicio que describa su responsabilidad real. Generar JavaDoc de Courier si se modifica su comentario de producción; para README bastan revisión de enlaces/contenido y diff.

### AUD-68-05 · Endurecimiento opcional · P3 · DEBT-01

Ubicación repetida en seis aplicaciones: `.mvn/wrapper/maven-wrapper.properties:3`; scripts `mvnw` y `mvnw.cmd` son consumidores. No hay `distributionSha256Sum`. No se evidenció una descarga comprometida. Referencia representativa: [shipment-service/.mvn/wrapper/maven-wrapper.properties:3](https://github.com/LogiTrack-Atom/shipment-service/blob/3b3bb57b9397d8d9c7e49d97351e0f0104cfd77a/.mvn/wrapper/maven-wrapper.properties#L3).

Valorar checksum de la distribución exacta, verificado contra fuente oficial y capacidad del wrapper actual en POSIX/Windows. No sustituir Maven ni editar scripts por inercia. Aceptación si se implementa: distribución válida aceptada y checksum incorrecto rechazado en entorno aislado, sin borrar cachés del usuario. Se puede diferir con motivo porque es endurecimiento adicional, no incidente confirmado.

### AUD-68-06 · Reproducibilidad opcional · P3 · DEBT-02

Ubicación: [eureka-server/Dockerfile:2](https://github.com/LogiTrack-Atom/eureka-server/blob/49f5271e2075fc1c9b5b6192ed1f7cb0da5bf24b/Dockerfile#L2); segunda base en línea 13 y sonda en 26–27. Los tags base son mutables. No se probó vulnerabilidad de esas imágenes ni se observó un fallo de puerto: 8065 coincide con configuración actual.

Valorar digest verificado y compatible con plataforma, conservando actualización deliberada de bases; documentar dependencia de la sonda respecto del puerto. Aceptación si se implementa: build y salud de la imagen nueva, digest/procedencia registrados; las imágenes históricas no se retaggean. Evitar una refactorización de sondas por un fallo que no existe.

## Backlog propuesto para revisión

Estos identificadores son tareas del dictamen, **no números de issues GitHub**. No se crean ramas ni se aplican correcciones desde este documento. Responsable propuesto: MaxDZ-10, manteniendo la asignación de #69 y la base de React reservada a GabDiaz. Las PR de implementación deberán enlazar #69 y obtener el número real de su issue antes de nombrar su rama.

| ID / clase | Origen | Resultado concreto / dependencias | Prioridad y decisión |
| --- | --- | --- | --- |
| FIX-01 · corrección | AUD-68-08 | Reparar ensayo de rollback intermedio sin debilitar guardas. Prueba MySQL con DML alcanzado, fallo SQL y reversión de filas/ledger. | P2; proponer antes de G0. |
| FIX-02 · corrección | AUD-68-07 | Corregir referencia de esquema de seguridad de Courier, validar contrato y conservar autorización. | P2; proponer antes de G0. |
| FIX-03 · corrección documental | AUD-68-02 | JavaDoc de IntegrationFixtures; comprobación acotada 83/83. | P3; proponer completar calidad documental antes de G0. |
| FIX-04 · corrección documental | AUD-68-04 | Ajustar README de Eureka/configserver-ms y JavaDoc de Courier. Puede requerir tres PR enlazadas a la misma tarea coordinadora. | P3; proponer junto a cierre documental. |
| VAL-01 · validación | AUD-68-03 | Matriz por aviso/coordenada/repo: condiciones, alcance, fuentes primarias, compatibilidad y decisión explícita. | P2; resolver evaluación antes de decidir G0, sin presumir bloqueo por mera coincidencia. |
| VAL-02 · validación de decisión | AUD-68-01 | Elegir conservar 409 o cambiar contrato a validación anticipada; documentar impacto y casos 255/256 si se adopta. | P3; mantener 409 es opción válida justificada. |
| DEBT-01 · deuda opcional | AUD-68-05 | Checksum de Maven Wrapper en seis apps solo si se acepta endurecimiento; prueba aislada válida/inválida. | P3; diferible con motivo y responsable. |
| DEBT-02 · deuda opcional | AUD-68-06 | Fijar bases Eureka por digest y documentar sonda solo si se acepta; build/salud de nueva imagen. | P3; diferible con motivo y responsable. |
| DEBT-03 · mantenimiento condicionado | AUD-68-03 | Actualización compatible y coherente de dependencias/BOM, **solo después de VAL-01**; reclasificar prioridad según su resultado, probar integración afectada y publicar imágenes nuevas si cambia runtime. | P2 inicial; no es diferimiento aceptado ni autorización automática. |

**REF: ninguna tarea justificada.** No se propone reestructurar capas, retirar asociaciones locales, mover llamadas fuera de transacciones ni eliminar clases indirectamente usadas sin un defecto y una prueba que lo respalden. La distinción FIX/VAL/REF/DEBT clasifica el trabajo y no convierte una deuda en riesgo aceptado.

## Secuencia, revisión y pase G0

1. Revisar este dictamen y contrastarlo con los entregables de #66–#68. Los hallazgos críticos, si aparecen al confirmar exposición, deben revisarse con otro integrante; propuesta de revisor técnico: JMatiasF. No se afirma que esa revisión humana haya ocurrido ni se envía una solicitud en su nombre.
2. Tras la decisión de revisión, materializar las correcciones aceptadas en issues por repositorio, con enlaces al hallazgo y PR hacia develop. FIX-01/FIX-02 son cambios acotados; VAL-01 debe preceder a una actualización de plataforma. Las deudas opcionales pueden conservarse diferidas con causa, responsable y condición de revisión.
3. Cada cambio aporta su prueba proporcional. No repetir todas las suites por comentarios o un README. Si se cambian dependencias/runtime, revalidar los recorridos afectados de Config/Git, Eureka, Feign, Gateway y seguridad, y el grafo final; registrar fuentes, imágenes y configuración reales.
4. #70 compara baseline inicial/final y ejecuta la regresión transversal exigida. No aprobar G0 con pruebas críticas de seguridad/datos pendientes o riesgos bloqueantes sin resolver. Una alerta sin explotación demostrada requiere decisión fundada; tampoco justifica afirmar ausencia de riesgo. Mantener Sprint03 y sus referencias históricos intactos.

## Verificación de este entregable

Se validaron las ocho referencias de hallazgo, la relación a nueve tareas, los siete SHA y las rutas/líneas de los enlaces de fuente contra los checkouts auditados. Se verificó que los siete HEAD mantienen sus fuentes limpias. No hubo tests nuevos, actualización de dependencias, reconstrucción de imágenes ni escaneo de secretos adicional: la evidencia reutilizada tiene las fechas y límites anteriores.

Rama de trabajo: `feature/69-hallazgos-backlog`, creada desde Development de #69 con base develop. Entregable para PR hacia develop y estado **En revisión**. Esta condición indica que el dictamen es revisable; no aprueba automáticamente sus propuestas, no ejecuta correcciones y no completa #70.
