# Entrega técnica Sprint03

El [manifiesto](versiones.json) fija las seis imágenes por tag y digest, configuración
Git, commits de construcción y commit de Compose/esquemas. `runtime_source_equivalence`
registra la igualdad de las fuentes de ejecución entre los commits construidos y los
integrados; las diferencias posteriores son pruebas o documentación.

Las cinco aplicaciones modificadas usan `sprint03-1.0.0`. Config Server conserva la
referencia inmutable publicada en Sprint02 porque su aplicación no cambió. Las seis
referencias pueden descargarse sin iniciar sesión y sin compilar localmente.

## Reproducción

Usar [Demo_Sprint03.md](../Demo_Sprint03.md) o completar una copia privada de
`.env.sprint03.example`. Para un clon superficial, recuperar el commit de topología
indicado en `source_commit` si el CLI informa que falta el objeto Git:

```bash
git fetch --no-tags --depth=1 origin <source_commit-del-manifiesto>
```

El tag Git `sprint03-1.0.0` identifica el cierre de cada repositorio. En Shipment el
manifiesto necesariamente fija un commit anterior para el snapshot de topología;
no intenta referenciar el hash del mismo commit que lo contiene.

## Integridad y evidencias

Consultar [Aceptacion_final.md](../Aceptacion_final.md). Las claves, contraseñas,
entornos privados de Postman y sesión del registro no forman parte de la entrega.
La publicación no sobrescribe los tags históricos Sprint02 ni utiliza `latest`.

`scripts/release/publish_images.py` publica imágenes ya construidas y comprobadas,
rechaza una etiqueta existente con contenido distinto y permite retomar una publicación
propia. La autenticación se realiza previamente mediante Docker; el script no recibe
tokens como argumentos ni los incorpora al manifiesto.

## Estado académico

El [guion](../Guion_exposicion.md) está preparado. Ensayo conjunto, participación de los
tres integrantes, video y constancia de presentación siguen pendientes en la issue #49.
La entrega técnica no se presenta como evidencia de una grabación que no se realizó.
