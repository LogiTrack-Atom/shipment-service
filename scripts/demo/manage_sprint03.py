"""Prepara o restablece exclusivamente una demo Sprint03 administrada por este CLI."""
import argparse
import json
import sys
from pathlib import Path
from manage import locked_directory, prepare, reset, seed
from runtime import DemoError, ROOT, run
from runtime_sprint03 import Sprint03Runtime
from seed import Seed


def execute(operation: str, directory: Path, *, release=None, profile=None) -> dict:
    """Conserva identidad, snapshots y comprobación de propiedad antes de operar Docker."""
    with locked_directory(directory) as directory:
        runtime = Sprint03Runtime(directory, release)
        run(['docker', 'info'])
        if operation == 'prepare':
            runtime.initialize()
            if profile is not None:
                runtime.guard_resources()
                runtime.state['profile'] = profile
                runtime.save()
            return prepare(runtime)
        if runtime.state is None:
            raise DemoError('Primero ejecutar prepare de Sprint03.')
        runtime.verify_snapshot()
        runtime.guard_resources()
        if operation == 'seed':
            return seed(runtime)
        if operation == 'verify':
            loader = Seed(runtime)
            result = loader.verify()
            loader.export()
            return result
        if operation in {'reset', 'destroy'}:
            return reset(runtime, restart=operation == 'reset')
        runtime.down()
        return {'project': runtime.project, 'estado': 'detenido; datos conservados'}


def main() -> int:
    """No imprime archivos privados, JWT ni credenciales del entorno."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('operation', choices=['prepare', 'seed', 'verify', 'reset', 'down', 'destroy'])
    parser.add_argument('--state-dir', type=Path, default=ROOT / '.logitrack-demo/sprint03')
    parser.add_argument('--release-manifest', type=Path)
    parser.add_argument('--profile', choices=['dev', 'prod'])
    args = parser.parse_args()
    try:
        release = json.loads(args.release_manifest.read_text()) if args.release_manifest else None
        print(json.dumps(execute(args.operation, args.state_dir, release=release, profile=args.profile), ensure_ascii=False))
        return 0
    except (DemoError, OSError, ValueError, KeyError, TypeError) as error:
        print(str(error) if isinstance(error, DemoError) else 'No se pudo completar la demo Sprint03.', file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
