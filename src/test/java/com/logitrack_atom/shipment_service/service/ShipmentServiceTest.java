package com.logitrack_atom.shipment_service.service;

import com.logitrack_atom.shipment_service.dto.request.ClienteRequest;
import com.logitrack_atom.shipment_service.dto.request.DireccionRequest;
import com.logitrack_atom.shipment_service.dto.request.EnvioRequest;
import com.logitrack_atom.shipment_service.dto.response.ClienteResponse;
import com.logitrack_atom.shipment_service.dto.response.EnvioResponse;
import com.logitrack_atom.shipment_service.entity.Cliente;
import com.logitrack_atom.shipment_service.entity.Envio;
import com.logitrack_atom.shipment_service.entity.HistorialEstadoEnvio;
import com.logitrack_atom.shipment_service.entity.Repartidor;
import com.logitrack_atom.shipment_service.enums.EstadoEnvio;
import com.logitrack_atom.shipment_service.exception.RecursoNoEncontradoException;
import com.logitrack_atom.shipment_service.mapper.ClienteMapper;
import com.logitrack_atom.shipment_service.mapper.EnvioMapper;
import com.logitrack_atom.shipment_service.mapper.HistorialEstadoEnvioMapper;
import com.logitrack_atom.shipment_service.repository.ClienteRepository;
import com.logitrack_atom.shipment_service.repository.EnvioRepository;
import com.logitrack_atom.shipment_service.repository.HistorialEstadoEnvioRepository;
import com.logitrack_atom.shipment_service.repository.RepartidorRepository;
import com.logitrack_atom.shipment_service.service.impl.ShipmentServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias para el servicio de gestión de envíos
 * Trazabilidad del historial inicial mediante {@link ArgumentCaptor}
 * y consultas de recursos bajo el patrón AAA.
 * @author JMatiasF
 * @since 18/10/2026
 */
@ExtendWith(MockitoExtension.class)
public class ShipmentServiceTest {
    @Mock
    private EnvioRepository envioRepository;
    @Mock
    private ClienteRepository clienteRepository;
    @Mock
    private RepartidorRepository repartidorRepository;
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
        // ==========================================
        // 1. ARRANGE (Preparación de datos y Mocks)
        // ==========================================
        String emailCliente = "cliente.existente@correo.com";
        ClienteRequest clienteRequest = new ClienteRequest("María Gómez", emailCliente, "388998877");
        DireccionRequest origenRequest = new DireccionRequest("Balcarce", "45", "San Salvador de Jujuy", "4600");
        DireccionRequest destinoRequest = new DireccionRequest("San Martín", "789", "San Salvador de Jujuy", "4600");

        EnvioRequest request = new EnvioRequest(
                clienteRequest,
                origenRequest,
                destinoRequest,
                BigDecimal.valueOf(1.5),
                BigDecimal.valueOf(10.0),
                BigDecimal.valueOf(10.0),
                BigDecimal.valueOf(10.0)
        );

        UUID clienteId = UUID.randomUUID();
        Cliente clienteExistente = new Cliente();
        clienteExistente.setId(clienteId);
        clienteExistente.setEmail(emailCliente);
        clienteExistente.setNombre("María Gómez");

        Envio envioMock = new Envio();
        envioMock.setId(UUID.randomUUID());
        envioMock.setCodigoSeguimiento("LT-ABC1234567");
        envioMock.setCliente(clienteExistente);
        envioMock.setEstado(EstadoEnvio.PENDIENTE);

        ClienteResponse clienteResponse = new ClienteResponse(clienteId, "María Gómez", emailCliente, "388998877");
        EnvioResponse responseEsperado = new EnvioResponse(
                envioMock.getId(),
                envioMock.getCodigoSeguimiento(),
                clienteResponse,
                null,
                envioMock.getPesoKg(),
                envioMock.getAnchoCm(),
                envioMock.getAltoCm(),
                envioMock.getLargoCm(),
                EstadoEnvio.PENDIENTE,
                null, null, null, null
        );

        when(clienteRepository.findByEmail(emailCliente)).thenReturn(Optional.of(clienteExistente));
        when(envioRepository.existsByCodigoSeguimiento(any())).thenReturn(false);
        when(envioMapper.toEntity(eq(request), eq(clienteExistente), any(String.class))).thenReturn(envioMock);
        when(envioRepository.save(any(Envio.class))).thenReturn(envioMock);
        when(envioMapper.toResponse(envioMock)).thenReturn(responseEsperado);

        // Declaramos el ArgumentCaptor para interceptar el historial también aquí
        ArgumentCaptor<HistorialEstadoEnvio> historialCaptor = ArgumentCaptor.forClass(HistorialEstadoEnvio.class);
        when(historialRepository.save(historialCaptor.capture())).thenReturn(new HistorialEstadoEnvio());

        // ==========================================
        // 2. ACT (Ejecución del método)
        // ==========================================
        EnvioResponse resultado = shipmentService.registrar(request);

        // ==========================================
        // 3. ASSERT (Aserciones y Verificaciones)
        // ==========================================
        assertNotNull(resultado, "La respuesta no debe ser nula");
        assertTrue(resultado.codigoSeguimiento().startsWith("LT-"), "El tracking debe generarse con el prefijo correcto (LT-)");
        assertEquals(EstadoEnvio.PENDIENTE, resultado.estado(), "El estado inicial debe ser PENDIENTE");
        assertEquals(emailCliente, resultado.cliente().email(), "La referencia del cliente debe coincidir");

        // Verificaciones de repositorios
        verify(clienteRepository, times(1)).findByEmail(emailCliente);
        verify(clienteRepository, never()).save(any(Cliente.class)); // Garantiza no duplicar el cliente existente
        verify(envioRepository, times(1)).save(envioMock);
        verify(envioMapper, times(1)).toResponse(envioMock);

        // Verificación profunda del historial mediante ArgumentCaptor
        HistorialEstadoEnvio historialCapturado = historialCaptor.getValue();
        assertNotNull(historialCapturado, "El objeto historial capturado no debe ser nulo");
        assertNull(historialCapturado.getEstadoAnterior(), "El estado anterior en el registro inicial debe ser nulo");
        assertEquals(EstadoEnvio.PENDIENTE, historialCapturado.getEstadoNuevo(), "El estado nuevo registrado debe ser PENDIENTE");
        assertNotNull(historialCapturado.getFechaCambio(), "Debe registrarse una marca de tiempo válida");
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
        // ==========================================
        // 1. ARRANGE (Preparación de datos)
        // ==========================================
        String emailCliente = "cliente.nuevo@correo.com";
        ClienteRequest clienteRequest = new ClienteRequest("Pedro Nuñez", emailCliente, "388112233");
        DireccionRequest origenRequest = new DireccionRequest("Güemes", "10", "San Salvador de Jujuy", "4600");
        DireccionRequest destinoRequest = new DireccionRequest("Mitre", "50", "San Salvador de Jujuy", "4600");

        EnvioRequest request = new EnvioRequest(
                clienteRequest,
                origenRequest,
                destinoRequest,
                BigDecimal.valueOf(3.0),
                BigDecimal.valueOf(15.0),
                BigDecimal.valueOf(15.0),
                BigDecimal.valueOf(15.0)
        );

        Cliente clienteNuevoMapeado = new Cliente();
        clienteNuevoMapeado.setEmail(emailCliente);
        clienteNuevoMapeado.setNombre("Pedro Nuñez");

        Cliente clienteGuardado = new Cliente();
        clienteGuardado.setId(UUID.randomUUID());
        clienteGuardado.setEmail(emailCliente);
        clienteGuardado.setNombre("Pedro Nuñez");

        Envio envioMock = new Envio();
        envioMock.setId(UUID.randomUUID());
        envioMock.setCodigoSeguimiento("LT-XYZ9876543");
        envioMock.setCliente(clienteGuardado);
        envioMock.setEstado(EstadoEnvio.PENDIENTE);

        EnvioResponse responseEsperado = mock(EnvioResponse.class);

        when(clienteRepository.findByEmail(emailCliente)).thenReturn(Optional.empty());
        when(clienteMapper.toEntity(clienteRequest)).thenReturn(clienteNuevoMapeado);
        when(clienteRepository.save(clienteNuevoMapeado)).thenReturn(clienteGuardado);
        when(envioRepository.existsByCodigoSeguimiento(any())).thenReturn(false);
        when(envioMapper.toEntity(eq(request), eq(clienteGuardado), any(String.class))).thenReturn(envioMock);
        when(envioRepository.save(any(Envio.class))).thenReturn(envioMock);
        when(envioMapper.toResponse(envioMock)).thenReturn(responseEsperado);

        // Declaración del ArgumentCaptor para inspeccionar el historial
        ArgumentCaptor<HistorialEstadoEnvio> historialCaptor = ArgumentCaptor.forClass(HistorialEstadoEnvio.class);
        when(historialRepository.save(historialCaptor.capture())).thenReturn(new HistorialEstadoEnvio());

        // ==========================================
        // 2. ACT (Ejecución)
        // ==========================================
        EnvioResponse resultado = shipmentService.registrar(request);

        // ==========================================
        // 3. ASSERT (Verificación profunda con ArgumentCaptor)
        // ==========================================
        assertNotNull(resultado, "La respuesta del envío registrado no debe ser nula");

        verify(clienteRepository, times(1)).findByEmail(emailCliente);
        verify(clienteRepository, times(1)).save(clienteNuevoMapeado); // Confirma el guardado del nuevo cliente
        verify(envioRepository, times(1)).save(envioMock);

        // Inspección directa del objeto guardado en auditoría
        HistorialEstadoEnvio historialCapturado = historialCaptor.getValue();
        assertNotNull(historialCapturado, "El objeto historial capturado no debe ser nulo");
        assertNull(historialCapturado.getEstadoAnterior(), "El estado anterior en el registro inicial debe ser nulo");
        assertEquals(EstadoEnvio.PENDIENTE, historialCapturado.getEstadoNuevo(), "El estado nuevo registrado debe ser PENDIENTE");
        assertNotNull(historialCapturado.getFechaCambio(), "Debe registrarse una marca de tiempo válida de cambio");
    }

    /**
     * Prueba unitaria para validar la búsqueda exitosa de un envío por su UUID.
     */
    @Test
    @DisplayName("Debe retornar un envío exitosamente al buscar por un ID existente")
    void testObtenerPorIdExitoso() {
        // ==========================================
        // 1. ARRANGE
        // ==========================================
        UUID envioId = UUID.randomUUID();

        Envio envioMock = new Envio();
        envioMock.setCodigoSeguimiento("LT-9876543210");
        envioMock.setEstado(EstadoEnvio.PENDIENTE);

        EnvioResponse responseEsperado = mock(EnvioResponse.class);

        when(envioRepository.findById(envioId)).thenReturn(Optional.of(envioMock));
        when(envioMapper.toResponse(envioMock)).thenReturn(responseEsperado);

        // ==========================================
        // 2. ACT
        // ==========================================
        EnvioResponse resultado = shipmentService.obtenerPorId(envioId);

        // ==========================================
        // 3. ASSERT
        // ==========================================
        assertNotNull(resultado, "El resultado de la búsqueda por ID no debe ser nulo");
        verify(envioRepository, times(1)).findById(envioId);
        verify(envioMapper, times(1)).toResponse(envioMock);
    }

    /**
     * Prueba unitaria para validar que se lance RecursoNoEncontradoException
     * si se consulta un UUID de envío inexistente.
     */

    @Test
    @DisplayName("Debe lanzar RecursoNoEncontradoException al buscar un envío con un ID que no existe")
    void testObtenerPorIdNoEncontrado() {
        // ==========================================
        // 1. ARRANGE
        // ==========================================
        UUID envioId = UUID.randomUUID();

        when(envioRepository.findById(envioId)).thenReturn(Optional.empty());

        // ==========================================
        // 2. ACT & 3. ASSERT
        // ==========================================
        Exception exception = assertThrows(RecursoNoEncontradoException.class, () -> {
            shipmentService.obtenerPorId(envioId);
        });

        assertNotNull(exception.getMessage(), "El mensaje de error no debe ser nulo");
        verify(envioRepository, times(1)).findById(envioId);
        verify(envioMapper, never()).toResponse(any());
    }

    /**
     * Prueba unitaria para validar la búsqueda exitosa por código de seguimiento.
     */

    @Test
    @DisplayName("Debe retornar un envío exitosamente al buscar por un código de seguimiento existente")
    void testObtenerPorCodigoSeguimientoExitoso() {
        // ==========================================
        // 1. ARRANGE
        // ==========================================
        String codigoSeguimiento = "LT-1234567890";

        Envio envioMock = new Envio();
        envioMock.setCodigoSeguimiento(codigoSeguimiento);
        envioMock.setEstado(EstadoEnvio.PENDIENTE);

        EnvioResponse responseEsperado = mock(EnvioResponse.class);

        when(envioRepository.findByCodigoSeguimiento(codigoSeguimiento)).thenReturn(Optional.of(envioMock));
        when(envioMapper.toResponse(envioMock)).thenReturn(responseEsperado);

        // ==========================================
        // 2. ACT
        // ==========================================
        EnvioResponse resultado = shipmentService.obtenerPorCodigoSeguimiento(codigoSeguimiento);

        // ==========================================
        // 3. ASSERT
        // ==========================================
        assertNotNull(resultado, "El resultado no debe ser nulo");
        verify(envioRepository, times(1)).findByCodigoSeguimiento(codigoSeguimiento);
        verify(envioMapper, times(1)).toResponse(envioMock);
    }

    /**
     * Prueba unitaria para validar la excepción lanzada al buscar un código de seguimiento inexistente.
     */

    @Test
    @DisplayName("Debe lanzar RecursoNoEncontradoException al buscar por un código de seguimiento que no existe")
    void testObtenerPorCodigoSeguimientoNoEncontrado() {
        // ==========================================
        // 1. ARRANGE
        // ==========================================
        String codigoSeguimiento = "LT-NO-EXISTE";

        when(envioRepository.findByCodigoSeguimiento(codigoSeguimiento)).thenReturn(Optional.empty());

        // ==========================================
        // 2. ACT & 3. ASSERT
        // ==========================================
        Exception exception = assertThrows(RecursoNoEncontradoException.class, () -> {
            shipmentService.obtenerPorCodigoSeguimiento(codigoSeguimiento);
        });

        assertNotNull(exception.getMessage(), "El mensaje de error no debe ser nulo");
        verify(envioRepository, times(1)).findByCodigoSeguimiento(codigoSeguimiento);
        verify(envioMapper, never()).toResponse(any());
    }

    /**
     * Prueba unitaria para verificar que se retornen todos los envíos al listar sin filtro de estado.
     */
    @Test
    @DisplayName("Debe retornar todos los envíos cuando el estado a filtrar es nulo")
    void testListarPorEstadoNulo() {
        // ==========================================
        // 1. ARRANGE
        // ==========================================
        Envio envioMock = new Envio();
        envioMock.setEstado(EstadoEnvio.PENDIENTE);
        List<Envio> listaEnvios = List.of(envioMock);

        EnvioResponse responseMock = mock(EnvioResponse.class);

        when(envioRepository.findAll()).thenReturn(listaEnvios);
        when(envioMapper.toResponse(envioMock)).thenReturn(responseMock);

        // ==========================================
        // 2. ACT
        // ==========================================
        List<EnvioResponse> resultado = shipmentService.listarPorEstado(null);

        // ==========================================
        // 3. ASSERT
        // ==========================================
        assertNotNull(resultado, "La lista resultante no debe ser nula");
        assertEquals(1, resultado.size(), "Debe retornar un elemento");
        verify(envioRepository, times(1)).findAll();
        verify(envioRepository, never()).findByEstado(any());
        verify(envioMapper, times(1)).toResponse(envioMock);
    }

    /**
     * Prueba unitaria para verificar el filtrado correcto por un estado específico.
     */

    @Test
    @DisplayName("Debe retornar los envíos filtrados cuando se especifica un estado válido")
    void testListarPorEstadoEspecifico() {
        // ==========================================
        // 1. ARRANGE
        // ==========================================
        EstadoEnvio estadoBuscado = EstadoEnvio.PENDIENTE;
        Envio envioMock = new Envio();
        envioMock.setEstado(estadoBuscado);
        List<Envio> listaEnvios = List.of(envioMock);

        EnvioResponse responseMock = mock(EnvioResponse.class);

        when(envioRepository.findByEstado(estadoBuscado)).thenReturn(listaEnvios);
        when(envioMapper.toResponse(envioMock)).thenReturn(responseMock);

        // ==========================================
        // 2. ACT
        // ==========================================
        List<EnvioResponse> resultado = shipmentService.listarPorEstado(estadoBuscado);

        // ==========================================
        // 3. ASSERT
        // ==========================================
        assertNotNull(resultado, "La lista filtrada no debe ser nula");
        assertEquals(1, resultado.size(), "Debe retornar un elemento filtrado");
        verify(envioRepository, times(1)).findByEstado(estadoBuscado);
        verify(envioRepository, never()).findAll();
        verify(envioMapper, times(1)).toResponse(envioMock);
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
    @ParameterizedTest
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
        // ==========================================
        // 1. ARRANGE (Preparación común)
        // ==========================================
        UUID envioId = UUID.randomUUID();
        Envio envioMock = new Envio();
        envioMock.setId(envioId);
        envioMock.setEstado(estadoActual);

        // Siempre se busca la entidad primero
        when(envioRepository.findById(envioId)).thenReturn(Optional.of(envioMock));

        // ==========================================
        // 2. ACT & 3. ASSERT (Ejecución y Verificación)
        // ==========================================
        if (esPermitida) {
            // Configurar stubs solo para los casos exitosos donde sí se guarda y mapea
            when(envioRepository.save(any(Envio.class))).thenReturn(envioMock);
            when(historialRepository.save(any(HistorialEstadoEnvio.class))).thenReturn(new HistorialEstadoEnvio());
            when(envioMapper.toResponse(any(Envio.class))).thenReturn(mock(EnvioResponse.class));

            // Si transiciona a EN_RUTA, asignamos un repartidor para cumplir con la regla de negocio del servicio
            if (estadoNuevo == EstadoEnvio.EN_RUTA) {
                envioMock.setRepartidor(new Repartidor());
            }

            assertDoesNotThrow(() -> shipmentService.cambiarEstado(envioId, estadoNuevo),
                    () -> String.format("La transición de %s a %s debería estar PERMITIDA", estadoActual, estadoNuevo));

            assertEquals(estadoNuevo, envioMock.getEstado(), "El estado del envío debe actualizarse correctamente");
            verify(envioRepository, times(1)).save(envioMock);
            verify(historialRepository, times(1)).save(any(HistorialEstadoEnvio.class));
        } else {
            // En los casos prohibidos, el servicio lanza excepción antes de guardar
            assertThrows(Exception.class, () -> shipmentService.cambiarEstado(envioId, estadoNuevo),
                    () -> String.format("La transición de %s a %s debería estar PROHIBIDA", estadoActual, estadoNuevo));

            verify(envioRepository, never()).save(any(Envio.class));
            verify(historialRepository, never()).save(any(HistorialEstadoEnvio.class));
        }
    }

}
