# Auditoría contextual de siete repositorios · Sprint04

Issue: [S04-00-C #68](https://github.com/LogiTrack-Atom/shipment-service/issues/68). Fecha: 2026-11-09.

El diagnóstico del inventario queda terminado: **451 archivos revisados en siete repositorios**, con ocho observaciones para evaluar en #69. No se modificaron fuentes funcionales, configuración desplegada, referencias históricas ni datos compartidos. Este resultado permite revisar la auditoría; no acredita la resolución de sus hallazgos ni el pase G0, que corresponde a #70.

## Base reproducible

| Repositorio | SHA auditado |
| --- | --- |
| shipment-service | 3b3bb57b9397d8d9c7e49d97351e0f0104cfd77a |
| courier-service | 003003d619d0479bc0577d839a580e61e51a8f0b |
| security-service | 37bb9e3146010fc4f459fcca8a0ae970b5ce1c64 |
| api-gateway | 283cbf80423705b3768787b77c8d35f9a0fd6305 |
| eureka-server | 49f5271e2075fc1c9b5b6192ed1f7cb0da5bf24b |
| configserver | 4d7b1fb484e3546a853bbd1dbd2ed04e24e78bd5 |
| configserver-ms | a08f95ee8fa0bf412fb30f68776e9a8794f94110 |

[MATRIZ.csv](MATRIZ.csv) conserva repo/SHA/ruta, uso, documentación, configuración, arquitectura, seguridad, datos/pruebas, evidencia y conclusión de cada archivo. [EVIDENCIAS.json](EVIDENCIAS.json) consolida los papeles contextuales, verificaciones, avisos, procedencia de herramientas y límites. Los nombres de archivos auxiliares citados en la matriz se resuelven como claves dentro de ese JSON; no requieren publicar copias adicionales.

## Método y resultados

Se cruzaron responsabilidades con sus consumidores: componentes Spring, proxies de repositorios, JPA, SpEL, perfiles, reflexión/configuración y llamadas desde Maven, Docker, scripts y pruebas. No se propuso eliminar código por ausencia de llamadas Java directas. La profundidad de lectura se ajustó al rol: implementación y caminos críticos; métodos/aserciones y auxiliares de pruebas; propósito, secciones operativas, fechas y relaciones de documentación. No se declara lectura byte a byte ni verificación formal. Los N/A de la matriz corresponden a dimensiones que el archivo no implementa, no a archivos omitidos.

Shipment conserva cliente, direcciones e historial locales y UUID de repartidor sin FK entre bases. La asociación de identidad local permite autorizar lecturas sin consultar proveedores; no constituye una copia del estado del repartidor. El bloqueo durante la consulta remota acotada preserva concurrencia y no debe retirarse sin mantener esa garantía. Courier expresa disponibilidad ACTIVO, no reserva exclusiva. Gateway, Eureka y Config conservan sus responsabilidades separadas. Las variantes Compose y documentos de sprints previos se interpretan en su contexto histórico.

La comprobación del inventario encontró JavaDoc en los **159 tipos de producción** y en **82 de 83 tipos de pruebas**. Se revisó su correspondencia con responsabilidades y se identificaron las excepciones documentales de la tabla. `javadoc:javadoc` de #67 verificó producción con doclint; no generó JavaDoc de tests.

Se reutilizó la [referencia ejecutada en #67 / PR #83](https://github.com/LogiTrack-Atom/shipment-service/pull/83): **839 casos Java y 70 Python aprobados**, más **65 peticiones Postman y 117 aserciones**. No se repitieron esas suites durante #68. Su aprobación no demuestra que toda prueba verifique lo que anuncia: AUD-68-08 deriva precisamente del contraste contextual. Las pruebas Java de rollback sí fuerzan fallo SQL y verifican en una transacción nueva; no se les atribuye ese defecto del ensayo Python.

Las tres copias YAML de configuración de pruebas coinciden por SHA-256 y contenido con los blobs históricos indicados en su manifiesto. Las referencias de las seis imágenes y la etiqueta de configuración del entorno Sprint03 coinciden con su manifiesto: `sprint03-1.0.1` amplía aceptación y conserva binarios `sprint03-1.0.0`. Esa diferencia de numeración está documentada. La grabación/exposición académica conserva su estado propio y no se acredita mediante esta auditoría técnica.

## Observaciones propuestas para #69

Prioridad interna de mantenimiento/revisión; no equivale a CVSS. Las mejoras opcionales requieren una decisión explícita antes de implementarse.

| ID | Prioridad | Evidencia y efecto | Acción propuesta |
| --- | --- | --- | --- |
| AUD-68-01 | P3, mejora de contrato | `ClienteRequest` y `DireccionRequest` no limitan textos al máximo SQL. El probe acepta 256 caracteres, pero `ShipmentApiIT.violacionRealDeLongitudDeColumnaResponde409YSinAltasParciales` ya pasó en #67: devuelve 409 sin altas parciales. No se confirmó un 500 ni ausencia de rollback. | Decidir si adoptar validación anticipada con 400. Si se adopta, alinear DTO, OpenAPI y pruebas; no tratarlo como defecto sin manejar. |
| AUD-68-02 | P3 | Falta JavaDoc de clase en `IntegrationFixtures`, dentro de tests. | Documentar responsabilidad y uso sin inventar autorías. |
| AUD-68-03 | P2 de mantenimiento | 243 coordenadas compile/runtime consultadas; ocho versiones coinciden con 14 avisos únicos publicados. No se demostró explotabilidad en el proyecto. | Elegir actualización compatible de plataforma/BOM, ampliar evaluación a avisos oficiales aplicables, confirmar grafo y ejecutar regresión afectada. No actualizar JAR aislados sin justificar alineación. |
| AUD-68-04 | P3 | README de Eureka conserva estado de preparación; tabla de configserver-ms omite Gateway/Eureka; JavaDoc de `RepartidorServiceImpl` describe un alcance menor que su implementación. | Corregir documentación vigente preservando las evidencias históricas. |
| AUD-68-05 | P3, opcional | Los seis Maven Wrapper descargan por HTTPS sin `distributionSha256Sum`; no hay evidencia de descarga comprometida. | Valorar checksum verificado de la distribución y documentar procedencia. |
| AUD-68-06 | P3, opcional | Dockerfile de Eureka usa bases por tags mutables. La sonda fija 8065 es coherente con la configuración actual. | Valorar fijación por digest y documentar el vínculo entre puerto configurado y sonda. |
| AUD-68-07 | P2 | OpenAPI Courier: GET `/api/v1/admin/configuracion` referencia `bearerAuth`, pero declara `BearerAuth`. Controller y pruebas sí exigen ADMIN; no se demuestra bypass. | Unificar la referencia y verificar resolución de esquemas de seguridad. El validador de referencias no certifica todo el estándar OpenAPI. |
| AUD-68-08 | P2 | `test_error_a_mitad_del_lote_revierte_filas_y_registro` instala un trigger; `schema_guard(target)` lo rechaza antes de `apply`. El test verde no demuestra rollback a mitad del lote. Lectura del flujo y ejecución del guard con metadatos sintéticos confirmaron el rechazo; no se ejecutó un nuevo fallo MySQL. | Mantener el guard productivo. Inyectar un fallo exclusivamente de ensayo después de validar el esquema, demostrar que se alcanzó el primer INSERT y que un fallo SQL posterior revierte filas y registro en MySQL real. |

## Dependencias y avisos

En clones separados de las seis aplicaciones se ejecutó `bash mvnw -B -ntp -o dependency:list -DincludeScope=runtime -DoutputFile=<archivo>` y se conservaron árboles de incorporación. Todas las ejecuciones terminaron con cero. Las 243 coordenadas únicas se consultaron mediante [OSV querybatch](https://github.com/google/osv.dev/blob/master/docs/api/post-v1-querybatch.md), sin enviar fuentes ni secretos. Los avisos obtenidos no estaban retirados; no quedó paginación pendiente. El JSON incluye rangos y referencias de las respuestas.

Las coincidencias corresponden a HttpClient 4.5.3, Jackson core/databind 2.21.5 y 3.1.5, Tomcat embed core 11.0.24, FreeMarker 2.3.34 y Guava 33.0.0-jre. Se contrastaron condiciones concretas con configuración y fuentes propias: APIs Bearer, sin configuración propia FORM/DIGEST, sin ObjectInputStream propio ni habilitación propia de tipado polimórfico observada. Esto no descarta recorridos de terceros. [Tomcat](https://tomcat.apache.org/security-11.html), [Jackson core](https://github.com/FasterXML/jackson-core/security/advisories/GHSA-7hhh-6rmp-j9qf), [Jackson databind](https://github.com/FasterXML/jackson-databind/security/advisories/GHSA-gx83-3vf8-gh7j), [FreeMarker](https://github.com/apache/freemarker/releases/tag/v2.3.35) y [Guava](https://github.com/google/guava/security/advisories/GHSA-xxph-c9ww-hj94) aportan las referencias primarias contrastadas; las restantes están preservadas en las fichas del JSON.

La evaluación queda acotada al grafo compile/runtime y a los avisos obtenidos: excluye dependencias de tests, plugins, SO/JDK y bases Docker; no cubre exhaustivamente el catálogo de cada fabricante ni la alcanzabilidad transitiva, y no incluye una prueba de explotación. Los umbrales de corrección preservados no son recomendaciones de versión vigente. Antes de elegir una actualización en #69 debe confirmarse compatibilidad y cobertura de avisos posteriores.

## Secretos y límites

Se ejecutó Gitleaks 8.30.1 sobre `--all --full-history` de los siete repositorios locales no superficiales, con reglas predeterminadas, exclusiones externas vacías, comentarios allow ignorados y redacción completa. La herramienta se obtuvo de la [publicación oficial](https://github.com/gitleaks/gitleaks/releases/tag/v8.30.1) y se verificó el checksum publicado; no se afirma verificación criptográfica de firma del editor. La suma por repositorio es 186 commits, sin errores de ejecución.

Hubo tres coincidencias en Shipment, todas falsos positivos verificados como SHA Git públicos que resuelven a commits de Courier/Gateway. No se confirmaron credenciales reales con este ensayo. Los informes persistentes contienen ubicación y clasificación sanitizada, sin Match, Secret ni fragmentos de claves. El código de salida 1 de Shipment indica esas coincidencias; los otros seis terminaron con cero.

Cobertura limitada a refs locales alcanzables descargadas y reglas de esa versión; permanecen filtros internos predeterminados. No acredita objetos inalcanzables, refs no descargadas, secretos externos, análisis completo de binarios/comprimidos ni formatos simples/desconocidos/ofuscados. El ensayo previo de 830 blobs y tres familias de firmas es distinto y no se suma como cobertura nueva. No hay fundamento para rotar credenciales únicamente por esos tres hashes.

## Revisión e integración

La validación final comprueba 451 filas únicas, rutas existentes, dimensiones cumplimentadas y los siete HEAD originales con sus fuentes limpias. Los entregables se versionan exclusivamente en `feature/68-auditoria-contextual`, con PR hacia `develop` enlazada a #68. El estado correspondiente es **En revisión**; las correcciones se deciden en #69 y G0 se revalida en #70. La aprobación y la integración siguen siendo pasos posteriores.
