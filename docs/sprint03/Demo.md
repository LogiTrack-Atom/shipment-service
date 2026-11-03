# Datos reproducibles para la demostración

La issue [#43](https://github.com/LogiTrack-Atom/shipment-service/issues/43) prepara un
entorno descartable propio mediante las APIs de los servicios. No inserta SQL en
bases ajenas ni incorpora borrado automático en los perfiles de las aplicaciones.

## Alcance actual

El catálogo versionado está en `scripts/demo/catalog.json`. La carga usa las cuatro
imágenes publicadas de Sprint02, fijadas por digest, y su Compose/esquemas del commit
`938496bae144d559187fa59368e155eea2525af9`. Config Server consulta el commit
`b40bd4d0eecd5eff6e5ef0a7b23d0a106a39ba13` de configuraciones.
Esto permite preparar y comprobar los datos antes de integrar descubrimiento y rutas.
La issue #44 incorporará la infraestructura final de seis aplicaciones y tres MySQL;
esta herramienta por sí sola no acredita Gateway, Eureka ni Feign integrados.

## Preparación

Requisitos: Linux, Python 3.10 o superior, OpenSSL, Git, Docker Engine y Compose con
soporte para `up --wait`; acceso a las imágenes públicas y al repositorio de configuración.
Ejecutar desde el repositorio shipment-service:

```bash
git fetch --no-tags --depth=1 origin 938496bae144d559187fa59368e155eea2525af9
python3 scripts/demo/manage.py prepare
python3 scripts/demo/manage.py verify
```

Se generan puertos locales disponibles, contraseñas y un par RSA exclusivo. El nombre
del proyecto Docker es `lt-demo-<identificador aleatorio>`. Solo se publican puertos en
127.0.0.1. Las credenciales, claves y salidas permanecen en `.logitrack-demo/`, excluido
de Git y del contexto Docker. El directorio tiene permisos 0700; los JSON y dotenv,
0600. Las claves tienen lectura de grupo para los procesos sin root de las imágenes.
No compartir ese directorio, sus salidas ni los valores mostrados al inspeccionar Compose.

El alta inicial habilita el bootstrap ADMIN en security-service; al completar la carga
lo deshabilita y recrea ese servicio, conservando su base. Los JWT se usan en memoria.

## Catálogo inicial

| Recurso | Alias | Estado o uso |
| --- | --- | --- |
| Cuenta ADMIN | admin | Administración de la demo |
| Cuenta CLIENTE | cliente | Propietaria de dos envíos |
| Cuenta CLIENTE | otro_cliente | Permite demostrar pertenencia |
| Cuenta REPARTIDOR | repartidor_activo | Asociada al repartidor activo |
| Cuenta REPARTIDOR | repartidor_inactivo | Asociada al repartidor inactivo |
| Repartidor | activo | ACTIVO, Moto, disponible |
| Repartidor | inactivo | INACTIVO, Bicicleta, no disponible |
| Envío | asignable | PENDIENTE, sin repartidor |
| Envío | asignado | PENDIENTE, asignado al activo |
| Envío | ajeno | PENDIENTE, pertenece a otro_cliente |

Los identificadores UUID son generados por los servicios. `manifest.json` registra las
identidades efectivas y `postman_environment.json` exporta URLs, cuentas e identificadores;
incluye contraseñas de prueba y no contiene JWT. Importar ese entorno únicamente para
la demo local. Las colecciones finales se adaptan en #45/#46; las colecciones históricas
que crean recursos no deben ejecutarse suponiendo que este catálogo está vacío.

## Operación y restablecimiento

```bash
python3 scripts/demo/manage.py seed     # recupera una carga interrumpida o verifica la lista
python3 scripts/demo/manage.py down     # detiene contenedores; conserva volúmenes
python3 scripts/demo/manage.py prepare  # vuelve a iniciar y verifica los mismos datos
python3 scripts/demo/manage.py reset    # elimina solo los volúmenes propios y vuelve a cargar
python3 scripts/demo/manage.py destroy  # retira recursos propios sin volver a iniciar
```

Una demo lista se verifica sin reescribir el negocio. Si se cambió un estado o una
asignación, `seed` y `prepare` informan la diferencia: repetir el escenario requiere
`reset` explícito. `reset`/`destroy` rotan contraseñas y claves, invalidan las salidas
anteriores y retiran únicamente volúmenes del proyecto cuya propiedad se haya comprobado.
Después de `reset`, volver a importar el entorno: los UUID, contraseñas y tokens anteriores
ya no corresponden al catálogo nuevo. `destroy` conserva el directorio privado de control;
un `prepare` posterior crea una nueva carga con las credenciales rotadas.

Si se interrumpe un reset, repetir `reset`; no editar manualmente el archivo de estado.
No mover el directorio ni modificar su copia de Compose/esquemas: esas diferencias se
rechazan. `--state-dir /ruta/privada` permite otra demo independiente, con un directorio
vacío o previamente administrado y excluido de Git cuando esté dentro del repositorio.
No se aceptan URLs, proyectos Docker ni archivos Compose externos como destino.

## Verificación

```bash
python3 -m unittest discover -s scripts/demo/tests -p 'test_*.py' -v
python3 scripts/demo/verify_demo.py
```

El workflow **Demo reproducible y restablecimiento aislado** ejecuta ambos comandos.
La aceptación usa las APIs y contenedores reales: carga repetida sin duplicados,
recuperación tras interrupción, persistencia al detener/recrear con perfiles dev y prod
sobre las mismas bases descartables, cambios de negocio que
exigen reset, recreación del catálogo y rotación de claves/contraseñas. Mantiene un volumen
de control ajeno durante el reset y rechaza un volumen con nombre esperado y etiquetas
de otro proyecto. Limpia solo sus recursos de prueba y escribe evidencia sin secretos
en `target/demo-verificacion.json` y el log del job.
