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
import org.springframework.http.HttpStatus;

import com.cloud_technological.aura_pos.dto.facturacion.NotaContableDto;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.FacturaEntity;
import com.cloud_technological.aura_pos.entity.NotaContableEntity;
import com.cloud_technological.aura_pos.entity.UsuarioEntity;
import com.cloud_technological.aura_pos.mappers.NotaContableMapper;
import com.cloud_technological.aura_pos.repositories.facturacion.FacturaJPARepository;
import com.cloud_technological.aura_pos.repositories.notas_contables.NotaContableJPARepository;
import com.cloud_technological.aura_pos.repositories.users.UsuarioJPARepository;
import com.cloud_technological.aura_pos.services.implementations.NotaContableServiceImpl;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.NotaContableTipo;

/**
 * Tests para HU-005: Notas Contables (Crédito/Débito)
 */
@ExtendWith(MockitoExtension.class)
class NotaContableServiceTest {

    @Mock
    private NotaContableJPARepository notaContableRepository;

    @Mock
    private FacturaJPARepository facturaRepository;

    @Mock
    private UsuarioJPARepository usuarioRepository;

    @Mock
    private NotaContableMapper notaContableMapper;

    @InjectMocks
    private NotaContableServiceImpl notaContableService;

    private FacturaEntity facturaMock;
    private UsuarioEntity usuarioMock;
    private EmpresaEntity empresaMock;

    @BeforeEach
    void setUp() {
        empresaMock = new EmpresaEntity();
        empresaMock.setId(1);
        empresaMock.setNombreComercial("Empresa Test");

        usuarioMock = new UsuarioEntity();
        usuarioMock.setId(1);
        usuarioMock.setUsername("testuser");

        facturaMock = new FacturaEntity();
        facturaMock.setId(1L);
        facturaMock.setEmpresa(empresaMock);
        facturaMock.setPrefijo("FV");
        facturaMock.setConsecutivo(1L);
    }

    /**
     * Escenario: Crear nota contable de tipo CRÉDITO exitosamente
     * Dado: Una factura válida y datos correctos
     * Cuando: Se crea una nota crédito
     * Entonces: La nota se crea sin errores
     */
    @Test
    void testCrearNotaContableCredito_Exitoso() {
        // Arrange
        NotaContableDto dto = new NotaContableDto();
        dto.setFacturaId(1L);
        dto.setValor(new BigDecimal("50000"));
        dto.setTipo(NotaContableTipo.CREDITO);
        dto.setNota("Devolución por producto en mal estado");
        dto.setMetodoPago("efectivo");

        when(facturaRepository.findByIdAndEmpresaId(1L, 1)).thenReturn(Optional.of(facturaMock));
        when(usuarioRepository.findById(1)).thenReturn(Optional.of(usuarioMock));
        
        NotaContableEntity entityGuardada = new NotaContableEntity();
        entityGuardada.setId(1L);
        entityGuardada.setFactura(facturaMock);
        entityGuardada.setUsuario(usuarioMock);
        entityGuardada.setValor(new BigDecimal("50000"));
        entityGuardada.setTipo(NotaContableTipo.CREDITO);
        
        when(notaContableRepository.save(any(NotaContableEntity.class))).thenReturn(entityGuardada);
        
        NotaContableDto dtoGuardado = new NotaContableDto();
        dtoGuardado.setId(1L);
        dtoGuardado.setFacturaId(1L);
        dtoGuardado.setValor(new BigDecimal("50000"));
        dtoGuardado.setTipo(NotaContableTipo.CREDITO);
        when(notaContableMapper.toDto(entityGuardada)).thenReturn(dtoGuardado);

        // Act
        NotaContableDto resultado = notaContableService.crear(dto, 1, 1);

        // Assert
        assertNotNull(resultado);
        assertEquals(1L, resultado.getId());
        assertEquals(NotaContableTipo.CREDITO, resultado.getTipo());
        verify(notaContableRepository).save(any(NotaContableEntity.class));
    }

    /**
     * Escenario: Crear nota contable de tipo DÉBITO exitosamente
     * Dado: Una factura válida y datos correctos
     * Cuando: Se crea una nota débito
     * Entonces: La nota se crea sin errores
     */
    @Test
    void testCrearNotaContableDebito_Exitoso() {
        // Arrange
        NotaContableDto dto = new NotaContableDto();
        dto.setFacturaId(1L);
        dto.setValor(new BigDecimal("30000"));
        dto.setTipo(NotaContableTipo.DEBITO);
        dto.setNota("Cargo adicional por envío");
        dto.setMetodoPago("transferencia");

        when(facturaRepository.findByIdAndEmpresaId(1L, 1)).thenReturn(Optional.of(facturaMock));
        when(usuarioRepository.findById(1)).thenReturn(Optional.of(usuarioMock));
        
        NotaContableEntity entityGuardada = new NotaContableEntity();
        entityGuardada.setId(1L);
        entityGuardada.setFactura(facturaMock);
        entityGuardada.setUsuario(usuarioMock);
        entityGuardada.setValor(new BigDecimal("30000"));
        entityGuardada.setTipo(NotaContableTipo.DEBITO);
        
        when(notaContableRepository.save(any(NotaContableEntity.class))).thenReturn(entityGuardada);
        
        NotaContableDto dtoGuardado = new NotaContableDto();
        dtoGuardado.setId(1L);
        dtoGuardado.setFacturaId(1L);
        dtoGuardado.setValor(new BigDecimal("30000"));
        dtoGuardado.setTipo(NotaContableTipo.DEBITO);
        when(notaContableMapper.toDto(entityGuardada)).thenReturn(dtoGuardado);

        // Act
        NotaContableDto resultado = notaContableService.crear(dto, 1, 1);

        // Assert
        assertNotNull(resultado);
        assertEquals(1L, resultado.getId());
        assertEquals(NotaContableTipo.DEBITO, resultado.getTipo());
    }

    /**
     * Escenario: Error al crear nota contable con tipo inválido
     * Dado: Una nota con tipo inválido (ni 1 ni 2)
     * Cuando: Se intenta crear la nota
     * Entonces: Lanza excepción con error 400
     */
    @Test
    void testCrearNotaContable_TipoInvalido_LanzaExcepcion() {
        // Arrange
        NotaContableDto dto = new NotaContableDto();
        dto.setFacturaId(1L);
        dto.setValor(new BigDecimal("50000"));
        dto.setTipo(99); // Tipo inválido
        dto.setNota("Nota inválida");
        dto.setMetodoPago("efectivo");

        // Act & Assert
        GlobalException exception = assertThrows(GlobalException.class, 
            () -> notaContableService.crear(dto, 1, 1));
        
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertTrue(exception.getMessage().contains("Tipo de nota inválido"));
    }

    /**
     * Escenario: Error al crear nota contable con valor menor o igual a cero
     * Dado: Una nota con valor inválido
     * Cuando: Se intenta crear la nota
     * Entonces: Lanza excepción con error 400
     */
    @Test
    void testCrearNotaContable_ValorInvalido_LanzaExcepcion() {
        // Arrange
        NotaContableDto dto = new NotaContableDto();
        dto.setFacturaId(1L);
        dto.setValor(BigDecimal.ZERO);
        dto.setTipo(NotaContableTipo.CREDITO);
        dto.setNota("Nota inválida");
        dto.setMetodoPago("efectivo");

        // Act & Assert
        GlobalException exception = assertThrows(GlobalException.class, 
            () -> notaContableService.crear(dto, 1, 1));
        
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertTrue(exception.getMessage().contains("valor"));
    }

    /**
     * Escenario: Error al crear nota contable con factura inexistente
     * Dado: Una factura que no existe
     * Cuando: Se intenta crear la nota
     * Entonces: Lanza excepción con error 404
     */
    @Test
    void testCrearNotaContable_FacturaNoExiste_LanzaExcepcion() {
        // Arrange
        NotaContableDto dto = new NotaContableDto();
        dto.setFacturaId(999L);
        dto.setValor(new BigDecimal("50000"));
        dto.setTipo(NotaContableTipo.CREDITO);
        dto.setNota("Nota");
        dto.setMetodoPago("efectivo");

        when(facturaRepository.findByIdAndEmpresaId(999L, 1)).thenReturn(Optional.empty());

        // Act & Assert
        GlobalException exception = assertThrows(GlobalException.class, 
            () -> notaContableService.crear(dto, 1, 1));
        
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        assertTrue(exception.getMessage().contains("Factura no encontrada"));
    }

    /**
     * Escenario: Obtener notas contables por factura
     * Dado: Una factura con notas asociadas
     * Cuando: Se consultan las notas
     * Entonces: Retorna todas las notas ordenadas por fecha descendente
     */
    @Test
    void testObtenerPorFactura_Exitoso() {
        // Arrange
        NotaContableEntity nota1 = new NotaContableEntity();
        nota1.setId(1L);
        nota1.setTipo(NotaContableTipo.CREDITO);
        nota1.setCreatedAt(LocalDateTime.now().plusHours(1));

        NotaContableEntity nota2 = new NotaContableEntity();
        nota2.setId(2L);
        nota2.setTipo(NotaContableTipo.DEBITO);
        nota2.setCreatedAt(LocalDateTime.now());

        when(facturaRepository.findByIdAndEmpresaId(1L, 1)).thenReturn(Optional.of(facturaMock));
        when(notaContableRepository.findByFacturaIdOrderByCreatedAtDesc(1L))
            .thenReturn(List.of(nota1, nota2));

        NotaContableDto dto1 = new NotaContableDto();
        dto1.setId(1L);
        dto1.setTipo(NotaContableTipo.CREDITO);
        
        NotaContableDto dto2 = new NotaContableDto();
        dto2.setId(2L);
        dto2.setTipo(NotaContableTipo.DEBITO);

        when(notaContableMapper.toDto(nota1)).thenReturn(dto1);
        when(notaContableMapper.toDto(nota2)).thenReturn(dto2);

        // Act
        var resultado = notaContableService.obtenerPorFactura(1L, 1);

        // Assert
        assertNotNull(resultado);
        assertEquals(2, resultado.size());
    }
}
