# Sprint04 · Inventario inicial de la auditoría

**Issue:** [shipment-service#66](https://github.com/LogiTrack-Atom/shipment-service/issues/66) · **Responsable:** MaxDZ-10 · **Fecha:** 2026-11-09 (UTC).

Este entregable congela el alcance de la auditoría. El inventario y la presencia de comentarios no acreditan una auditoría funcional, de seguridad o de calidad documental. La revisión contextual corresponde a #68; las pruebas nuevas, a #67; la aprobación de la base, a #70. Los siete checkouts originales permanecen en sus referencias y sin cambios versionados.

## Referencias y volumen

| Repositorio | HEAD de develop inventariado | Archivos | Java producción | Archivos bajo src/test |
|---|---|---:|---:|---:|
| shipment-service | `3b3bb57b9397d8d9c7e49d97351e0f0104cfd77a` | 212 | 67 | 34 |
| courier-service | `003003d619d0479bc0577d839a580e61e51a8f0b` | 82 | 40 | 25 |
| security-service | `37bb9e3146010fc4f459fcca8a0ae970b5ce1c64` | 74 | 36 | 21 |
| api-gateway | `283cbf80423705b3768787b77c8d35f9a0fd6305` | 39 | 13 | 11 |
| eureka-server | `49f5271e2075fc1c9b5b6192ed1f7cb0da5bf24b` | 14 | 1 | 2 |
| configserver | `4d7b1fb484e3546a853bbd1dbd2ed04e24e78bd5` | 14 | 2 | 2 |
| configserver-ms | `a08f95ee8fa0bf412fb30f68776e9a8794f94110` | 16 | 0 | 0 |
| **Total** | | **451** | **159** | **95** |

Hay **238 fuentes Java**, que contienen **242 declaraciones de tipos con nombre** (incluidos tipos anidados): 159 de producción y 83 de pruebas. Los 95 archivos bajo `src/test` incluyen 79 fuentes Java y 16 recursos, y no representan un conteo de casos de prueba. El total de blobs versionados ocupa 1.798.932 bytes.

El [manifiesto](manifiesto.json) conserva las ramas y etiquetas locales y remotas, el estado y los conteos por extensión y categoría. Los siete HEAD coinciden con `develop` remoto consultado para esta captura. `api-gateway` y `eureka-server` no publican una referencia `main` en la consulta; no se crean ni promueven ramas durante el diagnóstico. Las referencias históricas permanecen separadas de esta captura actual: una etiqueta Sprint03 no acredita que los contenedores en ejecución usen ese mismo código.

Las seis aplicaciones declaran **Java 25**, **Spring Boot parent 4.1.1** y **Spring Cloud BOM 2025.1.4**. Son versiones declaradas en POM, no una verificación del árbol efectivo, de compatibilidad ni de avisos de seguridad. `configserver-ms` es un repositorio de configuración con 15 YAML y README; no es una séptima aplicación Maven. Esas comprobaciones se realizan en #67 y #68.

## Archivos y revisión pendiente

- [inventario.csv](inventario.csv): una fila por blob del snapshot, con repositorio, SHA, ruta, modo, identificador de blob, bytes, extensión y categoría.
- [matriz-revision.csv](matriz-revision.csv): las mismas 451 rutas, todas **pendientes** de auditoría contextual; columnas para uso, documentación, configuración, arquitectura, seguridad, datos/pruebas, evidencias y hallazgos.
- [javadoc-tipos.csv](javadoc-tipos.csv): declaración, línea, ámbito y presencia de JavaDoc según el AST del JDK. La calidad permanece pendiente.
- [CONTRATOS.md](CONTRATOS.md): extracto de 24 operaciones REST y modelos para comenzar mocks. No habilita integración real antes de G0.

No se excluye ningún archivo versionado de la matriz inicial. Wrapper de Maven, CI, scripts, SQL, documentación y recursos también requieren revisión, aunque su criterio pueda terminar como «no aplica» justificado en alguna dimensión. La categoría es una ayuda de navegación, no un juicio sobre el contenido.

Quedan fuera del inventario los archivos ignorados/no versionados, `.git`, cachés, `target`, material de demo privado, claves, variables de entorno de ejecución, bases, contenedores, imágenes y volúmenes. No se enumeran rutas privadas ni se copian contenidos. El checkout histórico adicional `courier-integration` y el nuevo frontend quedan fuera de estos siete snapshots; sus alcances son distintos. #67 debe reconstruir la base en un entorno aislado y #68 revisar secretos en los archivos actuales y en el historial alcanzable, sin revelar valores. Esta captura no afirma ausencia de secretos.

## JavaDoc y criterio de implementación

Las **159 declaraciones de producción tienen JavaDoc**. De las 83 de pruebas, 82 lo tienen; falta en `shipment-service/src/test/java/com/logitrack_atom/shipment_service/integration/IntegrationFixtures.java`, declaración `IntegrationFixtures`, línea 12 del SHA inventariado. Se registra como observación documental para #68/#69, sin modificar el snapshot de referencia.

La presencia de un comentario no prueba que sea correcto o suficiente. En #68 se comprobarán responsabilidad real, restricciones, relaciones, parámetros de records, unidades, nulabilidad y efectos. Para cada clase Java nueva o modificada se exigirá JavaDoc coherente con su implementación; los contratos públicos relevantes deberán explicar parámetros, retorno y excepciones cuando aporten información. También se verificará la documentación técnica de las utilidades Java y clases de prueba. No se inventan autores ni se actualizan versiones para aparentar cambios.

## Reestimación y orden de trabajo

El inventario confirma 451 archivos, no siete README. Se conserva #66 en 2 puntos por su alcance acotado y se propone revisar **#68 de 8 a 13 puntos**: hay 159 fuentes de producción, 79 de pruebas, configuración distribuida, migraciones, CI y documentación histórica que contrastar. Es una propuesta basada en volumen, pendiente de calibración del equipo; no son horas ni puntos ya consumidos. El resto de la estimación de auditoría sigue provisional hasta ejecutar #67. No cambia la distribución de responsables.

La secuencia es #67 (pruebas de referencia) y #68 (revisión de todas las filas), #69 (hallazgos confirmados y correcciones), #70 (revalidación de G0). Las correcciones documentales y funcionales deben conservar evidencia previa, revisar usos indirectos de Spring/reflexión y añadir solo pruebas que validen comportamiento relevante. No se eliminan clases por no encontrar llamadas directas. Gab puede usar el extracto para preparar la base y mocks; la integración espera G0 y el contrato aprobado en #71.

## Reproducción y validación

Requisitos: Git, Python 3 estándar y JDK 25 con el módulo `jdk.compiler`. No requiere Maven, librerías externas, bases ni contenedores. Estos comandos no realizan `checkout`, `reset`, `fetch`, `pull` ni escrituras en los repositorios auditados. Ejecutar en un directorio de salida nuevo, fuera de los siete checkouts.

1. Capturar `git ls-remote --heads --tags origin` para cada repositorio y convertir su salida a `{repositorio: {ref: sha}}`. El JSON no debe contener credenciales ni contenidos de archivos.
2. Ejecutar `python3 inventariar.py --repos /ruta/a/repos --remote-refs /ruta/refs.json --output /ruta/salida-nueva`.
3. Ejecutar `python3 verificar.py --repos /ruta/a/repos --datos /ruta/salida-nueva`.

`DocInventory.java` usa exclusivamente parsing, sin resolución de dependencias, ejecución de fuentes auditadas o procesadores de anotaciones. Detecta comentarios asociados al tipo, no solo la presencia de `/**` en el archivo. Los tipos anónimos no tienen una declaración con nombre documentable y no se contabilizan.

Verificación realizada para esta captura: reconciliación de las 451 rutas con `git ls-tree`, tamaños/modos/blobs contra los SHA congelados, igualdad entre inventario y matriz, cobertura de todas las fuentes Java por el parser, huellas SHA-256 y ausencia de cambios versionados antes/después. No se ejecutaron aquí las pruebas Maven, Postman ni la demo: sus resultados históricos no se presentan como resultados de Sprint04.

Para publicar, copiar este paquete a `docs/sprint04/auditoria-inicial/` de la rama `feature/66-inventario-auditoria`, creada desde Development de #66 con base `develop`, y abrir PR hacia `develop`. Los archivos añadidos por esta PR no forman parte del snapshot anterior de 451 rutas. El cierre requiere revisión e integración del entregable.
