# FIX-01 · Rollback de migración y alcance de la guarda de triggers

Issue: [#86](https://github.com/LogiTrack-Atom/shipment-service/issues/86). Origen: AUD-68-08 de #68 / PR #84 y backlog de #69 / PR #85. Fecha: 2026-11-09.

## Rectificación del diagnóstico

La afirmación anterior de que el trigger del ensayo necesariamente provocaba rechazo en schema_guard antes de apply **no queda confirmada con el usuario real del ensayo**. Se había inferido del orden del código y de un probe con metadatos sintéticos que devolvían el trigger. En MySQL real, migration_writer carece del privilegio TRIGGER y no ve ese trigger en information_schema.TRIGGERS: la guarda pasa aunque el trigger exista.

Esta diferencia invalida la causalidad atribuida al test anterior. No se demostró que el rollback estuviera roto ni que el ensayo anterior no alcanzara el segundo INSERT por esa guarda. La corrección se justifica como refuerzo de las aserciones y precisión del diagnóstico, no como reparación de un rollback productivo confirmado.

[MySQL documenta que TRIGGER habilita mostrar triggers de la tabla](https://dev.mysql.com/doc/refman/8.4/en/privileges-provided.html). Una consulta de metadatos vacía con visibilidad insuficiente no demuestra ausencia de triggers. Antes de dar por comprobado ese requisito se necesita una revisión autorizada con visibilidad suficiente. Este cambio no amplía permisos del migrador ni modifica su código; el CLI sigue restringido a contenedores de ensayo etiquetados y copias de bases.

## Cambio y verificación

El ensayo de fallo intermedio recorre la misma función run utilizada por el CLI, incluyendo integridad y schema_guard, con las credenciales limitadas reales. Un trigger propio falla en el segundo INSERT y solo produce el marcador esperado si ya observa la primera fila y su ledger en la misma transacción. Se ejecuta el proceso MySQL real; la instrumentación observa su resultado sin sustituir comandos ni respuestas. Se exige ERROR 1644 (45000) y el marcador de ese punto. Consultas nuevas verifican filas/ledger vacíos y origen intacto.

Otro ensayo comprueba que el usuario sin TRIGGER no ve el objeto y que schema_guard pasa. Concede TRIGGER temporalmente solo en la tabla del contenedor efímero, comprueba visibilidad y exige que el CLI rechace entonces antes de crear target-before.json. Revoca ese permiso al finalizar. No equivale a afirmar que el CLI detecte triggers invisibles.

Comando de verificación dirigida:

```sh
python3 -m unittest discover -s scripts/migracion/tests -p test_migracion.py \
  -k test_error_a_mitad_del_lote_revierte_filas_y_registro \
  -k test_cli_rechaza_triggers_visibles_antes_de_aplicar_el_lote -v
```

Resultado: dos casos aprobados en dos MySQL 8.4.11 efímeros; contenedores previos conservados, recursos propios retirados. No se repitió la suite completa ni la prueba JPA de Courier, que no cambió. La guía vigente refleja el nuevo conteo de 19 casos SQL y 20 con el probe JPA; las evidencias históricas permanecen fechadas e intactas.

## Seguimiento

Este documento rectifica AUD-68-08 y su tarea FIX-01 respecto del motivo original. Al reconciliar el inventario y el dictamen para #70 debe prevalecer esta evidencia real sobre el probe sintético anterior. La limitación de visibilidad de metadatos debe evaluarse como precondición operativa antes de afirmar que el esquema no tiene triggers; no se soluciona otorgando DDL al migrador ni debilitando guardas.
