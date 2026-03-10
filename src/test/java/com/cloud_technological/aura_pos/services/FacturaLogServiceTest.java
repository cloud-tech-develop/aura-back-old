package com.cloud_technological.aura_pos.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.FacturaEntity;
import com.cloud_technological.aura_pos.entity.FacturaLogEntity;
import com.cloud_technological.aura_pos.repositories.factura_log.FacturaLogJPARepository;
import com.cloud_technological.aura_pos.repositories.facturacion.FacturaJPARepository;
import com.cloud_technological.aura_pos.services.implementations.FacturaLogServiceImpl;
import com.cloud_technological.aura_pos.utils.FacturaLogEvento;

/**
 * Tests para HU-007: Logs de Facturas (Async)
 */
@ExtendWith(MockitoExtension.class)
class FacturaLogServiceTest {

    @Mock
    private FacturaLogJPARepository facturaLogRepository;

    @Mock
    private FacturaJPARepository facturaRepository;

    @InjectMocks
    private FacturaLogServiceImpl facturaLogService;

    private FacturaEntity facturaMock;

    @BeforeEach
    void setUp() {
        EmpresaEntity empresaMock = new EmpresaEntity();
        empresaMock.setId(1);
        
        facturaMock = new FacturaEntity();
        facturaMock.setId(1L);
        facturaMock.setEmpresa(empresaMock);
        facturaMock.setPrefijo("FV");
        facturaMock.setConsecutivo(1L);
    }

    /**
     * Escenario: Registrar log exitosamente de forma asíncrona
     * Dado: Datos válidos de factura
     * Cuando: Se registra el log
     * Entonces: Se guarda en la base de datos
     */
    @Test
    void testRegistrarLogAsync_Exitoso() {
        // Arrange
        when(facturaRepository.findById(1L)).thenReturn(Optional.of(facturaMock));
        when(facturaLogRepository.save(any(FacturaLogEntity.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        facturaLogService.registrarLogAsync(
            1L,
            FacturaLogEvento.CREACION,
            null,
            "PENDIENTE",
            buildDatosFactura(),
            1,
            "Factura creada exitosamente",
            null
        );

        // Assert
        verify(facturaLogRepository).save(any(FacturaLogEntity.class));
    }

    /**
     * Escenario: Registrar log de cambio de estado DIAN
     * Dado: Un cambio de estado de PENDIENTE a AUTORIZADO
     * Cuando: Se registra el log
     * Entonces: Se guarda con los estados anterior y nuevo
     */
    @Test
    void testRegistrarLogCambioEstado_Exitoso() {
        // Arrange
        when(facturaRepository.findById(1L)).thenReturn(Optional.of(facturaMock));
        when(facturaLogRepository.save(any(FacturaLogEntity.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        facturaLogService.registrarLogAsync(
            1L,
            FacturaLogEvento.CAMBIO_ESTADO_DIAN,
            "PENDIENTE",
            "AUTORIZADO",
            null,
            1,
            "Factura autorizada por DIAN",
            buildMetadata()
        );

        // Assert
        verify(facturaLogRepository).save(argThat(log -> 
            "PENDIENTE".equals(log.getEstadoAnterior()) &&
            "AUTORIZADO".equals(log.getEstadoNuevo())
        ));
    }

    /**
     * Escenario: Silencioso cuando factura no existe
     * Dado: Una factura que no existe
     * Cuando: Se intenta registrar el log
     * Entonces: No lanza excepción, solo hace warn
     */
    @Test
    void testRegistrarLogFacturaNoExiste_Silencioso() {
        // Arrange
        when(facturaRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert - No debe lanzar excepción
        assertDoesNotThrow(() -> 
            facturaLogService.registrarLogAsync(
                999L,
                FacturaLogEvento.CREACION,
                null,
                "PENDIENTE",
                null,
                1,
                "Test",
                null
            )
        );
        
        verify(facturaLogRepository, never()).save(any());
    }

    /**
     * Escenario: Silencioso cuando falla el guardado
     * Dado: Un error al guardar en la base de datos
     * Cuando: Se intenta registrar el log
     * Entonces: No lanza excepción al proceso principal
     */
    @Test
    void testRegistrarLogErrorGuardado_Silencioso() {
        // Arrange
        when(facturaRepository.findById(1L)).thenReturn(Optional.of(facturaMock));
        when(facturaLogRepository.save(any(FacturaLogEntity.class)))
            .thenThrow(new RuntimeException("Error de base de datos"));

        // Act & Assert - No debe lanzar excepción
        assertDoesNotThrow(() -> 
            facturaLogService.registrarLogAsync(
                1L,
                FacturaLogEvento.CREACION,
                null,
                "PENDIENTE",
                null,
                1,
                "Test",
                null
            )
        );
    }

    /**
     * Escenario: Obtener logs por factura
     * Dado: Una factura con múltiples logs
     * Cuando: Se consultan los logs
     * Entonces: Retorna todos los logs ordenados por fecha
     */
    @Test
    void testObtenerPorFactura_Exitoso() {
        // Arrange
        FacturaLogEntity log1 = new FacturaLogEntity();
        log1.setId(1L);
        log1.setEvento(FacturaLogEvento.CREACION);
        log1.setCreatedAt(LocalDateTime.now().plusHours(1));

        FacturaLogEntity log2 = new FacturaLogEntity();
        log2.setId(2L);
        log2.setEvento(FacturaLogEvento.CAMBIO_ESTADO_DIAN);
        log2.setCreatedAt(LocalDateTime.now());

        when(facturaLogRepository.findByFacturaIdOrderByCreatedAtDesc(1L))
            .thenReturn(List.of(log1, log2));

        // Act
        var resultado = facturaLogService.obtenerPorFactura(1L);

        // Assert
        assertNotNull(resultado);
        assertEquals(2, resultado.size());
    }

    private java.util.Map<String, Object> buildDatosFactura() {
        java.util.Map<String, Object> datos = new java.util.HashMap<>();
        datos.put("id", 1L);
        datos.put("prefijo", "FV");
        datos.put("consecutivo", 1L);
        datos.put("valor", new BigDecimal("100000"));
        return datos;
    }

    private java.util.Map<String, Object> buildMetadata() {
        java.util.Map<String, Object> metadata = new java.util.HashMap<>();
        metadata.put("codigoRespuesta", "201");
        metadata.put("descripcionRespuesta", "Factura autorizada");
        return metadata;
    }
}
