"""CLI de preparación y restablecimiento explícito de un entorno LogiTrack aislado."""
from __future__ import annotations

import argparse
import contextlib
import fcntl
import json
import os
import stat
import subprocess
import sys
from pathlib import Path

from runtime import DemoError, ROOT, Runtime, run
from seed import Seed


@contextlib.contextmanager
def locked_directory(directory: Path):
    """Serializa operaciones y rechaza rutas que puedan publicar secretos en Git."""
    if directory.is_symlink():
        raise DemoError('El directorio de estado no puede ser un enlace simbólico.')
    directory = directory.resolve()
    if directory.is_relative_to(ROOT):
        check = subprocess.run(['git', 'check-ignore', '--quiet', '--no-index',
                                str(directory / 'runtime.json')], cwd=ROOT)
        if check.returncode != 0:
            raise DemoError('El directorio privado debe estar excluido de Git; usar .logitrack-demo.')
    if directory.exists() and not (directory / 'runtime.json').exists():
        if any(path.name != '.lock' for path in directory.iterdir()):
            raise DemoError('No se inicializa una demo sobre un directorio ajeno no vacío.')
    directory.mkdir(mode=0o700, parents=True, exist_ok=True)
    if directory.stat().st_uid != os.getuid():
        raise DemoError('El directorio privado pertenece a otro usuario.')
    os.chmod(directory, 0o700)
    descriptor = os.open(directory / '.lock', os.O_RDWR | os.O_CREAT | os.O_NOFOLLOW, 0o600)
    try:
        properties = os.fstat(descriptor)
        if properties.st_nlink != 1 or not stat.S_ISREG(properties.st_mode):
            raise DemoError('El archivo de bloqueo no es un archivo propio válido.')
        os.fchmod(descriptor, 0o600)
        fcntl.flock(descriptor, fcntl.LOCK_EX)
        yield directory
    finally:
        os.close(descriptor)


def seed(runtime: Runtime) -> dict:
    """Completa la carga inicial; una demo lista solo recibe comprobaciones de lectura."""
    if runtime.state['phase'] not in {'seeding', 'finalizing', 'ready'}:
        raise DemoError('La infraestructura no está preparada; ejecutar prepare.')
    loader = Seed(runtime)
    if runtime.state['phase'] == 'seeding':
        runtime.state['resources'] = loader.provision()
        runtime.save()
        loader.verify()
        runtime.state['phase'] = 'finalizing'
        runtime.save()
    if runtime.state['phase'] == 'finalizing':
        result = loader.verify()
        # Deshabilitar bootstrap no cambia cuentas ni sus credenciales persistidas.
        runtime.compose('up', '-d', '--wait', '--wait-timeout', '120', 'security-service', capture=False)
        runtime.wait_for_routes()
        runtime.state['phase'] = 'ready'
        runtime.save()
    else:
        result = loader.verify()
    loader.export()
    return result


def prepare(runtime: Runtime) -> dict:
    """Arranca la demo propia conservando datos y exige reset si ya fue modificada."""
    runtime.initialize()
    if runtime.state['phase'] == 'resetting':
        raise DemoError('Un reset quedó interrumpido; volver a ejecutar reset explícitamente.')
    if runtime.state['phase'] == 'initializing':
        runtime.state['phase'] = 'seeding'
        runtime.save()
    runtime.up()
    return seed(runtime)


def reset(runtime: Runtime, *, restart=True) -> dict:
    """Retira solo volúmenes comprobados de demo y revoca sus claves y contraseñas."""
    if runtime.state is None:
        raise DemoError('No existe una demo administrada para restablecer.')
    runtime.guard_resources()
    runtime.state['phase'] = 'resetting'
    runtime.save()
    runtime.down(remove_volumes=True)
    runtime.rotate()
    if restart:
        return prepare(runtime)
    return {'project': runtime.project, 'estado': 'recursos retirados; credenciales anteriores revocadas'}


def execute(operation: str, directory: Path) -> dict:
    """No admite URLs, proyectos, perfiles o archivos Compose externos como destino."""
    with locked_directory(directory) as directory:
        runtime = Runtime(directory)
        run(['docker', 'info'])
        if operation == 'prepare':
            return prepare(runtime)
        if runtime.state is None:
            raise DemoError('Primero debe ejecutarse prepare.')
        runtime.verify_snapshot()
        runtime.guard_resources()
        if operation == 'seed':
            return seed(runtime)
        if operation == 'verify':
            loader = Seed(runtime)
            result = loader.verify()
            loader.export()
            return result
        if operation == 'reset':
            return reset(runtime)
        if operation == 'destroy':
            return reset(runtime, restart=False)
        runtime.down()
        return {'project': runtime.project, 'estado': 'detenido; datos conservados'}


def main() -> int:
    """Presenta resultados sin imprimir claves, tokens ni contraseñas."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('operation', choices=['prepare', 'seed', 'verify', 'reset', 'down', 'destroy'])
    parser.add_argument('--state-dir', type=Path, default=ROOT / '.logitrack-demo',
                        help='Directorio privado persistente; por defecto .logitrack-demo, excluido de Git.')
    args = parser.parse_args()
    try:
        print(json.dumps(execute(args.operation, args.state_dir), ensure_ascii=False))
        return 0
    except (DemoError, OSError, ValueError, KeyError, TypeError):
        # Los errores operativos conocen mensajes seguros; los inesperados no muestran estado privado.
        error = sys.exc_info()[1]
        message = str(error) if isinstance(error, DemoError) else 'No se pudo completar la operación; comprobar el estado privado y las herramientas.'
        print(message, file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
