#!/usr/bin/env python3
"""Comprueba proxy y persistencia en un proyecto Sprint04 local descartable.

Preparar: verify_sprint04.py prepare /ruta/privada/state.json
Recrear contenedores sin borrar volúmenes y ejecutar: verify_sprint04.py verify /ruta/privada/state.json
Requiere S04_GATEWAY_URL (origen Nginx de loopback), S04_ADMIN_EMAIL y S04_ADMIN_PASSWORD.
El estado privado incluye una contraseña ficticia; queda en modo 0600 y nunca debe publicarse.
"""
import json
import os
from pathlib import Path
import secrets
import sys
import uuid
from urllib.error import HTTPError
from urllib.parse import urlsplit
from urllib.request import Request, HTTPRedirectHandler, build_opener


class NoRedirect(HTTPRedirectHandler):
    """No propaga credenciales a un destino de redirección."""

    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def main():
    """Prepara recursos ficticios o verifica su conservación tras recreación."""
    phase, filename = sys.argv[1:]
    if phase not in ('prepare', 'verify'):
        raise ValueError('Fase esperada: prepare o verify.')
    base = os.environ['S04_GATEWAY_URL'].rstrip('/')
    url = urlsplit(base)
    if url.scheme != 'http' or url.hostname not in ('127.0.0.1', 'localhost') or url.path or url.query or url.fragment or url.username or url.password:
        raise ValueError('El destino debe ser un origen HTTP de loopback sin credenciales.')
    opener = build_opener(NoRedirect())
    calls = assertions = 0

    def check(value, message):
        nonlocal assertions
        assertions += 1
        if not value:
            raise AssertionError(message)

    def call(path, expected=200, token=None, method='GET', body=None):
        nonlocal calls
        calls += 1
        headers = {'Accept': 'application/json'}
        if token:
            headers['Authorization'] = 'Bearer ' + token
        if body is not None:
            headers['Content-Type'] = 'application/json'
        req = Request(base + path, method=method, headers=headers,
                      data=None if body is None else json.dumps(body).encode())
        try:
            response = opener.open(req, timeout=30)
        except HTTPError as error:
            response = error
        with response:
            check(response.code == expected, f'{method} {path}: HTTP {response.code}, esperado {expected}')
            if response.code < 400:
                check('application/json' in response.headers.get('Content-Type', ''), 'La API debe devolver JSON, no el fallback SPA')
                return json.load(response)
            check('text/html' not in response.headers.get('Content-Type', ''), 'El error API no debe devolver HTML de la SPA')
            return None

    security = '/applications/apisecurity/api/v1/'
    courier = '/applications/apicourier/api/v1/repartidores'
    shipments = '/applications/apishipment/api/v1/envios'

    def login(email, password):
        return call(security + 'auth/login', method='POST', body={'email': email, 'password': password})['accessToken']

    admin = login(os.environ['S04_ADMIN_EMAIL'], os.environ['S04_ADMIN_PASSWORD'])
    state_file = Path(filename)
    if phase == 'prepare':
        # No sobrescribir un ensayo anterior ni seguir un symlink en el archivo de estado.
        run = uuid.uuid4().hex[:12]
        state = {'email': f'persistencia-{run}@example.test', 'password': secrets.token_urlsafe(24)}
        user = call(security + 'auth/register', expected=201, method='POST', body=state)
        state['user_id'] = user['id']
        client = login(state['email'], state['password'])
        row = call(courier, expected=201, token=admin, method='POST', body={'nombre': 'Prueba inicial ' + run, 'tipoVehiculo': 'Moto'})
        state['courier_id'] = row['id']
        state['courier_name'] = 'Prueba actualizada ' + run
        path = courier + '/' + row['id']
        call(path, token=admin, method='PUT', body={'nombre': state['courier_name'], 'tipoVehiculo': 'Bicicleta'})
        check(call(path, token=admin)['nombre'] == state['courier_name'], 'Edición Courier confirmada por GET')
        call(path + '/estado', token=admin, method='PATCH', body={'estado': 'INACTIVO'})
        check(call(path + '/disponibilidad', token=admin)['disponible'] is False, 'Disponibilidad INACTIVO')
        address = {'calle': 'Ejemplo', 'numero': '123', 'ciudad': 'Jujuy', 'codigoPostal': '4600'}
        shipment = call(shipments, expected=201, token=client, method='POST', body={
            'cliente': {'nombre': 'Cliente ficticio persistencia', 'email': state['email']},
            'origen': address, 'destino': address, 'pesoKg': 1.25, 'anchoCm': 25, 'altoCm': 15, 'largoCm': 30})
        state['shipment_id'] = shipment['id']
        shipment_path = shipments + '/' + shipment['id']
        call(shipment_path + '/repartidor', expected=422, token=admin, method='PUT', body={'repartidorId': row['id']})
        check(call(shipment_path, token=admin)['repartidorId'] is None, 'Asignación rechazada no muta el envío')
        check(len(call(shipment_path + '/historial', token=admin)) == 1, 'Asignación rechazada no agrega historial')
        call(path + '/estado', token=admin, method='PATCH', body={'estado': 'ACTIVO'})
        call(shipment_path + '/repartidor', token=admin, method='PUT', body={'repartidorId': row['id']})
        # Otro repartidor no vinculado no obtiene la ficha ajena.
        other = call(security + 'usuarios', expected=201, token=admin, method='POST', body={
            'email': f'ajeno-{run}@example.test', 'password': state['password'], 'rol': 'REPARTIDOR'})
        rider = login(other['email'], state['password'])
        call(path, expected=403, token=rider)
        with open(state_file, 'x', opener=lambda p, flags: os.open(p, flags, 0o600)) as handle:
            json.dump(state, handle)
    else:
        state = json.loads(state_file.read_text())
        client = login(state['email'], state['password'])
        check(call(security + 'auth/me', token=client)['id'] == state['user_id'], 'Usuario conservado')
        shipment = call(shipments + '/' + state['shipment_id'], token=client)
        check(shipment['estado'] == 'PENDIENTE' and shipment['repartidorId'] == state['courier_id'], 'Envío y asignación conservados')
        check(len(call(shipments + '/' + state['shipment_id'] + '/historial', token=client)) == 1, 'Historial conservado')
        row = call(courier + '/' + state['courier_id'], token=admin)
        check(row['nombre'] == state['courier_name'] and row['estado'] == 'ACTIVO', 'Edición Courier conservada')
        call(shipments, expected=401)
    print(json.dumps({'ok': True, 'phase': phase, 'requests_http': calls, 'aserciones': assertions}, indent=2))


if __name__ == '__main__':
    try:
        main()
    except (AssertionError, ValueError) as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
    except Exception:
        print('Verificación incompleta: revisar el laboratorio local y el archivo privado de estado.', file=sys.stderr)
        sys.exit(1)
