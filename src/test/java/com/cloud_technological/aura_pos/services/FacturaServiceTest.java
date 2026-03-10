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
import com.cloud_technological.aura_pos.entity.UsuarioEntity;
import com.cloud_technological.aura_pos.entity.VentaEntity;
import com.cloud_technological.aura_pos.mappers.FacturaMapper;
import com.cloud_technological.aura_pos.repositories.empresas.EmpresaJPARepository;
import com.cloud_technological.aura_pos.repositories.facturacion.FacturaJPARepository;
import com.cloud_technological.aura_pos.repositories.facturacion.FacturaQueryRepository;
import com.cloud_technological.aura_pos.repositories.facturacion.ReciboPagoJPARepository;
import com.cloud_technological.aura_pos.repositories.users.UsuarioJPARepository;
import com.cloud_technological.aura_pos.repositories.ventas.VentaJPARepository;
import com.cloud_technological.aura_pos.repositories.venta_pago.VentaPagoJPARepository;
import com.cloud_technological.aura_pos.services.implementations.FacturaServiceImpl;
import com.cloud_technological.aura_pos.utils.GlobalException;

/**
 * Tests para HU-003: Generación Automática de Facturas
 */
@ExtendWith(MockitoExtension.class)
class FacturaServiceTest {

    @Mock
    private FacturaJPARepository facturaJPARepository;

    @Mock
    private FacturaQueryRepository facturaQueryRepository;

    @Mock
    private ReciboPagoJPARepository reciboPagoJPARepository;

    @Mock
    private VentaJPARepository ventaJPARepository;

    @Mock
    private VentaPagoJPARepository ventaPagoJPARepository;

    @Mock
    private EmpresaJPARepository empresaJPARepository;

    @Mock
    private UsuarioJPARepository usuarioJPARepository;

    @Mock
    private FacturaMapper facturaMapper;

    @Mock
    private FacturaLogService facturaLogService;

    @InjectMocks
    private FacturaServiceImpl facturaService;

    private EmpresaEntity empresaMock;
    private UsuarioEntity usuarioMock;
    private VentaEntity ventaMock;

    @BeforeEach
    void setUp() {
        empresaMock = new EmpresaEntity();
        empresaMock.setId(1);
        empresaMock.setNit("12345678");

        usuarioMock = new UsuarioEntity();
        usuarioMock.setId(1);

        ventaMock = new VentaEntity();
        ventaMock.setId(1L);
        ventaMock.setEmpresa(empresaMock);
        ventaMock.setTotalPagar(new BigDecimal("100000"));
        ventaMock.setImpuestosTotal(new BigDecimal("19000"));
    }

    /**
     * Escenario: Crear factura desde venta exitosamente
     * Dado: Una venta válida sin factura existente
     * Cuando: Se genera la factura
     * Entonces: Se crea la factura con CUFE y se registran los pagos
     */
    @Test
    void testCrearDesdeVenta_Exitoso() {
        // Arrange
        when(empresaJPARepository.findById(1)).thenReturn(Optional.of(empresaMock));
        when(usuarioJPARepository.findById(1)).thenReturn(Optional.of(usuarioMock));
        when(ventaJPARepository.findById(1L)).thenReturn(Optional.of(ventaMock));
        when(facturaJPARepository.findByVentaId(1L)).thenReturn(Optional.empty());
        when(facturaQueryRepository.obtenerSiguienteConsecutivo(1)).thenReturn(1L);
        
        FacturaEntity facturaGuardada = new FacturaEntity();
        facturaGuardada.setId(1L);
        facturaGuardada.setPrefijo("FV");
        facturaGuardada.setConsecutivo(1L);
        facturaGuardada.setCufe("test-cufe-hash");
        facturaGuardada.setEmpresa(empresaMock);
        facturaGuardada.setUsuario(usuarioMock);
        facturaGuardada.setVenta(ventaMock);
        facturaGuardada.setCreatedAt(LocalDateTime.now());
        
        when(facturaJPARepository.save(any(FacturaEntity.class))).thenReturn(facturaGuardada);
        when(ventaPagoJPARepository.findByVentaId(1L)).thenReturn(List.of());

        com.cloud_technological.aura_pos.dto.facturacion.FacturaDto dtoMock = 
            new com.cloud_technological.aura_pos.dto.facturacion.FacturaDto();
        dtoMock.setId(1L);
        dtoMock.setPrefijo("FV");
        dtoMock.setConsecutivo(1L);
        when(facturaMapper.toDto(facturaGuardada)).thenReturn(dtoMock);

        // Act
        var resultado = facturaService.crearDesdeVenta(1L, 1, 1);

        // Assert
        assertNotNull(resultado);
        assertEquals("FV", resultado.getPrefijo());
        assertEquals(1L, resultado.getConsecutivo());
        verify(facturaLogService).registrarLogAsync(
            eq(1L), eq("CREACION"), isNull(), eq("PENDIENTE"), any(), eq(1), any(), isNull());
    }

    /**
     * Escenario: Error al crear factura con venta inexistente
     * Dado: Una venta que no existe
     * Cuando: Se intenta generar la factura
     * Entonces: Lanza excepción 404
     */
    @Test
    void testCrearDesdeVenta_VentaNoExiste_LanzaExcepcion() {
        // Arrange
        when(empresaJPARepository.findById(1)).thenReturn(Optional.of(empresaMock));
        when(usuarioJPARepository.findById(1)).thenReturn(Optional.of(usuarioMock));
        when(ventaJPARepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        GlobalException exception = assertThrows(GlobalException.class, 
            () -> facturaService.crearDesdeVenta(999L, 1, 1));
        
        assertEquals(404, exception.getStatus().value());
        assertTrue(exception.getMessage().contains("Venta no encontrada"));
    }

    /**
     * Escenario: Error al crear factura para venta que ya tiene factura
     * Dado: Una venta que ya tiene factura asociada
     * Cuando: Se intenta generar la factura
     * Entonces: Lanza excepción 400
     */
    @Test
    void testCrearDesdeVenta_FacturaYaExiste_LanzaExcepcion() {
        // Arrange
        when(empresaJPARepository.findById(1)).thenReturn(Optional.of(empresaMock));
        when(usuarioJPARepository.findById(1)).thenReturn(Optional.of(usuarioMock));
        when(ventaJPARepository.findById(1L)).thenReturn(Optional.of(ventaMock));
        
        FacturaEntity facturaExistente = new FacturaEntity();
        when(facturaJPARepository.findByVentaId(1L)).thenReturn(Optional.of(facturaExistente));

        // Act & Assert
        GlobalException exception = assertThrows(GlobalException.class, 
            () -> facturaService.crearDesdeVenta(1L, 1, 1));
        
        assertEquals(400, exception.getStatus().value());
        assertTrue(exception.getMessage().contains("Ya existe una factura"));
    }

    /**
     * Escenario: Error al crear factura con empresa inexistente
     * Dado: Una empresa que no existe
     * Cuando: Se intenta generar la factura
     * Entonces: Lanza excepción 404
     */
    @Test
    void testCrearDesdeVenta_EmpresaNoExiste_LanzaExcepcion() {
        // Arrange
        when(empresaJPARepository.findById(999)).thenReturn(Optional.empty());

        // Act & Assert
        GlobalException exception = assertThrows(GlobalException.class, 
            () -> facturaService.crearDesdeVenta(1L, 999, 1));
        
        assertEquals(404, exception.getStatus().value());
        assertTrue(exception.getMessage().contains("Empresa no encontrada"));
    }
}
