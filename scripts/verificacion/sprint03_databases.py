"""Comprueba acceso propio y rechazo de credenciales cruzadas en la demo Sprint03."""
import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'demo'))
from runtime import DemoError, ROOT
from runtime_sprint03 import Sprint03Runtime


def verify(directory: Path) -> dict:
    """Solo ejecuta SELECT sobre las bases de un proyecto cuya propiedad se verificó."""
    runtime = Sprint03Runtime(directory)
    runtime.guard_resources()
    results = []
    for origin in ['shipment', 'courier', 'security']:
        query = ('MYSQL_PWD="$MYSQL_PASSWORD" mysql --protocol=TCP --host=127.0.0.1 '
                 '--user="$MYSQL_USER" --database="$MYSQL_DATABASE" --batch --skip-column-names '
                 '--execute="SELECT DATABASE()"')
        schema = runtime.compose('exec', '-T', origin + '-mysql', 'sh', '-c', query).strip()
        if schema != origin + '_db':
            raise DemoError('El usuario de aplicación no accede a su esquema esperado.')
        results.append({'origen': origin, 'destino': origin, 'resultado': 'esquema propio accesible'})
        for destination in ['shipment', 'courier', 'security']:
            if destination == origin:
                continue
            query = ('result=$(MYSQL_PWD="$MYSQL_PASSWORD" mysql --protocol=TCP --host=' + destination + '-mysql '
                     '--user="$MYSQL_USER" --connect-timeout=5 --batch --skip-column-names '
                     '--execute="SELECT 1" 2>&1); '
                     'case "$result" in *"ERROR 1045"*) printf AUTH_DENIED;; *) exit 1;; esac')
            result = runtime.compose('exec', '-T', origin + '-mysql', 'sh', '-c', query).strip()
            if result != 'AUTH_DENIED':
                raise DemoError('No se pudo acreditar el rechazo de credenciales entre bases.')
            results.append({'origen': origin, 'destino': destination, 'resultado': 'autenticación cruzada rechazada'})
    return {'aprobado': True, 'alcance': 'tres conexiones propias y seis rechazos MySQL 1045 entre contenedores',
            'resultados': results}


def main() -> int:
    """Publica únicamente resultados de aislamiento, sin parámetros de conexión."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--state-dir', type=Path, default=ROOT / '.logitrack-demo/sprint03')
    parser.add_argument('--output', type=Path, default=ROOT / 'target/sprint03-databases.json')
    args = parser.parse_args()
    try:
        report = verify(args.state_dir)
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
        print(json.dumps({'aprobado': True, 'comprobaciones': len(report['resultados'])}))
        return 0
    except (DemoError, OSError, ValueError, KeyError, TypeError) as error:
        print(str(error) if isinstance(error, DemoError) else 'No se pudo verificar el aislamiento MySQL.', file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
