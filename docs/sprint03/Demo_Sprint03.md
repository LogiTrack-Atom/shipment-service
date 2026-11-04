# Demo reproducible de Sprint03

La modalidad final usa Gateway, Eureka y Feign. La demo histórica documentada en
[Demo.md](Demo.md) conserva su snapshot de Sprint02 para regresión.

## Preparación

Requiere Docker Engine/Compose, Python 3 y OpenSSL. Las seis imágenes deben estar
disponibles con las referencias del manifiesto. Desde shipment-service:

```bash
python3 scripts/demo/manage_sprint03.py --state-dir .logitrack-demo/sprint03-final prepare \
  --release-manifest docs/sprint03/entrega/versiones.json
python3 scripts/demo/manage_sprint03.py --state-dir .logitrack-demo/sprint03-final verify
```

El administrador crea un proyecto `lt-demo-s03-<identidad>` con nueve contenedores,
tres volúmenes propios, puertos de loopback distintos, claves RSA y contraseñas nuevas.
Espera salud y los cuatro registros de Eureka antes de cargar el catálogo mediante
las APIs públicas. Los datos iniciales son cinco cuentas, dos repartidores y tres
envíos. Repetir prepare o seed sobre la demo inicial no duplica esos recursos.

La configuración se fija por SHA. El manifiesto también fija el commit de Compose y
los esquemas. Se comprueba el contenido del snapshot antes de operar; un cambio en
los archivos o recursos ajenos bajo los nombres esperados impide continuar.

El estado se guarda en `.logitrack-demo/sprint03-final`, excluido de Git. `runtime.json`,
`runtime.env`, claves y `postman_environment.json` son privados. No publicarlos, subirlos
a issues ni mostrarlos completos durante la grabación. `manifest.json` contiene los
UUID y nombres sintéticos necesarios para identificar los recursos preparados.

El entorno Postman exportado configura las tres bases URL sobre el mismo Gateway:

| Variable | Destino |
| --- | --- |
| baseUrl | `/applications/apishipment` |
| courierBaseUrl | `/applications/apicourier` |
| securityBaseUrl | `/applications/apisecurity` |

Los puertos directos de aplicaciones son diagnósticos locales; las demostraciones de
negocio y el aprovisionamiento usan exclusivamente Gateway. MySQL no publica puertos.

## Postman

Importar las colecciones de Sprint03 y la copia privada de
`.logitrack-demo/sprint03-final/postman_environment.json`. Ejecutar primero los rechazos,
que conservan el catálogo, y después el flujo, que crea recursos nuevos:

```bash
npx --yes --package=newman@6.2.3 newman run \
  postman/LogiTrack-Sprint03-Rechazos.postman_collection.json \
  -e .logitrack-demo/sprint03-final/postman_environment.json --bail
npx --yes --package=newman@6.2.3 newman run \
  postman/LogiTrack-Sprint03-Flujo.postman_collection.json \
  -e .logitrack-demo/sprint03-final/postman_environment.json --bail
```

El flujo realiza login, altas, asociación de identidades, creación de envío, asignación,
lectura persistida, EN_RUTA, ENTREGADO e historial. Los tokens solo viven en las variables
locales de la ejecución. Los reportes completos de Newman contienen datos de autenticación:
guardarlos exclusivamente en un directorio privado o ignorado, nunca en el repositorio.

## Persistencia y restablecimiento

```bash
python3 scripts/demo/manage_sprint03.py --state-dir .logitrack-demo/sprint03-final down
python3 scripts/demo/manage_sprint03.py --state-dir .logitrack-demo/sprint03-final prepare
python3 scripts/demo/manage_sprint03.py --state-dir .logitrack-demo/sprint03-final prepare --profile prod
```

Down conserva las tres bases. Cambiar el perfil conserva identidad, credenciales y datos.
Si el flujo Postman ya modificó el catálogo, prepare/verify informa que hace falta reset;
no intenta revertir estados ni reparar asignaciones silenciosamente.

Para volver al inicio después de una demostración:

```bash
python3 scripts/demo/manage_sprint03.py --state-dir .logitrack-demo/sprint03-final reset
```

Reset verifica la propiedad del proyecto y sus volúmenes, retira únicamente esa demo,
rota claves y credenciales, y vuelve a cargar el catálogo. Hay que importar de nuevo su
entorno Postman porque los UUID y credenciales anteriores dejan de ser válidos.

`destroy` aplica la misma comprobación y retira los recursos sin volver a arrancarlos.
El administrador de Sprint03 rechaza el estado de una demo histórica de Sprint02.

## Aceptación automatizada

Sobre una demo inicial sin modificaciones:

```bash
python3 scripts/verificacion/sprint03_acceptance.py --state-dir .logitrack-demo/sprint03-final --reset-after
```

La prueba cambia dev/prod, verifica diagnóstico y persistencia, detiene Courier y
comprueba 503 sin mutación. Anuncia otra instancia Courier con DNS y puerto distintos,
prueba recuperación por Feign sin reiniciar Shipment y retira el proveedor temporal.
También ejecuta entrega, recreación del conjunto y restablecimiento explícito de la demo.
La opción `--reset-after` solicita deliberadamente el reset de sus recursos propios.

No ejecutar Postman simultáneamente con la aceptación automatizada: ambos modifican
el catálogo de la misma demo. La ruta explícita permite identificar la demo final de
esta máquina; en un entorno nuevo puede usarse el directorio predeterminado del CLI.
