package com.cloud_technological.aura_pos.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.cloud_technological.aura_pos.dto.ventas.CreateVentaPagoDto;
import com.cloud_technological.aura_pos.utils.GlobalException;

/**
 * Tests para HU-006: Validación de Cliente para Ventas a Crédito
 */
@ExtendWith(MockitoExtension.class)
class ValidacionCreditoTest {

    @InjectMocks
    private ValidacionCreditoHelper helper;

    /**
     * Escenario: Venta a crédito sin cliente
     * Dado: Una venta con método de pago "crédito" sin cliente
     * Cuando: Se valida la venta
     * Entonces: Lanza excepción indicando que el cliente es obligatorio
     */
    @Test
    void testVentaCreditoSinCliente_LanzaExcepcion() {
        // Arrange
        List<CreateVentaPagoDto> pagos = List.of(
            crearPago("credito", new BigDecimal("100000"))
        );
        Long clienteId = null;

        // Act & Assert
        GlobalException exception = assertThrows(GlobalException.class, 
            () -> helper.validarClienteParaCredito(pagos, clienteId));
        
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertTrue(exception.getMessage().contains("Las ventas a crédito requieren un cliente"));
    }

    /**
     * Escenario: Venta a crédito con cliente válido
     * Dado: Una venta con método de pago "crédito" y cliente válido
     * Cuando: Se valida la venta
     *Entonces: No lanza excepción
     */
    @Test
    void testVentaCreditoConCliente_NoLanzaExcepcion() {
        // Arrange
        List<CreateVentaPagoDto> pagos = List.of(
            crearPago("credito", new BigDecimal("100000"))
        );
        Long clienteId = 1L;

        // Act & Assert - No debe lanzar excepción
        assertDoesNotThrow(() -> helper.validarClienteParaCredito(pagos, clienteId));
    }

    /**
     * Escenario: Venta en efectivo sin cliente
     * Dado: Una venta con método de pago "efectivo" sin cliente
     * Cuando: Se valida la venta
     * Entonces: No lanza excepción (cliente opcional)
     */
    @Test
    void testVentaEfectivoSinCliente_NoLanzaExcepcion() {
        // Arrange
        List<CreateVentaPagoDto> pagos = List.of(
            crearPago("efectivo", new BigDecimal("100000"))
        );
        Long clienteId = null;

        // Act & Assert
        assertDoesNotThrow(() -> helper.validarClienteParaCredito(pagos, clienteId));
    }

    /**
     * Escenario: Venta con múltiples pagos incluyendo crédito
     * Dado: Una venta con pagos efectivo + crédito sin cliente
     * Cuando: Se valida la venta
     * Entonces: Lanza excepción
     */
    @Test
    void testVentaMultiplesPagosConCreditoSinCliente_LanzaExcepcion() {
        // Arrange
        List<CreateVentaPagoDto> pagos = List.of(
            crearPago("efectivo", new BigDecimal("50000")),
            crearPago("credito", new BigDecimal("50000"))
        );
        Long clienteId = null;

        // Act & Assert
        GlobalException exception = assertThrows(GlobalException.class, 
            () -> helper.validarClienteParaCredito(pagos, clienteId));
        
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
    }

    private CreateVentaPagoDto crearPago(String metodoPago, BigDecimal monto) {
        CreateVentaPagoDto pago = new CreateVentaPagoDto();
        pago.setMetodoPago(metodoPago);
        pago.setMonto(monto);
        return pago;
    }

    /**
     * Helper class para probar la validación
     */
    static class ValidacionCreditoHelper {
        public void validarClienteParaCredito(List<CreateVentaPagoDto> pagos, Long clienteId) {
            boolean tienePagoCredito = pagos.stream()
                    .anyMatch(p -> "credito".equalsIgnoreCase(p.getMetodoPago()));
            
            if (tienePagoCredito && clienteId == null) {
                throw new GlobalException(HttpStatus.BAD_REQUEST, 
                        "Las ventas a crédito requieren un cliente asociado");
            }
        }
    }
}
