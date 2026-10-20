package com.logitrack_atom.shipment_service.service;

import com.logitrack_atom.shipment_service.dto.request.RepartidorRequest;
import com.logitrack_atom.shipment_service.dto.response.RepartidorResponse;
import com.logitrack_atom.shipment_service.entity.Repartidor;
import com.logitrack_atom.shipment_service.enums.EstadoRepartidor;
import com.logitrack_atom.shipment_service.mapper.RepartidorMapper;
import com.logitrack_atom.shipment_service.repository.RepartidorRepository;
import com.logitrack_atom.shipment_service.service.impl.RepartidorServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Clase de pruebas unitarias para validar la lógica de negocio y las reglas
 * del servicio de repartidores (RepartidorServiceImpl).
 * Utiliza JUnit 5 y Mockito para aislar el comportamiento de los repositorios y mappers.
 * @author JMatiasF
 * @since 20/10/2026
 */
@ExtendWith(MockitoExtension.class)
public class RepartidorTest {
    @Mock
    private RepartidorRepository repartidorRepository;

    @Mock
    private RepartidorMapper repartidorMapper;

    @InjectMocks
    private RepartidorServiceImpl repartidorService;

    /**
     * Prueba unitaria para validar el registro exitoso de un nuevo repartidor
     * aplicando rigurosamente el patrón AAA (Arrange, Act, Assert).
     */
    @Test
    @DisplayName("Debe registrar un repartidor exitosamente aplicando el patrón AAA")
    void testRegistrarRepartidorExitoso() {
        // ==========================================
        // 1. ARRANGE (Preparación de datos y mocks)
        // ==========================================
        RepartidorRequest request = new RepartidorRequest("Carlos Gómez", "Moto");

        Repartidor repartidorEntity = new Repartidor();
        repartidorEntity.setNombre("Carlos Gómez");
        repartidorEntity.setTipoVehiculo("Moto");
        repartidorEntity.setEstado(EstadoRepartidor.ACTIVO);

        Repartidor repartidorGuardado = new Repartidor();
        ReflectionTestUtils.setField(repartidorGuardado, "id", UUID.randomUUID());
        repartidorGuardado.setNombre("Carlos Gómez");
        repartidorGuardado.setTipoVehiculo("Moto");
        repartidorGuardado.setEstado(EstadoRepartidor.ACTIVO);

        RepartidorResponse responseEsperado = new RepartidorResponse(
                repartidorGuardado.getId(),
                "Carlos Gómez",
                "Moto",
                EstadoRepartidor.ACTIVO
        );

        // Configuración del comportamiento simulado de los componentes dependientes
        when(repartidorMapper.toEntity(request)).thenReturn(repartidorEntity);
        when(repartidorRepository.save(any(Repartidor.class))).thenReturn(repartidorGuardado);
        when(repartidorMapper.toResponse(repartidorGuardado)).thenReturn(responseEsperado);

        // ==========================================
        // 2. ACT (Ejecución del método a probar)
        // ==========================================
        RepartidorResponse resultado = repartidorService.registrar(request);

        // ==========================================
        // 3. ASSERT (Verificaciones y comprobaciones)
        // ==========================================
        assertNotNull(resultado, "La respuesta del repartidor registrado no debe ser nula");
        assertEquals("Carlos Gómez", resultado.nombre(), "El nombre del repartidor debe coincidir");
        assertEquals("Moto", resultado.tipoVehiculo(), "El tipo de vehículo debe coincidir");
        assertEquals(EstadoRepartidor.ACTIVO, resultado.estado(), "El estado inicial debe ser ACTIVO");

        // Verificación de llamadas a las dependencias simuladas
        verify(repartidorMapper, times(1)).toEntity(request);
        verify(repartidorRepository, times(1)).save(any(Repartidor.class));
        verify(repartidorMapper, times(1)).toResponse(repartidorGuardado);
    }

    /**
     * Prueba unitaria para validar que el método listar devuelva todos los registros
     * cuando el parámetro de estado recibido es nulo.
     */
    @Test
    @DisplayName("Debe listar todos los repartidores cuando el estado a filtrar es nulo")
    void testListarRepartidoresEstadoNulo() {
        // ==========================================
        // 1. ARRANGE (Preparación)
        // ==========================================
        Repartidor repartidorEntity = new Repartidor();
        ReflectionTestUtils.setField(repartidorEntity, "id", UUID.randomUUID());
        repartidorEntity.setNombre("Carlos Gómez");
        repartidorEntity.setTipoVehiculo("Moto");
        repartidorEntity.setEstado(EstadoRepartidor.ACTIVO);

        List<Repartidor> listaEntidades = List.of(repartidorEntity);

        RepartidorResponse responseMock = new RepartidorResponse(
                repartidorEntity.getId(),
                repartidorEntity.getNombre(),
                repartidorEntity.getTipoVehiculo(),
                repartidorEntity.getEstado()
        );

        when(repartidorRepository.findAll()).thenReturn(listaEntidades);
        when(repartidorMapper.toResponse(repartidorEntity)).thenReturn(responseMock);

        // ==========================================
        // 2. ACT (Ejecución)
        // ==========================================
        List<RepartidorResponse> resultado = repartidorService.listar(null);

        // ==========================================
        // 3. ASSERT (Verificación)
        // ==========================================
        assertNotNull(resultado, "La lista resultante no debe ser nula");
        assertEquals(1, resultado.size(), "Debe retornar exactamente un elemento");
        assertEquals("Carlos Gómez", resultado.get(0).nombre(), "El nombre del elemento debe coincidir");

        verify(repartidorRepository, times(1)).findAll();
        verify(repartidorRepository, never()).findByEstado(any());
        verify(repartidorMapper, times(1)).toResponse(repartidorEntity);
    }

    /**
     * Prueba unitaria para validar que el metodo listar filtre correctamente los repartidores
     * al recibir un estado específico (por ejemplo, ACTIVO).
     */
    @Test
    @DisplayName("Debe listar los repartidores filtrados cuando se especifica un estado válido")
    void testListarRepartidoresEstadoEspecifico() {
        // ==========================================
        // 1. ARRANGE (Preparación)
        // ==========================================
        EstadoRepartidor estadoBuscado = EstadoRepartidor.ACTIVO;

        Repartidor repartidorEntity = new Repartidor();
        ReflectionTestUtils.setField(repartidorEntity, "id", UUID.randomUUID());
        repartidorEntity.setNombre("Ana Torres");
        repartidorEntity.setTipoVehiculo("Bicicleta");
        repartidorEntity.setEstado(estadoBuscado);

        List<Repartidor> listaEntidades = List.of(repartidorEntity);

        RepartidorResponse responseMock = new RepartidorResponse(
                repartidorEntity.getId(),
                repartidorEntity.getNombre(),
                repartidorEntity.getTipoVehiculo(),
                repartidorEntity.getEstado()
        );

        when(repartidorRepository.findByEstado(estadoBuscado)).thenReturn(listaEntidades);
        when(repartidorMapper.toResponse(repartidorEntity)).thenReturn(responseMock);

        // ==========================================
        // 2. ACT (Ejecución)
        // ==========================================
        List<RepartidorResponse> resultado = repartidorService.listar(estadoBuscado);

        // ==========================================
        // 3. ASSERT (Verificación)
        // ==========================================
        assertNotNull(resultado, "La lista filtrada no debe ser nula");
        assertEquals(1, resultado.size(), "Debe retornar un elemento filtrado");
        assertEquals(estadoBuscado, resultado.get(0).estado(), "El estado debe coincidir con el filtro aplicado");

        verify(repartidorRepository, times(1)).findByEstado(estadoBuscado);
        verify(repartidorRepository, never()).findAll();
        verify(repartidorMapper, times(1)).toResponse(repartidorEntity);
    }
    /**
     * Prueba unitaria para validar que se retorne un repartidor exitosamente
     * al buscar por un UUID existente en el repositorio.
     */
    @Test
    @DisplayName("Debe retornar un repartidor exitosamente al buscar por un ID existente")
    void testObtenerPorIdExitoso() {
        // ==========================================
        // 1. ARRANGE (Preparación)
        // ==========================================
        UUID repartidorId = UUID.randomUUID();

        Repartidor repartidorEntity = new Repartidor();
        ReflectionTestUtils.setField(repartidorEntity, "id", repartidorId);
        repartidorEntity.setNombre("Carlos Gómez");
        repartidorEntity.setTipoVehiculo("Moto");
        repartidorEntity.setEstado(EstadoRepartidor.ACTIVO);

        RepartidorResponse responseEsperado = new RepartidorResponse(
                repartidorId,
                "Carlos Gómez",
                "Moto",
                EstadoRepartidor.ACTIVO
        );

        when(repartidorRepository.findById(repartidorId)).thenReturn(Optional.of(repartidorEntity));
        when(repartidorMapper.toResponse(repartidorEntity)).thenReturn(responseEsperado);

        // ==========================================
        // 2. ACT (Ejecución)
        // ==========================================
        RepartidorResponse resultado = repartidorService.obtenerPorId(repartidorId);

        // ==========================================
        // 3. ASSERT (Verificación)
        // ==========================================
        assertNotNull(resultado, "El resultado no debe ser nulo");
        assertEquals(repartidorId, resultado.id(), "El ID del repartidor debe coincidir");
        assertEquals("Carlos Gómez", resultado.nombre(), "El nombre debe coincidir");

        verify(repartidorRepository, times(1)).findById(repartidorId);
        verify(repartidorMapper, times(1)).toResponse(repartidorEntity);
    }

    /**
     * Prueba unitaria para validar que se lance la excepción RecursoNoEncontradoException
     * cuando se intenta buscar un repartidor con un UUID que no existe.
     */
    @Test
    @DisplayName("Debe lanzar RecursoNoEncontradoException al buscar un repartidor con un ID inexistente")
    void testObtenerPorIdNoEncontrado() {
        // ==========================================
        // 1. ARRANGE (Preparación)
        // ==========================================
        UUID repartidorId = UUID.randomUUID();

        when(repartidorRepository.findById(repartidorId)).thenReturn(Optional.empty());

        // ==========================================
        // 2. ACT & 3. ASSERT (Ejecución y Verificación de Excepción)
        // ==========================================
        Exception exception = assertThrows(
                com.logitrack_atom.shipment_service.exception.RecursoNoEncontradoException.class,
                () -> repartidorService.obtenerPorId(repartidorId),
                "Debería lanzar RecursoNoEncontradoException"
        );

        assertNotNull(exception.getMessage(), "El mensaje de la excepción no debe ser nulo");

        verify(repartidorRepository, times(1)).findById(repartidorId);
        verify(repartidorMapper, never()).toResponse(any());
    }
}

