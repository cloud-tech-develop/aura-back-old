package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.dto.factus.FacturaElectronicaRequest;
import com.cloud_technological.aura_pos.dto.factus.FacturaElectronicaRequest.ItemFacturaRequest;
import com.cloud_technological.aura_pos.dto.factus.FacturaElectronicaResponseDto;
import com.cloud_technological.aura_pos.dto.factus.FactusBillDto;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.TerceroEntity;
import com.cloud_technological.aura_pos.entity.VentaDetalleEntity;
import com.cloud_technological.aura_pos.entity.VentaEntity;
import com.cloud_technological.aura_pos.repositories.venta_detalle.VentaDetalleJPARepository;
import com.cloud_technological.aura_pos.repositories.ventas.VentaJPARepository;
import com.cloud_technological.aura_pos.utils.GlobalException;

import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;


@Slf4j
@Service
public class VentaFacturaService {
    private final VentaJPARepository        ventaRepository;
    private final VentaDetalleJPARepository ventaDetalleRepository; // ajusta a tu repo real
    private final FactusService             factusService;

    public VentaFacturaService(VentaJPARepository ventaRepository,
                               VentaDetalleJPARepository ventaDetalleRepository,
                               FactusService factusService) {
        this.ventaRepository = ventaRepository;
        this.ventaDetalleRepository = ventaDetalleRepository;
        this.factusService = factusService;
    }

 // tipoDocumento del sistema → ID tipo documento en Factus
    private static final Map<String, Integer> TIPO_DOC_FACTUS = Map.of(
        "CC",        3,
        "NIT",       6,
        "CE",        2,
        "TI",        7,
        "PASAPORTE", 13,
        "PEP",       21
    );

    @Transactional
    public FacturaElectronicaResponseDto generarFacturaElectronica(
            Long ventaId, Integer empresaId) {

        // 1. Obtener venta
        VentaEntity venta = ventaRepository.findByIdAndEmpresaId(ventaId, empresaId)
                .orElseThrow(() -> new GlobalException(
                        HttpStatus.NOT_FOUND, "Venta no encontrada"));

        // 2. Empresa tiene FE habilitada?
        EmpresaEntity empresa = venta.getEmpresa();
        if (!empresa.isFacturaElectronica())
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Esta empresa no tiene habilitada la facturación electrónica");

        // 3. Tiene cliente? (no consumidor final)
        if (venta.getCliente() == null)
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Las ventas a consumidor final no generan factura electrónica");

        // 4. Ya fue facturada?
        if ("EMITIDA".equals(venta.getEstadoDian()))
            throw new GlobalException(HttpStatus.CONFLICT,
                    "Esta venta ya tiene factura electrónica emitida: " + venta.getCufe());

        // 5. Cargar detalles
        List<VentaDetalleEntity> detalles =
                ventaDetalleRepository.findByVentaId(ventaId);
        if (detalles == null || detalles.isEmpty())
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "La venta no tiene productos para facturar");

        // 6. Llamar a Factus
        FacturaElectronicaRequest request = buildRequest(venta, empresa, detalles);
        FactusBillDto factura = factusService.generarFactura(empresaId, request);

        if (factura == null)
            throw new GlobalException(HttpStatus.SERVICE_UNAVAILABLE,
                    "El servicio de facturación electrónica no está disponible. " +
                    "Intenta nuevamente en unos minutos.");

        // 7. Persistir CUFE y QR
        venta.setCufe(factura.getCufe());
        venta.setQrData(factura.getQr());
        venta.setEstadoDian("EMITIDA");
        venta.setFactusUrl(factura.getPublicUrl());
        venta.setFactusNumero(factura.getNumber());
        ventaRepository.save(venta);

        log.info("[Factus] Factura {} | Venta {} | CUFE: {}",
                factura.getNumber(), ventaId, factura.getCufe());

        FacturaElectronicaResponseDto response = new FacturaElectronicaResponseDto();
        response.setVentaId(ventaId);
        response.setFacturaNumero(factura.getNumber());
        response.setCufe(factura.getCufe());
        response.setQr(factura.getQr());
        response.setPdfUrl(factura.getPublicUrl());
        response.setEstadoDian("EMITIDA");
        return response;
    }

    private FacturaElectronicaRequest buildRequest(
            VentaEntity venta,
            EmpresaEntity empresa,
            List<VentaDetalleEntity> detalles) {

        TerceroEntity cliente = venta.getCliente();

        String nombreCliente = (cliente.getRazonSocial() != null
                && !cliente.getRazonSocial().isBlank())
                ? cliente.getRazonSocial()
                : (cliente.getNombres() + " " + cliente.getApellidos()).trim();

        String emailFactura = (cliente.getEmailFe() != null
                && !cliente.getEmailFe().isBlank())
                ? cliente.getEmailFe() : cliente.getEmail();

        Integer tipoDocId = TIPO_DOC_FACTUS.getOrDefault(
                cliente.getTipoDocumento() != null
                        ? cliente.getTipoDocumento().toUpperCase() : "CC", 3);

        List<ItemFacturaRequest> items = detalles.stream()
                .map(d -> ItemFacturaRequest.builder()
                        .sku(d.getProducto().getSku() != null
                                ? d.getProducto().getSku() : "SIN-SKU")
                        .nombre(d.getProducto().getNombre())
                        .cantidad(d.getCantidad())
                        // Factus recibe el precio CON IVA incluido
                        // precioUnitario está sin IVA → reconstruir: precio × (1 + IVA%)
                        .precioSinIva(precioConIva(
                                d.getPrecioUnitario(),
                                d.getProducto().getIvaPorcentaje()))
                        .ivaPorcentaje(d.getProducto().getIvaPorcentaje()
                                .setScale(2).toPlainString())
                        .build())
                .collect(Collectors.toList());

        return FacturaElectronicaRequest.builder()
                .numeroVenta((empresa.getFactusPrefijo() != null ? empresa.getFactusPrefijo() : "POS") + "-" + venta.getConsecutivo())
                .observacion(venta.getObservaciones())
                .metodoPago("10") // Efectivo por defecto
                .fechaVencimiento(LocalDate.now().toString())
                .clienteDocumento(cliente.getNumeroDocumento())
                .clienteDv(cliente.getDv())
                .clienteNombre(nombreCliente)
                .clienteEmail(emailFactura)
                .clienteTelefono(cliente.getTelefono())
                .clienteDireccion(cliente.getDireccion())
                .clienteTipoDocumentoFactusId(tipoDocId)
                .clienteMunicipioId(cliente.getMunicipioId().intValue())
                .items(items)
                .build();
    }


    // precioUnitario viene SIN IVA → multiplicar para obtener precio CON IVA
    // Ej: 4621.85 × 1.19 = 5500 ← lo que Factus espera
    private BigDecimal precioConIva(BigDecimal precioSinIva, BigDecimal ivaPct) {
        if (ivaPct == null || ivaPct.compareTo(BigDecimal.ZERO) == 0)
            return precioSinIva.setScale(2, RoundingMode.HALF_UP);
        BigDecimal factor = BigDecimal.ONE.add(
                ivaPct.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
        return precioSinIva.multiply(factor).setScale(2, RoundingMode.HALF_UP);
    }
}
