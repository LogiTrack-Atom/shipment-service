package com.logitrack_atom.shipment_service.service;

import com.logitrack_atom.shipment_service.dto.request.ClienteRequest;
import com.logitrack_atom.shipment_service.dto.request.DireccionRequest;
import com.logitrack_atom.shipment_service.dto.request.EnvioRequest;
import com.logitrack_atom.shipment_service.dto.response.ClienteResponse;
import com.logitrack_atom.shipment_service.dto.response.EnvioResponse;
import com.logitrack_atom.shipment_service.entity.Cliente;
import com.logitrack_atom.shipment_service.entity.Envio;
import com.logitrack_atom.shipment_service.entity.HistorialEstadoEnvio;
import com.logitrack_atom.shipment_service.enums.EstadoEnvio;
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

    @Test
    @DisplayName("Debe registrar un envío exitosamente creando o recuperando el cliente y guardando su historial inicial")
    void testRegistrarEnvioExitoso() {
        // Arrange (Preparación de datos de entrada)
        String emailCliente = "test@correo.com";
        ClienteRequest clienteRequest = new ClienteRequest("Juan Pérez", emailCliente, "388123456");
        DireccionRequest origenRequest = new DireccionRequest("Belgrano", "123", "San Salvador de Jujuy", "4600");
        DireccionRequest destinoRequest = new DireccionRequest("Alvear", "456", "San Salvador de Jujuy", "4600");

        EnvioRequest request = new EnvioRequest(
                clienteRequest,
                origenRequest,
                destinoRequest,
                BigDecimal.valueOf(2.5),
                BigDecimal.valueOf(10.0),
                BigDecimal.valueOf(10.0),
                BigDecimal.valueOf(10.0)
        );

        Cliente clienteMock = new Cliente();
        clienteMock.setId(UUID.randomUUID());
        clienteMock.setEmail(emailCliente);

        Envio envioMock = new Envio();
        envioMock.setId(UUID.randomUUID());
        envioMock.setCodigoSeguimiento("LT-1234567890");
        envioMock.setCliente(clienteMock);
        envioMock.setEstado(EstadoEnvio.PENDIENTE);

        EnvioResponse responseEsperado = new EnvioResponse(
                envioMock.getId(),
                envioMock.getCodigoSeguimiento(),
                new ClienteResponse(clienteMock.getId(), "Juan Pérez", emailCliente, "388123456"),
                null,
                envioMock.getPesoKg(),
                envioMock.getAnchoCm(),
                envioMock.getAltoCm(),
                envioMock.getLargoCm(),
                EstadoEnvio.PENDIENTE,
                null, null, null, null
        );

        // Configuración de comportamientos (Mocks)
        when(clienteRepository.findByEmail(emailCliente)).thenReturn(Optional.of(clienteMock));
        when(envioRepository.existsByCodigoSeguimiento(any())).thenReturn(false);
        when(envioMapper.toEntity(eq(request), eq(clienteMock), any(String.class))).thenReturn(envioMock);
        when(envioRepository.save(any(Envio.class))).thenReturn(envioMock);
        when(historialRepository.save(any(HistorialEstadoEnvio.class))).thenReturn(new HistorialEstadoEnvio());
        when(envioMapper.toResponse(envioMock)).thenReturn(responseEsperado);

        // Act (Ejecución del metodo a probar)
        EnvioResponse resultado = shipmentService.registrar(request);

        // Assert (Verificaciones y comprobaciones)
        assertNotNull(resultado);
        assertEquals("LT-1234567890", resultado.codigoSeguimiento());
        assertEquals(EstadoEnvio.PENDIENTE, resultado.estado());

        // Verificamos que se hayan invocado los métodos de guardado y auditoría esperados
        verify(clienteRepository, times(1)).findByEmail(emailCliente);
        verify(envioRepository, times(1)).save(envioMock);
        verify(historialRepository, times(1)).save(any(HistorialEstadoEnvio.class));
        verify(envioMapper, times(1)).toResponse(envioMock);
    }
    @Test
    @DisplayName("Debe retornar un envío exitosamente al buscar por un ID existente")
    void testObtenerPorIdExitoso() {
        // Arrange (Preparación)
        UUID envioId = UUID.randomUUID();

        Envio envioMock = new Envio();
        envioMock.setCodigoSeguimiento("LT-9876543210");
        envioMock.setEstado(EstadoEnvio.PENDIENTE);

        EnvioResponse responseEsperado = mock(EnvioResponse.class);

        // Configuración de Mocks
        when(envioRepository.findById(envioId)).thenReturn(Optional.of(envioMock));
        when(envioMapper.toResponse(envioMock)).thenReturn(responseEsperado);

        // Act (Ejecución)
        EnvioResponse resultado = shipmentService.obtenerPorId(envioId);

        // Assert (Verificación)
        assertNotNull(resultado);
        verify(envioRepository, times(1)).findById(envioId);
        verify(envioMapper, times(1)).toResponse(envioMock);
    }

    @Test
    @DisplayName("Debe lanzar RecursoNoEncontradoException al buscar un envío con un ID que no existe")
    void testObtenerPorIdNoEncontrado() {
        // Arrange (Preparación)
        UUID envioId = UUID.randomUUID();

        when(envioRepository.findById(envioId)).thenReturn(Optional.empty());

        // Act & Assert (Ejecución y Verificación de Excepción)
        Exception exception = assertThrows(com.logitrack_atom.shipment_service.exception.RecursoNoEncontradoException.class, () -> {
            shipmentService.obtenerPorId(envioId);
        });

        assertNotNull(exception.getMessage());
        verify(envioRepository, times(1)).findById(envioId);
        verify(envioMapper, never()).toResponse(any());
    }
    @Test
    @DisplayName("Debe retornar un envío exitosamente al buscar por un código de seguimiento existente")
    void testObtenerPorCodigoSeguimientoExitoso() {
        // Arrange (Preparación)
        String codigoSeguimiento = "LT-1234567890";

        Envio envioMock = new Envio();
        envioMock.setCodigoSeguimiento(codigoSeguimiento);
        envioMock.setEstado(EstadoEnvio.PENDIENTE);

        EnvioResponse responseEsperado = mock(EnvioResponse.class);

        // Configuración de Mocks
        when(envioRepository.findByCodigoSeguimiento(codigoSeguimiento)).thenReturn(Optional.of(envioMock));
        when(envioMapper.toResponse(envioMock)).thenReturn(responseEsperado);

        // Act (Ejecución)
        EnvioResponse resultado = shipmentService.obtenerPorCodigoSeguimiento(codigoSeguimiento);

        // Assert (Verificación)
        assertNotNull(resultado);
        verify(envioRepository, times(1)).findByCodigoSeguimiento(codigoSeguimiento);
        verify(envioMapper, times(1)).toResponse(envioMock);
    }

    @Test
    @DisplayName("Debe lanzar RecursoNoEncontradoException al buscar por un código de seguimiento que no existe")
    void testObtenerPorCodigoSeguimientoNoEncontrado() {
        // Arrange (Preparación)
        String codigoSeguimiento = "LT-NO-EXISTE";

        when(envioRepository.findByCodigoSeguimiento(codigoSeguimiento)).thenReturn(Optional.empty());

        // Act & Assert (Ejecución y Verificación de Excepción)
        Exception exception = assertThrows(com.logitrack_atom.shipment_service.exception.RecursoNoEncontradoException.class, () -> {
            shipmentService.obtenerPorCodigoSeguimiento(codigoSeguimiento);
        });

        assertNotNull(exception.getMessage());
        verify(envioRepository, times(1)).findByCodigoSeguimiento(codigoSeguimiento);
        verify(envioMapper, never()).toResponse(any());
    }

    @Test
    @DisplayName("Debe retornar todos los envíos cuando el estado a filtrar es nulo")
    void testListarPorEstadoNulo() {
        // Arrange (Preparación)
        Envio envioMock = new Envio();
        envioMock.setEstado(EstadoEnvio.PENDIENTE);
        List<Envio> listaEnvios = List.of(envioMock);

        EnvioResponse responseMock = mock(EnvioResponse.class);

        // Configuración de Mocks
        when(envioRepository.findAll()).thenReturn(listaEnvios);
        when(envioMapper.toResponse(envioMock)).thenReturn(responseMock);

        // Act (Ejecución)
        List<EnvioResponse> resultado = shipmentService.listarPorEstado(null);

        // Assert (Verificación)
        assertNotNull(resultado);
        assertEquals(1, resultado.size());
        verify(envioRepository, times(1)).findAll();
        verify(envioRepository, never()).findByEstado(any());
        verify(envioMapper, times(1)).toResponse(envioMock);
    }

    @Test
    @DisplayName("Debe retornar los envíos filtrados cuando se especifica un estado válido")
    void testListarPorEstadoEspecifico() {
        // Arrange (Preparación)
        EstadoEnvio estadoBuscado = EstadoEnvio.PENDIENTE;
        Envio envioMock = new Envio();
        envioMock.setEstado(estadoBuscado);
        List<Envio> listaEnvios = List.of(envioMock);

        EnvioResponse responseMock = mock(EnvioResponse.class);

        // Configuración de Mocks
        when(envioRepository.findByEstado(estadoBuscado)).thenReturn(listaEnvios);
        when(envioMapper.toResponse(envioMock)).thenReturn(responseMock);

        // Act (Ejecución)
        List<EnvioResponse> resultado = shipmentService.listarPorEstado(estadoBuscado);

        // Assert (Verificación)
        assertNotNull(resultado);
        assertEquals(1, resultado.size());
        verify(envioRepository, times(1)).findByEstado(estadoBuscado);
        verify(envioRepository, never()).findAll();
        verify(envioMapper, times(1)).toResponse(envioMock);
    }

}
