"""Extrae métricas públicas de Newman sin copiar solicitudes, cuerpos o credenciales."""
import argparse
import json
from pathlib import Path


def summarize(document: dict) -> dict:
    """Aplica una lista explícita de campos públicos al reporte privado de Newman."""
    run = document['run']
    requests = run['stats']['requests']
    assertions = run['stats']['assertions']
    return {'coleccion': document['collection']['info']['name'],
            'pedidos': requests['total'], 'aserciones': assertions['total'],
            'fallos': len(run['failures']),
            'aprobado': requests['total'] > 0 and not run['failures']
            and requests['failed'] == assertions['failed'] == 0,
            'ejecuciones': [{'nombre': row['item']['name'], 'http': (row.get('response') or {}).get('code')}
                           for row in run['executions']]}


def main() -> int:
    """Mantiene el reporte completo en su archivo privado y escribe solo el resumen."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--report', type=Path, action='append', required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    results = [summarize(json.loads(path.read_text())) for path in args.report]
    report = {'aprobado': all(row['aprobado'] for row in results), 'resultados': results}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({'aprobado': report['aprobado'], 'colecciones': len(results)}))
    return 0 if report['aprobado'] else 1


if __name__ == '__main__':
    raise SystemExit(main())
