package com.cloud_technological.aura_pos.dto.factus;

import java.math.BigDecimal;
import java.util.List;

import lombok.Builder;
import lombok.Data;


@Data
@Builder
public class FacturaElectronicaRequest {
    private String numeroVenta;          // "VTA-001"
    private String observacion;
    private String metodoPago;           // "10"=Efectivo, "42"=Transferencia

    // "YYYY-MM-DD" — mismo día para ventas POS
    private String fechaVencimiento;

    // Datos del cliente comprador
    private String clienteDocumento;
    private String clienteDv;            // Dígito verificador (solo NIT)
    private String clienteNombre;
    private String clienteEmail;
    private String clienteTelefono;
    private String clienteDireccion;
    private Integer clienteTipoDocumentoFactusId; // 3=Cédula, 6=NIT
    private Integer clienteMunicipioId;           // ID municipio en Factus

    private List<ItemFacturaRequest> items;

    @Data
    @Builder
    public static class ItemFacturaRequest {
        private String sku;
        private String nombre;
        private BigDecimal cantidad;
        private BigDecimal precioSinIva;  // precio unitario SIN IVA
        private String ivaPorcentaje;     // "19.00", "5.00", "0.00"
    }
}
