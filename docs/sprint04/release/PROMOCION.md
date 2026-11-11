# Promoción P0 a main · #75

El [inventario](promotion-candidates.json) fija ocho candidatos por SHA, sus ramas de revisión y las PRs hacia main. Los seis servicios con trazabilidad de compilación en el manifiesto usan sus `release_commit`. ConfigServer reutiliza la imagen Sprint02 fijada por digest y su candidato Git corresponde a la línea base del contrato Sprint04. ConfigServer MS usa el commit de configuración del [manifiesto P0](published.json).

Cada rama `release/75-sprint04-p0` apunta al SHA del candidato. Las PRs están en borrador. API Gateway y Eureka tienen la rama candidata preparada para crear main una vez habilitada la promoción. ConfigServer MS tiene una historia divergente y requiere preservar ambos historiales mediante merge, revisando que el árbol resultante conserve la configuración aprobada.

## Ejecución

1. Registrar la constancia de [#74](https://github.com/LogiTrack-Atom/shipment-service/issues/74). Comprobar nuevamente el SHA de cada candidato y el estado de main frente al inventario.
2. Revisar diff y posibles conflictos por repositorio. Ejecutar los checks aplicables al resultado de integración. Para los repositorios sin main, validar la rama candidata y crear main apuntando al SHA fijado.
3. Integrar los candidatos mediante merge que conserve la ascendencia. Confirmar en cada repositorio que el SHA P0 sea ancestro de main y comprobar los cambios que introduzca un merge divergente. Mantener las ramas de trabajo y el historial.
4. Levantar la plantilla P0 con sus digests y revalidar arranque, sesión, autorización y asignación. Registrar resultados por SHA y usar el guion de la [guía de defensa](../GUIA_DEFENSA.md) para la constancia académica.
5. Publicar los tags Git y referencias por repositorio tras comprobar que cada tag libre apunta al SHA de entrega previsto. Registrar por separado los digests existentes en Docker Hub y el SHA de main verificado. Si existe un tag con otro destino, detener ese paso y resolver la discrepancia sin moverlo.

La promoción usa el núcleo aceptado. Los cambios posteriores de RabbitMQ en develop pertenecen al release MQ y su propio manifiesto.
