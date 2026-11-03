"""Carga un catálogo conocido mediante las APIs, sin acceder al SQL de otros servicios."""
from __future__ import annotations

import json
import urllib.error
import urllib.request
import uuid
from pathlib import Path

from runtime import DemoError, Runtime, write_json

CATALOG = Path(__file__).with_name('catalog.json')


class NoRedirect(urllib.request.HTTPRedirectHandler):
    """Impide reenviar credenciales a una redirección del servidor."""

    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


class Api:
    """Consume únicamente los puertos de loopback elegidos para el stack propio."""

    def __init__(self, runtime: Runtime):
        self.runtime = runtime
        self.opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())

    def url(self, service: str) -> str:
        return f'http://127.0.0.1:{self.runtime.state["ports"][service]}'

    def request(self, service: str, method: str, path: str, *, token=None, body=None, expected=(200,)):
        """Valida HTTP sin incluir datos de autenticación en errores o registros."""
        headers = {'Accept': 'application/json'}
        data = None
        if body is not None:
            data = json.dumps(body).encode()
            headers['Content-Type'] = 'application/json'
        if token:
            headers['Authorization'] = 'Bearer ' + token
        request = urllib.request.Request(self.url(service) + path, data=data, headers=headers, method=method)
        try:
            with self.opener.open(request, timeout=15) as response:
                status, payload = response.status, response.read()
        except urllib.error.HTTPError as error:
            with error:
                status, payload = error.code, error.read()
        except OSError:
            raise DemoError(f'No se pudo consultar {service}; comprobar prepare y la salud de la demo.') from None
        if status not in expected:
            raise DemoError(f'{service}: {method} {path} respondió HTTP {status}. No se restablecieron datos.')
        try:
            return status, json.loads(payload) if payload else None
        except ValueError:
            raise DemoError(f'{service} devolvió una respuesta no compatible con el contrato JSON.') from None


class Seed:
    """Reconcilia una carga inicial incompleta y verifica sin reescribir una demo ya lista."""

    def __init__(self, runtime: Runtime):
        self.runtime = runtime
        self.api = Api(runtime)
        self.catalog = json.loads(CATALOG.read_text())
        self.tokens = {}
        self.accounts = {}

    def password(self, alias: str) -> str:
        return self.runtime.state['credentials']['admin_password' if alias == 'admin' else 'test_password']

    def login(self, alias: str, role: str, *, allow_missing=False) -> bool:
        """Comprueba identidad y rol; no permite reclamar cuentas mediante email ajeno."""
        email = self.runtime.email(alias)
        status, response = self.api.request('security', 'POST', '/api/v1/auth/login',
                                           body={'email': email, 'password': self.password(alias)},
                                           expected=(200, 401) if allow_missing else (200,))
        if status == 401:
            return False
        token = response['accessToken']
        _, account = self.api.request('security', 'GET', '/api/v1/auth/me', token=token)
        if account['email'] != email or account['roles'] != [role]:
            raise DemoError('Una cuenta de demo no coincide con la identidad y rol previstos.')
        uuid.UUID(account['id'])
        self.tokens[alias] = token
        self.accounts[alias] = {'id': account['id'], 'email': email, 'role': role}
        return True

    def load_accounts(self, *, create=False) -> None:
        """Recupera cuentas existentes antes de intentar nuevas altas."""
        self.login('admin', 'ADMIN')
        for alias, item in self.catalog['accounts'].items():
            if self.login(alias, item['role'], allow_missing=create):
                continue
            body = {'email': self.runtime.email(alias), 'password': self.password(alias)}
            if item['role'] == 'CLIENTE':
                self.api.request('security', 'POST', '/api/v1/auth/register', body=body, expected=(201, 409))
            else:
                self.api.request('security', 'POST', '/api/v1/usuarios', token=self.tokens['admin'],
                                 body={**body, 'rol': 'REPARTIDOR'}, expected=(201, 409))
            self.login(alias, item['role'])

    def courier_name(self, alias: str) -> str:
        return 'Demo ' + self.runtime.state['demo_id'] + ' ' + alias

    def shipment_street(self, alias: str) -> str:
        return 'Demo ' + self.runtime.state['demo_id'] + ' ' + alias

    def unique(self, matches: list, description: str):
        """No selecciona arbitrariamente entre recursos duplicados."""
        if len(matches) > 1:
            raise DemoError(f'Hay recursos duplicados para {description}; ejecutar reset de esta demo.')
        return matches[0] if matches else None

    def provision(self) -> dict:
        """Completa únicamente el aprovisionamiento inicial o su recuperación interrumpida."""
        self.load_accounts(create=True)
        admin = self.tokens['admin']
        _, existing = self.api.request('courier', 'GET', '/api/v1/repartidores', token=admin)
        couriers = {}
        for alias, item in self.catalog['couriers'].items():
            name = self.courier_name(alias)
            row = self.unique([x for x in existing if x['nombre'] == name], 'repartidor ' + alias)
            if row is None:
                _, row = self.api.request('courier', 'POST', '/api/v1/repartidores', token=admin,
                                          body={'nombre': name, 'tipoVehiculo': item['vehicle']}, expected=(201,))
            if row['tipoVehiculo'] != item['vehicle']:
                raise DemoError('Un repartidor de demo fue modificado; ejecutar reset.')
            if row['estado'] != item['state']:
                _, row = self.api.request('courier', 'PATCH', '/api/v1/repartidores/' + row['id'] + '/estado',
                                          token=admin, body={'estado': item['state']})
            user_id = self.accounts[item['account']]['id']
            _, association = self.api.request('courier', 'GET', '/api/v1/repartidores/' + row['id'] + '/usuario', token=admin)
            if association['usuarioId'] is None:
                self.api.request('courier', 'PUT', '/api/v1/repartidores/' + row['id'] + '/usuario',
                                 token=admin, body={'usuarioId': user_id})
            elif association['usuarioId'] != user_id:
                raise DemoError('La identidad del repartidor no coincide con el catálogo de esta demo.')
            self.api.request('shipment', 'PUT', '/api/v1/asociaciones/repartidores/' + row['id'],
                             token=admin, body={'usuarioId': user_id})
            couriers[alias] = {'id': row['id'], 'account': item['account'], 'state': item['state']}
        _, existing = self.api.request('shipment', 'GET', '/api/v1/envios', token=admin)
        shipments = {}
        for alias, item in self.catalog['shipments'].items():
            street = self.shipment_street(alias)
            email = self.runtime.email(item['account'])
            row = self.unique([x for x in existing if x['origen']['calle'] == street and x['cliente']['email'] == email],
                              'envío ' + alias)
            if row is None:
                _, row = self.api.request('shipment', 'POST', '/api/v1/envios', token=self.tokens[item['account']],
                                          body=self.shipment_body(alias, item['account']), expected=(201,))
            if row['estado'] != 'PENDIENTE':
                raise DemoError('Un envío ya avanzó de estado; se requiere reset explícito de la demo.')
            expected_courier = couriers[item['courier']]['id'] if item['courier'] else None
            if row['repartidorId'] != expected_courier:
                if row['repartidorId'] is not None or expected_courier is None:
                    raise DemoError('La asignación del envío fue modificada; ejecutar reset.')
                _, row = self.api.request('shipment', 'PUT', '/api/v1/envios/' + row['id'] + '/repartidor',
                                          token=admin, body={'repartidorId': expected_courier})
            shipments[alias] = {'id': row['id'], 'tracking': row['codigoSeguimiento'],
                                'account': item['account'], 'courier': item['courier']}
        return {'accounts': self.accounts, 'couriers': couriers, 'shipments': shipments}

    def shipment_body(self, alias: str, account: str) -> dict:
        """Mantiene medidas y direcciones conocidas con marcadores para recuperar altas."""
        return {'cliente': {'nombre': self.catalog['accounts'][account]['name'], 'email': self.runtime.email(account)},
                'origen': {'calle': self.shipment_street(alias), 'numero': '100', 'ciudad': 'Jujuy', 'codigoPostal': '4600'},
                'destino': {'calle': 'Destino de demo', 'numero': '200', 'ciudad': 'Salta', 'codigoPostal': '4400'},
                'pesoKg': 1.25, 'anchoCm': 25.25, 'altoCm': 15.5, 'largoCm': 30.75}

    def verify(self) -> dict:
        """Comprueba el catálogo sin alterar las asignaciones o estados existentes."""
        resources = self.runtime.state['resources']
        if not resources:
            raise DemoError('La carga inicial no está terminada; ejecutar seed.')
        self.load_accounts()
        if self.accounts != resources['accounts']:
            raise DemoError('Las cuentas de la demo no coinciden con su manifiesto.')
        admin = self.tokens['admin']
        _, couriers = self.api.request('courier', 'GET', '/api/v1/repartidores', token=admin)
        _, shipments = self.api.request('shipment', 'GET', '/api/v1/envios', token=admin)
        if {x['id'] for x in couriers} != {x['id'] for x in resources['couriers'].values()}:
            raise DemoError('La cantidad de repartidores cambió; ejecutar reset para repetir la demo.')
        if {x['id'] for x in shipments} != {x['id'] for x in resources['shipments'].values()}:
            raise DemoError('La cantidad de envíos cambió; ejecutar reset para repetir la demo.')
        by_courier = {x['id']: x for x in couriers}
        for alias, item in self.catalog['couriers'].items():
            row = resources['couriers'][alias]
            actual = by_courier[row['id']]
            if actual['nombre'] != self.courier_name(alias) or actual['estado'] != item['state']:
                raise DemoError('El estado inicial del repartidor cambió; ejecutar reset explícitamente.')
            _, availability = self.api.request('courier', 'GET', '/api/v1/repartidores/' + row['id'] + '/disponibilidad',
                                               token=self.tokens[item['account']])
            if availability['disponible'] != (item['state'] == 'ACTIVO'):
                raise DemoError('La disponibilidad no coincide con el catálogo.')
            _, association = self.api.request('courier', 'GET', '/api/v1/repartidores/' + row['id'] + '/usuario', token=admin)
            if association['usuarioId'] != self.accounts[item['account']]['id']:
                raise DemoError('La asociación de identidad no coincide con la demo.')
        by_shipment = {x['id']: x for x in shipments}
        for alias, item in self.catalog['shipments'].items():
            row = resources['shipments'][alias]
            actual = by_shipment[row['id']]
            courier_id = resources['couriers'][item['courier']]['id'] if item['courier'] else None
            if (actual['estado'] != 'PENDIENTE' or actual['repartidorId'] != courier_id
                    or actual['origen']['calle'] != self.shipment_street(alias)
                    or actual['cliente']['email'] != self.runtime.email(item['account'])):
                raise DemoError('El envío cambió respecto del inicio; ejecutar reset explícitamente.')
            _, history = self.api.request('shipment', 'GET', '/api/v1/envios/' + row['id'] + '/historial',
                                          token=self.tokens[item['account']])
            if len(history) != 1 or history[0]['estadoNuevo'] != 'PENDIENTE':
                raise DemoError('El historial ya no corresponde al estado inicial.')
        own = resources['shipments']['asignado']['id']
        other = resources['shipments']['ajeno']['id']
        self.api.request('shipment', 'GET', '/api/v1/envios/' + own, token=self.tokens['repartidor_activo'])
        self.api.request('shipment', 'GET', '/api/v1/envios/' + other, token=self.tokens['cliente'], expected=(403,))
        return {'project': self.runtime.project, 'accounts': len(resources['accounts']),
                'couriers': len(couriers), 'shipments': len(shipments), 'estado': 'inicial verificado'}

    def export(self) -> None:
        """Escribe variables privadas para las colecciones futuras, sin exportar JWT."""
        resources = self.runtime.state['resources']
        write_json(self.runtime.directory / 'manifest.json', {'project': self.runtime.project,
                   'catalog_version': self.catalog['version'], 'resources': resources})
        variables = {'baseUrl': self.api.url('shipment'), 'courierBaseUrl': self.api.url('courier'),
                     'securityBaseUrl': self.api.url('security'), 'adminEmail': self.runtime.email('admin'),
                     'adminPassword': self.password('admin'), 'testPassword': self.password('cliente')}
        for alias, account in resources['accounts'].items():
            variables[alias + 'Email'] = account['email']
            variables[alias + 'UsuarioId'] = account['id']
        for alias, courier in resources['couriers'].items():
            variables['repartidor_' + alias + 'Id'] = courier['id']
        for alias, shipment in resources['shipments'].items():
            variables['envio_' + alias + 'Id'] = shipment['id']
            variables['envio_' + alias + 'Tracking'] = shipment['tracking']
        write_json(self.runtime.directory / 'postman_environment.json', {
            'name': 'LogiTrack demo ' + self.runtime.state['demo_id'][:8], '_postman_variable_scope': 'environment',
            'values': [{'key': key, 'value': value, 'enabled': True,
                        'type': 'secret' if 'Password' in key else 'default'} for key, value in variables.items()]})
