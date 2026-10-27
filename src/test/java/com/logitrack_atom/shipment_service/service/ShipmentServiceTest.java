package com.logitrack_atom.shipment_service.service;

import com.logitrack_atom.shipment_service.client.CourierClient;
import com.logitrack_atom.shipment_service.dto.request.ClienteRequest;
import com.logitrack_atom.shipment_service.dto.request.DireccionRequest;
import com.logitrack_atom.shipment_service.dto.request.EnvioRequest;
import com.logitrack_atom.shipment_service.dto.response.ClienteResponse;
import com.logitrack_atom.shipment_service.dto.response.EnvioResponse;
import com.logitrack_atom.shipment_service.dto.response.HistorialEstadoEnvioResponse;
import com.logitrack_atom.shipment_service.entity.Cliente;
import com.logitrack_atom.shipment_service.entity.Envio;
import com.logitrack_atom.shipment_service.entity.HistorialEstadoEnvio;
import com.logitrack_atom.shipment_service.enums.EstadoEnvio;
import com.logitrack_atom.shipment_service.exception.ReglaNegocioException;
import com.logitrack_atom.shipment_service.exception.TransicionEstadoInvalidaException;
import com.logitrack_atom.shipment_service.exception.RecursoNoEncontradoException;
import com.logitrack_atom.shipment_service.mapper.ClienteMapper;
import com.logitrack_atom.shipment_service.mapper.EnvioMapper;
import com.logitrack_atom.shipment_service.mapper.HistorialEstadoEnvioMapper;
import com.logitrack_atom.shipment_service.repository.ClienteRepository;
import com.logitrack_atom.shipment_service.repository.EnvioRepository;
import com.logitrack_atom.shipment_service.repository.HistorialEstadoEnvioRepository;
import com.logitrack_atom.shipment_service.service.impl.ShipmentServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias para el servicio de gestión de envíos
 * Trazabilidad del historial inicial mediante {@link ArgumentCaptor}
 * y consultas de recursos bajo el patrón AAA.
 * Continuación de MaxDZ-10: matriz, asignaciones, rechazos y consultas completas.
 * @author JMatiasF
 * @author MaxDZ-10
 * @since 18/10/2026
 */
@ExtendWith(MockitoExtension.class)
public class ShipmentServiceTest {
    @Mock
    private EnvioRepository envioRepository;
    @Mock
    private ClienteRepository clienteRepository;
    @Mock
    private CourierClient courierClient;
    @Mock
    private HistorialEstadoEnvioRepository historialRepository;
    @Mock
    private EnvioMapper envioMapper;
    @Mock
    private ClienteMapper clienteMapper;
    @Mock
    private HistorialEstadoEnvioMapper historialMapper;

    @InjectMocks
    private ShipmentServiceImpl shipmentService;

    /**
     * Prueba unitaria para verificar el alta de un envío cuando el cliente YA EXISTE en la base de datos.
     * <p>
     * Criterio de Aceptación:
     * - No debe invocar al repositorio para guardar un nuevo cliente.
     * - Debe generar el código de seguimiento (tracking code) con el formato correspondiente.
     * - Debe iniciar en estado PENDIENTE y enlazar las referencias del cliente adecuadamente.
     * </p>
     */
    @Test
    @DisplayName("Debe registrar un envío con cliente existente sin duplicar el cliente, verificando tracking, referencias y capturando historial")
    void testRegistrarEnvioConClienteExistente() {
        // Arrange: datos distintos del cliente existente no deben sobrescribirlo.
        EnvioRequest request = solicitudDeEnvio();
        Cliente clienteExistente = new Cliente();
        clienteExistente.setNombre("Nombre conservado");
        clienteExistente.setEmail(request.cliente().email());
        clienteExistente.setTelefono("telefono conservado");
        Envio envio = new Envio();
        when(clienteRepository.findByEmail(request.cliente().email())).thenReturn(Optional.of(clienteExistente));
        prepararMapeoDeRegistro(request, clienteExistente, envio);

        // Act
        EnvioResponse resultado = shipmentService.registrar(request);

        // Assert: se inspeccionan argumentos reales, no solo el DTO del mapper.
        verificarRegistro(request, clienteExistente, envio, resultado);
        verify(clienteRepository).findByEmail(request.cliente().email());
        verify(clienteRepository, never()).save(any());
        verifyNoInteractions(clienteMapper, courierClient, historialMapper);
        assertAll(
                () -> assertEquals("Nombre conservado", clienteExistente.getNombre()),
                () -> assertEquals("telefono conservado", clienteExistente.getTelefono()));
    }

    /**
     * Prueba unitaria para verificar el alta de un envío con un CLIENTE NUEVO.
     * <p>
     * Criterio de Aceptación:
     * - Debe persistir previamente el nuevo cliente.
     * - Captura y valida internamente los campos asignados en el {@link HistorialEstadoEnvio}
     *   usando {@link ArgumentCaptor} para garantizar el estado anterior nulo, el estado nuevo
     *   PENDIENTE y el registro de la marca de tiempo.
     * </p>
     */

    @Test
    @DisplayName("Debe registrar un envío con cliente nuevo, guardándolo y capturando el historial inicial con ArgumentCaptor")
    void testRegistrarEnvioConClienteNuevoYCapturarHistorial() {
        // Arrange: el repositorio devuelve una instancia persistida distinta de la entrada.
        EnvioRequest request = solicitudDeEnvio();
        Cliente clienteMapeado = new Cliente();
        Cliente clienteGuardado = new Cliente();
        clienteGuardado.setNombre(request.cliente().nombre());
        clienteGuardado.setEmail(request.cliente().email());
        clienteGuardado.setTelefono(request.cliente().telefono());
        Envio envio = new Envio();
        when(clienteRepository.findByEmail(request.cliente().email())).thenReturn(Optional.empty());
        when(clienteMapper.toEntity(request.cliente())).thenReturn(clienteMapeado);
        when(clienteRepository.save(clienteMapeado)).thenReturn(clienteGuardado);
        prepararMapeoDeRegistro(request, clienteGuardado, envio);

        // Act
        EnvioResponse resultado = shipmentService.registrar(request);

        // Assert: el envío y su historial usan las referencias correctas.
        ArgumentCaptor<Cliente> clienteCaptor = ArgumentCaptor.forClass(Cliente.class);
        verify(clienteRepository).save(clienteCaptor.capture());
        assertSame(clienteMapeado, clienteCaptor.getValue());
        verify(clienteMapper).toEntity(request.cliente());
        verificarRegistro(request, clienteGuardado, envio, resultado);
        verifyNoInteractions(courierClient, historialMapper);
    }

    /**
     * Prueba unitaria para validar la búsqueda exitosa de un envío por su UUID.
     */
    @Test
    @DisplayName("Debe retornar un envío exitosamente al buscar por un ID existente")
    void testObtenerPorIdExitoso() {
        // Arrange
        Envio envio = envioEnEstado(EstadoEnvio.PENDIENTE);
        EnvioResponse esperado = respuesta(envio);
        when(envioRepository.findById(envio.getId())).thenReturn(Optional.of(envio));
        when(envioMapper.toResponse(envio)).thenReturn(esperado);

        // Act
        EnvioResponse resultado = shipmentService.obtenerPorId(envio.getId());

        // Assert
        assertSame(esperado, resultado);
        verify(envioRepository).findById(envio.getId());
        verify(envioMapper).toResponse(envio);
        verificarSinEscrituras();
    }

    /**
     * Prueba unitaria para validar que se lance RecursoNoEncontradoException
     * si se consulta un UUID de envío inexistente.
     */

    @Test
    @DisplayName("Debe lanzar RecursoNoEncontradoException al buscar un envío con un ID que no existe")
    void testObtenerPorIdNoEncontrado() {
        // Arrange
        UUID envioId = UUID.randomUUID();
        when(envioRepository.findById(envioId)).thenReturn(Optional.empty());

        // Act / Assert
        RecursoNoEncontradoException error = assertThrowsExactly(RecursoNoEncontradoException.class,
                () -> shipmentService.obtenerPorId(envioId));
        assertTrue(error.getMessage().contains(envioId.toString()));
        verify(envioRepository).findById(envioId);
        verificarRechazoSinEscrituras();
    }

    /**
     * Prueba unitaria para validar la búsqueda exitosa por código de seguimiento.
     */

    @Test
    @DisplayName("Debe retornar un envío exitosamente al buscar por un código de seguimiento existente")
    void testObtenerPorCodigoSeguimientoExitoso() {
        // Arrange
        Envio envio = envioEnEstado(EstadoEnvio.PENDIENTE);
        EnvioResponse esperado = respuesta(envio);
        when(envioRepository.findByCodigoSeguimiento(envio.getCodigoSeguimiento())).thenReturn(Optional.of(envio));
        when(envioMapper.toResponse(envio)).thenReturn(esperado);

        // Act
        EnvioResponse resultado = shipmentService.obtenerPorCodigoSeguimiento(envio.getCodigoSeguimiento());

        // Assert
        assertSame(esperado, resultado);
        verify(envioRepository).findByCodigoSeguimiento(envio.getCodigoSeguimiento());
        verify(envioMapper).toResponse(envio);
        verificarSinEscrituras();
    }

    /**
     * Prueba unitaria para validar la excepción lanzada al buscar un código de seguimiento inexistente.
     */

    @Test
    @DisplayName("Debe lanzar RecursoNoEncontradoException al buscar por un código de seguimiento que no existe")
    void testObtenerPorCodigoSeguimientoNoEncontrado() {
        // Arrange
        String tracking = "LT-NO-EXISTE";
        when(envioRepository.findByCodigoSeguimiento(tracking)).thenReturn(Optional.empty());

        // Act / Assert
        RecursoNoEncontradoException error = assertThrowsExactly(RecursoNoEncontradoException.class,
                () -> shipmentService.obtenerPorCodigoSeguimiento(tracking));
        assertTrue(error.getMessage().contains(tracking));
        verify(envioRepository).findByCodigoSeguimiento(tracking);
        verificarRechazoSinEscrituras();
    }

    /**
     * Prueba unitaria para verificar que se retornen todos los envíos al listar sin filtro de estado.
     */
    @Test
    @DisplayName("Debe retornar todos los envíos cuando el estado a filtrar es nulo")
    void testListarPorEstadoNulo() {
        // Arrange: se comprueba el mapeo de ambas entidades devueltas por el repositorio.
        Envio primero = envioEnEstado(EstadoEnvio.PENDIENTE);
        Envio segundo = envioEnEstado(EstadoEnvio.ENTREGADO);
        EnvioResponse respuestaPrimera = respuesta(primero);
        EnvioResponse respuestaSegunda = respuesta(segundo);
        when(envioRepository.findAll()).thenReturn(List.of(primero, segundo));
        when(envioMapper.toResponse(primero)).thenReturn(respuestaPrimera);
        when(envioMapper.toResponse(segundo)).thenReturn(respuestaSegunda);

        // Act
        List<EnvioResponse> resultado = shipmentService.listarPorEstado(null);

        // Assert: el servicio no impone un orden de base de datos adicional.
        assertIterableEquals(List.of(respuestaPrimera, respuestaSegunda), resultado);
        verify(envioRepository).findAll();
        verify(envioRepository, never()).findByEstado(any());
        verify(envioMapper).toResponse(primero);
        verify(envioMapper).toResponse(segundo);
        verificarSinEscrituras();
    }

    /**
     * Prueba unitaria para verificar el filtrado correcto por un estado específico.
     */

    @ParameterizedTest(name = "Filtro {0}")
    @EnumSource(EstadoEnvio.class)
    @DisplayName("Debe retornar los envíos filtrados cuando se especifica un estado válido")
    void testListarPorEstadoEspecifico(EstadoEnvio estadoBuscado) {
        // Arrange
        Envio envio = envioEnEstado(estadoBuscado);
        EnvioResponse esperado = respuesta(envio);
        when(envioRepository.findByEstado(estadoBuscado)).thenReturn(List.of(envio));
        when(envioMapper.toResponse(envio)).thenReturn(esperado);

        // Act
        List<EnvioResponse> resultado = shipmentService.listarPorEstado(estadoBuscado);

        // Assert
        assertIterableEquals(List.of(esperado), resultado);
        verify(envioRepository).findByEstado(estadoBuscado);
        verify(envioRepository, never()).findAll();
        verify(envioMapper).toResponse(envio);
        verificarSinEscrituras();
    }
    /**
     * Prueba parametrizada para validar la matriz completa de las 16 combinaciones
     * de estados en el método cambiarEstado.
     * Permite exclusivamente:
     * - PENDIENTE -> EN_RUTA
     * - PENDIENTE -> CANCELADO
     * - EN_RUTA -> ENTREGADO
     * Y verifica que cualquier otra transición lance una excepción sin guardar cambios.
     */
    @ParameterizedTest(name = "{0} -> {1}: permitida={2}")
    @CsvSource({
            // Combinaciones desde PENDIENTE
            "PENDIENTE, PENDIENTE, false",
            "PENDIENTE, EN_RUTA, true",
            "PENDIENTE, ENTREGADO, false",
            "PENDIENTE, CANCELADO, true",

            // Combinaciones desde EN_RUTA
            "EN_RUTA, PENDIENTE, false",
            "EN_RUTA, EN_RUTA, false",
            "EN_RUTA, ENTREGADO, true",
            "EN_RUTA, CANCELADO, false",

            // Combinaciones desde ENTREGADO (Estado final, ninguna permitida)
            "ENTREGADO, PENDIENTE, false",
            "ENTREGADO, EN_RUTA, false",
            "ENTREGADO, ENTREGADO, false",
            "ENTREGADO, CANCELADO, false",

            // Combinaciones desde CANCELADO (Estado final, ninguna permitida)
            "CANCELADO, PENDIENTE, false",
            "CANCELADO, EN_RUTA, false",
            "CANCELADO, ENTREGADO, false",
            "CANCELADO, CANCELADO, false"
    })
    @DisplayName("Debe validar las 16 combinaciones de la máquina de estados en cambiarEstado")
    void testMatrizCambiarEstado(EstadoEnvio estadoActual, EstadoEnvio estadoNuevo, boolean esPermitida) {
        // Arrange: la matriz aísla la máquina de estados de la condición extra del repartidor.
        Envio envio = envioEnEstado(estadoActual);
        UUID repartidor = repartidorEnEstado(EstadoRepartidor.ACTIVO);
        envio.setRepartidorId(repartidor);
        when(envioRepository.findById(envio.getId())).thenReturn(Optional.of(envio));

        // Act / Assert
        if (esPermitida) {
            prepararRespuestaDeEnvio(envio);
            EnvioResponse resultado = shipmentService.cambiarEstado(envio.getId(), estadoNuevo);
            assertEquals(estadoNuevo, resultado.estado());
            assertSame(repartidor, envio.getRepartidorId());
            verificarTransicionGuardada(envio, estadoActual, estadoNuevo);
        } else {
            assertThrowsExactly(TransicionEstadoInvalidaException.class,
                    () -> shipmentService.cambiarEstado(envio.getId(), estadoNuevo));
            assertEquals(estadoActual, envio.getEstado());
            assertSame(repartidor, envio.getRepartidorId());
            verify(envioRepository).findById(envio.getId());
            verificarRechazoSinEscrituras();
        }
    }


    @Test
    @DisplayName("Registro: vuelve a generar tracking si el repositorio detecta una colisión")
    void testRegistrarConColisionDeTracking() {
        // Arrange
        EnvioRequest request = solicitudDeEnvio();
        Cliente cliente = new Cliente();
        cliente.setEmail(request.cliente().email());
        Envio envio = new Envio();
        when(clienteRepository.findByEmail(request.cliente().email())).thenReturn(Optional.of(cliente));
        prepararMapeoDeRegistro(request, cliente, envio);
        when(envioRepository.existsByCodigoSeguimiento(anyString())).thenReturn(true, false);

        // Act
        EnvioResponse resultado = shipmentService.registrar(request);

        // Assert: se observa el código consultado y el que llega al mapper/guardado.
        ArgumentCaptor<String> codigos = ArgumentCaptor.forClass(String.class);
        verify(envioRepository, times(2)).existsByCodigoSeguimiento(codigos.capture());
        assertTrue(codigos.getAllValues().stream().allMatch(codigo -> codigo.matches("LT-[0-9A-F]{10}")));
        assertEquals(codigos.getAllValues().getLast(), resultado.codigoSeguimiento());
        verify(envioRepository).save(same(envio));
        verificarHistorialGuardado(envio, null, EstadoEnvio.PENDIENTE);
    }

    @ParameterizedTest(name = "EN_RUTA con repartidor {0}")
    @NullSource
    @EnumSource(EstadoRepartidor.class)
    @DisplayName("Entrar en EN_RUTA requiere un repartidor asignado y ACTIVO")
    void testCambiarAEnRutaSegunRepartidor(EstadoRepartidor estadoRepartidor) {
        // Arrange: null representa ausencia de repartidor, no un estado válido de este.
        Envio envio = envioEnEstado(EstadoEnvio.PENDIENTE);
        UUID asignado = estadoRepartidor == null ? null : repartidorEnEstado(estadoRepartidor);
        envio.setRepartidorId(asignado);
        when(envioRepository.findById(envio.getId())).thenReturn(Optional.of(envio));

        // Act / Assert
        if (estadoRepartidor == EstadoRepartidor.ACTIVO) {
            prepararRespuestaDeEnvio(envio);
            EnvioResponse resultado = shipmentService.cambiarEstado(envio.getId(), EstadoEnvio.EN_RUTA);
            assertEquals(EstadoEnvio.EN_RUTA, resultado.estado());
            verificarTransicionGuardada(envio, EstadoEnvio.PENDIENTE, EstadoEnvio.EN_RUTA);
        } else {
            assertThrowsExactly(ReglaNegocioException.class,
                    () -> shipmentService.cambiarEstado(envio.getId(), EstadoEnvio.EN_RUTA));
            assertEquals(EstadoEnvio.PENDIENTE, envio.getEstado());
            assertSame(asignado, envio.getRepartidorId());
            verify(envioRepository).findById(envio.getId());
            if (asignado != null) verify(courierClient).verificarDisponible(asignado);
            verificarRechazoSinEscrituras();
        }
    }

    @Test
    @DisplayName("Cambio de estado: envío inexistente se rechaza antes de cualquier escritura")
    void testCambiarEstadoDeEnvioInexistente() {
        // Arrange
        UUID id = UUID.randomUUID();
        when(envioRepository.findById(id)).thenReturn(Optional.empty());

        // Act / Assert
        assertThrowsExactly(RecursoNoEncontradoException.class,
                () -> shipmentService.cambiarEstado(id, EstadoEnvio.CANCELADO));
        verify(envioRepository).findById(id);
        verificarRechazoSinEscrituras();
    }

    @ParameterizedTest(name = "Reemplaza asignación previa: {0}")
    @ValueSource(booleans = {false, true})
    @DisplayName("Asignación: un ACTIVO se asigna o reemplaza en PENDIENTE sin generar historial")
    void testAsignarRepartidorActivo(boolean teniaRepartidor) {
        // Arrange
        Envio envio = envioEnEstado(EstadoEnvio.PENDIENTE);
        if (teniaRepartidor) {
            envio.setRepartidorId(repartidorEnEstado(EstadoRepartidor.ACTIVO));
        }
        UUID nuevo = repartidorEnEstado(EstadoRepartidor.ACTIVO);
        when(envioRepository.findById(envio.getId())).thenReturn(Optional.of(envio));
        prepararRespuestaDeEnvio(envio);

        // Act
        EnvioResponse resultado = shipmentService.asignarRepartidor(envio.getId(), nuevo);

        // Assert
        ArgumentCaptor<Envio> guardado = ArgumentCaptor.forClass(Envio.class);
        verify(envioRepository).save(guardado.capture());
        assertSame(envio, guardado.getValue());
        assertEquals(nuevo, guardado.getValue().getRepartidorId());
        verify(courierClient).verificarDisponible(nuevo);
        assertEquals(EstadoEnvio.PENDIENTE, envio.getEstado());
        assertEquals(nuevo, resultado.repartidorId());
        assertEquals(EstadoEnvio.PENDIENTE, resultado.estado());

        verifyNoInteractions(clienteRepository, historialRepository, clienteMapper, historialMapper);
    }

    @Test
    @DisplayName("Asignación: INACTIVO se rechaza sin reemplazar al repartidor previo")
    void testAsignarRepartidorInactivo() {
        // Arrange
        Envio envio = envioEnEstado(EstadoEnvio.PENDIENTE);
        UUID anterior = repartidorEnEstado(EstadoRepartidor.ACTIVO);
        envio.setRepartidorId(anterior);
        UUID inactivo = repartidorEnEstado(EstadoRepartidor.INACTIVO);
        when(envioRepository.findById(envio.getId())).thenReturn(Optional.of(envio));

        // Act / Assert
        assertThrowsExactly(ReglaNegocioException.class,
                () -> shipmentService.asignarRepartidor(envio.getId(), inactivo));
        assertEquals(EstadoEnvio.PENDIENTE, envio.getEstado());
        assertSame(anterior, envio.getRepartidorId());
        verify(envioRepository).findById(envio.getId());
        verify(courierClient).verificarDisponible(inactivo);
        verificarRechazoSinEscrituras();
    }

    @Test
    @DisplayName("Asignación: repartidor inexistente no altera el envío ni guarda historial")
    void testAsignarRepartidorInexistente() {
        // Arrange
        Envio envio = envioEnEstado(EstadoEnvio.PENDIENTE);
        UUID anterior = repartidorEnEstado(EstadoRepartidor.ACTIVO);
        envio.setRepartidorId(anterior);
        UUID repartidorId = UUID.randomUUID();
        when(envioRepository.findById(envio.getId())).thenReturn(Optional.of(envio));
        doThrow(new RecursoNoEncontradoException("Repartidor", repartidorId))
                .when(courierClient).verificarDisponible(repartidorId);

        // Act / Assert
        RecursoNoEncontradoException error = assertThrowsExactly(RecursoNoEncontradoException.class,
                () -> shipmentService.asignarRepartidor(envio.getId(), repartidorId));
        assertTrue(error.getMessage().contains(repartidorId.toString()));
        assertEquals(EstadoEnvio.PENDIENTE, envio.getEstado());
        assertSame(anterior, envio.getRepartidorId());
        verify(envioRepository).findById(envio.getId());
        verify(courierClient).verificarDisponible(repartidorId);
        verificarRechazoSinEscrituras();
    }

    @Test
    @DisplayName("Asignación: envío inexistente no consulta repartidores ni escribe")
    void testAsignarRepartidorAEnvioInexistente() {
        // Arrange
        UUID envioId = UUID.randomUUID();
        UUID repartidorId = UUID.randomUUID();
        when(envioRepository.findById(envioId)).thenReturn(Optional.empty());

        // Act / Assert
        assertThrowsExactly(RecursoNoEncontradoException.class,
                () -> shipmentService.asignarRepartidor(envioId, repartidorId));
        verify(envioRepository).findById(envioId);
        verificarRechazoSinEscrituras();
    }

    @ParameterizedTest(name = "Asignación rechazada en {0}")
    @EnumSource(value = EstadoEnvio.class, names = "PENDIENTE", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("Asignación: un envío no PENDIENTE conserva su estado y repartidor")
    void testAsignarRepartidorAEnvioNoPendiente(EstadoEnvio estado) {
        // Arrange
        Envio envio = envioEnEstado(estado);
        UUID anterior = repartidorEnEstado(EstadoRepartidor.ACTIVO);
        envio.setRepartidorId(anterior);
        UUID nuevoId = UUID.randomUUID();
        when(envioRepository.findById(envio.getId())).thenReturn(Optional.of(envio));

        // Act / Assert
        assertThrowsExactly(ReglaNegocioException.class,
                () -> shipmentService.asignarRepartidor(envio.getId(), nuevoId));
        assertEquals(estado, envio.getEstado());
        assertSame(anterior, envio.getRepartidorId());
        verify(envioRepository).findById(envio.getId());
        verificarRechazoSinEscrituras();
    }

    @Test
    @DisplayName("Cancelar: PENDIENTE pasa a CANCELADO con el historial correspondiente")
    void testCancelarEnvioPendiente() {
        // Arrange
        Envio envio = envioEnEstado(EstadoEnvio.PENDIENTE);
        when(envioRepository.findById(envio.getId())).thenReturn(Optional.of(envio));
        prepararRespuestaDeEnvio(envio);

        // Act
        EnvioResponse resultado = shipmentService.cancelar(envio.getId());

        // Assert: se prueba el método público real, sin espiar ni mockear el servicio.
        assertEquals(EstadoEnvio.CANCELADO, resultado.estado());
        verificarTransicionGuardada(envio, EstadoEnvio.PENDIENTE, EstadoEnvio.CANCELADO);
    }

    @ParameterizedTest(name = "Cancelar rechazado en {0}")
    @EnumSource(value = EstadoEnvio.class, names = "PENDIENTE", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("Cancelar: rechaza estados no PENDIENTE sin alterar estado ni asignación")
    void testCancelarEnvioNoPendiente(EstadoEnvio estado) {
        // Arrange
        Envio envio = envioEnEstado(estado);
        UUID asignado = repartidorEnEstado(EstadoRepartidor.ACTIVO);
        envio.setRepartidorId(asignado);
        when(envioRepository.findById(envio.getId())).thenReturn(Optional.of(envio));

        // Act / Assert
        assertThrowsExactly(TransicionEstadoInvalidaException.class,
                () -> shipmentService.cancelar(envio.getId()));
        assertEquals(estado, envio.getEstado());
        assertSame(asignado, envio.getRepartidorId());
        verify(envioRepository).findById(envio.getId());
        verificarRechazoSinEscrituras();
    }

    @Test
    @DisplayName("Cancelar: envío inexistente se rechaza sin escrituras")
    void testCancelarEnvioInexistente() {
        // Arrange
        UUID id = UUID.randomUUID();
        when(envioRepository.findById(id)).thenReturn(Optional.empty());

        // Act / Assert
        assertThrowsExactly(RecursoNoEncontradoException.class, () -> shipmentService.cancelar(id));
        verify(envioRepository).findById(id);
        verificarRechazoSinEscrituras();
    }

    @ParameterizedTest(name = "Lista vacía para filtro {0}")
    @NullSource
    @EnumSource(EstadoEnvio.class)
    @DisplayName("Listar: sin coincidencias devuelve lista vacía y no mapea ni escribe")
    void testListarEnviosSinResultados(EstadoEnvio estado) {
        // Arrange
        if (estado == null) {
            when(envioRepository.findAll()).thenReturn(List.of());
        } else {
            when(envioRepository.findByEstado(estado)).thenReturn(List.of());
        }

        // Act
        List<EnvioResponse> resultado = shipmentService.listarPorEstado(estado);

        // Assert
        assertTrue(resultado.isEmpty());
        verifyNoInteractions(envioMapper, clienteMapper, historialMapper);
        verificarSinEscrituras();
    }

    @Test
    @DisplayName("Historial: consulta por fecha ascendente y conserva el orden al mapear cada evento")
    void testObtenerHistorialDeEnvioExistente() {
        // Arrange: la ordenación en SQL corresponde al repositorio, no se simula aquí.
        Envio envio = envioEnEstado(EstadoEnvio.EN_RUTA);
        Instant inicio = Instant.parse("2026-10-20T10:00:00Z");
        HistorialEstadoEnvio primero = new HistorialEstadoEnvio(envio, null, EstadoEnvio.PENDIENTE, inicio);
        HistorialEstadoEnvio segundo = new HistorialEstadoEnvio(envio, EstadoEnvio.PENDIENTE,
                EstadoEnvio.EN_RUTA, inicio.plusSeconds(60));
        HistorialEstadoEnvioResponse respuestaPrimera = new HistorialEstadoEnvioResponse(null,
                null, EstadoEnvio.PENDIENTE, primero.getFechaCambio());
        HistorialEstadoEnvioResponse respuestaSegunda = new HistorialEstadoEnvioResponse(null,
                EstadoEnvio.PENDIENTE, EstadoEnvio.EN_RUTA, segundo.getFechaCambio());
        when(envioRepository.findById(envio.getId())).thenReturn(Optional.of(envio));
        when(historialRepository.findByEnvioIdOrderByFechaCambioAsc(envio.getId()))
                .thenReturn(List.of(primero, segundo));
        when(historialMapper.toResponse(primero)).thenReturn(respuestaPrimera);
        when(historialMapper.toResponse(segundo)).thenReturn(respuestaSegunda);

        // Act
        List<HistorialEstadoEnvioResponse> resultado = shipmentService.obtenerHistorial(envio.getId());

        // Assert
        assertIterableEquals(List.of(respuestaPrimera, respuestaSegunda), resultado);
        verify(envioRepository).findById(envio.getId());
        verify(historialRepository).findByEnvioIdOrderByFechaCambioAsc(envio.getId());
        verify(historialMapper).toResponse(primero);
        verify(historialMapper).toResponse(segundo);
        verifyNoInteractions(envioMapper, clienteMapper);
        verificarSinEscrituras();
    }

    @Test
    @DisplayName("Historial: envío existente sin eventos devuelve lista vacía sin mapear")
    void testObtenerHistorialVacio() {
        // Arrange
        Envio envio = envioEnEstado(EstadoEnvio.PENDIENTE);
        when(envioRepository.findById(envio.getId())).thenReturn(Optional.of(envio));
        when(historialRepository.findByEnvioIdOrderByFechaCambioAsc(envio.getId())).thenReturn(List.of());

        // Act
        List<HistorialEstadoEnvioResponse> resultado = shipmentService.obtenerHistorial(envio.getId());

        // Assert
        assertTrue(resultado.isEmpty());
        verifyNoInteractions(historialMapper, envioMapper, clienteMapper);
        verificarSinEscrituras();
    }

    @Test
    @DisplayName("Historial: envío inexistente se rechaza sin consultar ni guardar eventos")
    void testObtenerHistorialDeEnvioInexistente() {
        // Arrange
        UUID id = UUID.randomUUID();
        when(envioRepository.findById(id)).thenReturn(Optional.empty());

        // Act / Assert
        assertThrowsExactly(RecursoNoEncontradoException.class, () -> shipmentService.obtenerHistorial(id));
        verify(envioRepository).findById(id);
        verificarRechazoSinEscrituras();
    }

    private static EnvioRequest solicitudDeEnvio() {
        return new EnvioRequest(new ClienteRequest("Datos de solicitud", "cliente@example.test", "123456"),
                new DireccionRequest("Origen", "10", "Jujuy", "4600"),
                new DireccionRequest("Destino", "20", "Salta", "4400"),
                new BigDecimal("1.250"), new BigDecimal("10.25"),
                new BigDecimal("20.50"), new BigDecimal("30.75"));
    }

    private static Envio envioEnEstado(EstadoEnvio estado) {
        Envio envio = new Envio();
        // La identidad asignada por JPA se simula solo en la fixture, sin API pública nueva.
        ReflectionTestUtils.setField(envio, "id", UUID.randomUUID());
        envio.setCodigoSeguimiento("LT-0123456789");
        envio.setEstado(estado);
        return envio;
    }

    /** Estados del doble remoto, sin entidad persistida en shipment. */
    private enum EstadoRepartidor { ACTIVO, INACTIVO }

    private UUID repartidorEnEstado(EstadoRepartidor estado) {
        UUID id = UUID.randomUUID();
        if (estado == EstadoRepartidor.INACTIVO) {
            doThrow(new ReglaNegocioException("El repartidor no está activo"))
                    .when(courierClient).verificarDisponible(id);
        }
        return id;
    }

    private static EnvioResponse respuesta(Envio envio) {
        Cliente cliente = envio.getCliente();
        ClienteResponse clienteResponse = cliente == null ? null : new ClienteResponse(cliente.getId(),
                cliente.getNombre(), cliente.getEmail(), cliente.getTelefono());
        return new EnvioResponse(envio.getId(), envio.getCodigoSeguimiento(), clienteResponse, envio.getRepartidorId(),
                envio.getPesoKg(), envio.getAnchoCm(), envio.getAltoCm(), envio.getLargoCm(), envio.getEstado(),
                null, null, envio.getCreatedAt(), envio.getUpdatedAt());
    }

    private void prepararRespuestaDeEnvio(Envio envio) {
        when(envioMapper.toResponse(envio)).thenAnswer(invocation -> respuesta(envio));
    }

    private void prepararMapeoDeRegistro(EnvioRequest request, Cliente cliente, Envio envio) {
        when(envioMapper.toEntity(same(request), same(cliente), anyString())).thenAnswer(invocation -> {
            envio.setCliente(invocation.getArgument(1));
            envio.setCodigoSeguimiento(invocation.getArgument(2));
            return envio;
        });
        prepararRespuestaDeEnvio(envio);
    }

    private void verificarRegistro(EnvioRequest request, Cliente cliente, Envio envio, EnvioResponse resultado) {
        ArgumentCaptor<String> trackingCaptor = ArgumentCaptor.forClass(String.class);
        verify(envioMapper).toEntity(same(request), same(cliente), trackingCaptor.capture());
        String tracking = trackingCaptor.getValue();
        assertTrue(tracking.matches("LT-[0-9A-F]{10}"), "El tracking generado debe cumplir el contrato");
        verify(envioRepository).existsByCodigoSeguimiento(tracking);
        ArgumentCaptor<Envio> envioCaptor = ArgumentCaptor.forClass(Envio.class);
        verify(envioRepository).save(envioCaptor.capture());
        assertAll(
                () -> assertSame(envio, envioCaptor.getValue()),
                () -> assertSame(cliente, envioCaptor.getValue().getCliente()),
                () -> assertEquals(tracking, envioCaptor.getValue().getCodigoSeguimiento()),
                () -> assertEquals(EstadoEnvio.PENDIENTE, envioCaptor.getValue().getEstado()),
                () -> assertEquals(tracking, resultado.codigoSeguimiento()),
                () -> assertEquals(EstadoEnvio.PENDIENTE, resultado.estado()),
                () -> assertEquals(cliente.getEmail(), resultado.cliente().email()));
        verify(envioMapper).toResponse(envio);
        verificarHistorialGuardado(envio, null, EstadoEnvio.PENDIENTE);
    }

    private void verificarTransicionGuardada(Envio envio, EstadoEnvio anterior, EstadoEnvio nuevo) {
        ArgumentCaptor<Envio> envioCaptor = ArgumentCaptor.forClass(Envio.class);
        verify(envioRepository).save(envioCaptor.capture());
        assertSame(envio, envioCaptor.getValue());
        assertEquals(nuevo, envioCaptor.getValue().getEstado());
        verificarHistorialGuardado(envio, anterior, nuevo);
        verifyNoInteractions(clienteRepository);
        if (nuevo == EstadoEnvio.EN_RUTA) verify(courierClient).verificarDisponible(envio.getRepartidorId());
        else verifyNoInteractions(courierClient);
    }

    private void verificarHistorialGuardado(Envio envio, EstadoEnvio anterior, EstadoEnvio nuevo) {
        ArgumentCaptor<HistorialEstadoEnvio> historialCaptor = ArgumentCaptor.forClass(HistorialEstadoEnvio.class);
        verify(historialRepository).save(historialCaptor.capture());
        HistorialEstadoEnvio historial = historialCaptor.getValue();
        assertAll(
                () -> assertSame(envio, historial.getEnvio()),
                () -> assertEquals(anterior, historial.getEstadoAnterior()),
                () -> assertEquals(nuevo, historial.getEstadoNuevo()),
                () -> assertNotNull(historial.getFechaCambio()));
    }

    private void verificarSinEscrituras() {
        verify(envioRepository, never()).save(any());
        verify(historialRepository, never()).save(any());
        verify(clienteRepository, never()).save(any());
    }

    private void verificarRechazoSinEscrituras() {
        verificarSinEscrituras();
        // Las lecturas esperadas se verifican en cada caso: detecta escrituras o trabajo posterior extra.
        verifyNoMoreInteractions(envioRepository, historialRepository, clienteRepository, courierClient);
        verifyNoInteractions(envioMapper, clienteMapper, historialMapper);
    }

}
