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

        // 3. Sin cliente => se factura como Consumidor Final (Factus lo permite)

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

    // ── Datos estándar DIAN para Consumidor Final ───────────────────
    private static final String CF_DOCUMENTO        = "222222222222";
    private static final String CF_NOMBRE           = "Consumidor Final";
    private static final Integer CF_TIPO_DOC_FACTUS = 3;   // 3 = Cédula de ciudadanía
    private static final Integer CF_MUNICIPIO_ID    = 511; // municipio por defecto solicitado

    private FacturaElectronicaRequest buildRequest(
            VentaEntity venta,
            EmpresaEntity empresa,
            List<VentaDetalleEntity> detalles) {

        TerceroEntity cliente = venta.getCliente();

        // Valores por defecto = Consumidor Final (cuando la venta no tiene cliente).
        // Dirección y municipio NO se queman: se toman de la empresa/sucursal en
        // ese momento. Municipio desde la empresa; dirección desde la sucursal de
        // la venta (con respaldos para no enviar vacío a Factus).
        String  clienteDocumento  = CF_DOCUMENTO;
        String  clienteDv         = null;
        String  nombreCliente     = CF_NOMBRE;
        String  emailFactura      = null;
        String  clienteTelefono   = empresa.getTelefono();
        Integer tipoDocId         = CF_TIPO_DOC_FACTUS;
        Integer clienteMunicipioId = empresa.getMunicipioId() != null
                ? empresa.getMunicipioId() : CF_MUNICIPIO_ID;
        String  clienteDireccion  = direccionConsumidorFinal(venta, empresa);

        if (cliente != null) {
            nombreCliente = (cliente.getRazonSocial() != null
                    && !cliente.getRazonSocial().isBlank())
                    ? cliente.getRazonSocial()
                    : (cliente.getNombres() + " " + cliente.getApellidos()).trim();

            emailFactura = (cliente.getEmailFe() != null
                    && !cliente.getEmailFe().isBlank())
                    ? cliente.getEmailFe() : cliente.getEmail();

            tipoDocId = TIPO_DOC_FACTUS.getOrDefault(
                    cliente.getTipoDocumento() != null
                            ? cliente.getTipoDocumento().toUpperCase() : "CC", 3);

            clienteDocumento  = cliente.getNumeroDocumento();
            clienteDv         = cliente.getDv();
            clienteTelefono   = cliente.getTelefono();
            clienteDireccion  = cliente.getDireccion();
            clienteMunicipioId = cliente.getMunicipioId() != null
                    ? cliente.getMunicipioId().intValue() : CF_MUNICIPIO_ID;
        }

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
                .clienteDocumento(clienteDocumento)
                .clienteDv(clienteDv)
                .clienteNombre(nombreCliente)
                .clienteEmail(emailFactura)
                .clienteTelefono(clienteTelefono)
                .clienteDireccion(clienteDireccion)
                .clienteTipoDocumentoFactusId(tipoDocId)
                .clienteMunicipioId(clienteMunicipioId)
                .items(items)
                .build();
    }


    // Dirección a usar para Consumidor Final: dirección de la sucursal de la venta;
    // si no hay, el municipio (nombre) de la empresa; último recurso "Sin dirección".
    private String direccionConsumidorFinal(VentaEntity venta, EmpresaEntity empresa) {
        if (venta.getSucursal() != null
                && venta.getSucursal().getDireccion() != null
                && !venta.getSucursal().getDireccion().isBlank())
            return venta.getSucursal().getDireccion();

        if (empresa.getMunicipio() != null && !empresa.getMunicipio().isBlank())
            return empresa.getMunicipio();

        return "Sin dirección";
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
