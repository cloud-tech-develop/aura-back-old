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

    @org.springframework.beans.factory.annotation.Autowired
    private com.cloud_technological.aura_pos.repositories.ventas.VentaQueryRepository ventaQueryRepository;

    /**
     * La venta queda DESCONOCIDO (y se confirma) cuando Factus no respondió
     * tras recibir la factura; por eso esa excepción no revierte la transacción.
     */
    @Transactional(dontRollbackOn = com.cloud_technological.aura_pos.utils.FacturaEstadoInciertoException.class)
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

        // 5.b Reserva: dos clics (o dos cajas) no pueden mandar la misma venta a
        //     la vez. Antes el "ya fue emitida" se leía sin bloqueo.
        if (ventaQueryRepository.reservarEnvioFe(ventaId, empresaId) == 0)
            throw new GlobalException(HttpStatus.CONFLICT,
                    "La factura de esta venta ya fue emitida o se está enviando en este momento");

        // 6. Llamar a Factus
        FacturaElectronicaRequest request = buildRequest(venta, empresa, detalles);
        FactusBillDto factura;
        try {
            factura = factusService.generarFactura(empresaId, request);
        } catch (com.cloud_technological.aura_pos.utils.FacturaEstadoInciertoException ex) {
            // La DIAN pudo haberla recibido: se deja marcada en vez de revertir,
            // para que no se reenvíe a ciegas.
            venta.setEstadoDian("DESCONOCIDO");
            ventaRepository.save(venta);
            throw ex;
        }

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
        String  clienteOrganizacion = "2";   // persona natural
        String  clienteTributo      = "21";  // no aplica

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

        if (cliente != null) {
            clienteOrganizacion = "JURIDICA".equalsIgnoreCase(cliente.getTipoPersona()) ? "1" : "2";
            clienteTributo = "RESPONSABLE_IVA".equalsIgnoreCase(cliente.getRegimen()) ? "18" : "21";
        }

        // El descuento general se aplicó sobre el total de la venta: se reparte
        // en las líneas por valor para que el total de la factura sea lo cobrado.
        BigDecimal sumaLineas = detalles.stream()
                .map(d -> d.getSubtotalLinea() != null ? d.getSubtotalLinea() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPagar = venta.getTotalPagar() != null ? venta.getTotalPagar() : sumaLineas;
        BigDecimal factorGeneral = sumaLineas.signum() > 0 && totalPagar.compareTo(sumaLineas) < 0
                ? totalPagar.divide(sumaLineas, 8, RoundingMode.HALF_UP) : BigDecimal.ONE;

        List<ItemFacturaRequest> items = detalles.stream()
                .filter(d -> d.getCantidad() != null && d.getCantidad().signum() > 0)
                .map(d -> {
                    // IVA de la LÍNEA vendida, no el que tiene hoy el producto.
                    BigDecimal ivaPct = com.cloud_technological.aura_pos.utils.FacturaElectronicaCalculo
                            .ivaPorcentajeLinea(d.getSubtotalLinea(), d.getImpuestoValor());
                    // Factus recibe el precio CON IVA incluido
                    // precioUnitario está sin IVA → reconstruir: precio × (1 + IVA%)
                    BigDecimal precioConIva = precioConIva(d.getPrecioUnitario(), ivaPct);
                    BigDecimal cobrado = (d.getSubtotalLinea() != null ? d.getSubtotalLinea() : BigDecimal.ZERO)
                            .multiply(factorGeneral);
                    BigDecimal descuentoPct = com.cloud_technological.aura_pos.utils.FacturaElectronicaCalculo
                            .tasaDescuento(precioConIva.multiply(d.getCantidad()), cobrado);
                    return ItemFacturaRequest.builder()
                            .sku(d.getProducto().getSku() != null
                                    ? d.getProducto().getSku() : "SIN-SKU")
                            .nombre(d.getDescripcion() != null && !d.getDescripcion().isBlank()
                                    ? d.getDescripcion().trim() : d.getProducto().getNombre())
                            .cantidad(d.getCantidad())
                            .precioSinIva(precioConIva)
                            .ivaPorcentaje(ivaPct.toPlainString())
                            .descuentoPct(descuentoPct)
                            .build();
                })
                .collect(Collectors.toList());

        // Forma y medio de pago de la venta real: antes siempre contado y
        // efectivo, aunque fuera a crédito o por transferencia.
        List<com.cloud_technological.aura_pos.repositories.ventas.VentaQueryRepository.PagoVenta> pagos =
                ventaQueryRepository.pagosDeVenta(venta.getId());
        boolean esCredito = pagos.stream().anyMatch(p -> "CREDITO".equalsIgnoreCase(p.metodoPago()));
        String medioPago = pagos.stream()
                .filter(p -> !"CREDITO".equalsIgnoreCase(p.metodoPago()))
                .findFirst()
                .map(p -> com.cloud_technological.aura_pos.utils.FacturaElectronicaCalculo.medioPagoDian(p.metodoPago()))
                .orElse("10");
        LocalDate vence = LocalDate.now();
        if (esCredito) {
            LocalDate v = ventaQueryRepository.vencimientoCredito(venta.getId());
            vence = v != null && v.isAfter(LocalDate.now()) ? v : LocalDate.now().plusDays(30);
        }

        return FacturaElectronicaRequest.builder()
                // Referencia única en la empresa: el consecutivo es por sucursal,
                // así que con dos sucursales la venta 123 de cada una chocaba y
                // Factus rechazaba la segunda como duplicada.
                .numeroVenta("V" + venta.getId())
                .observacion(venta.getObservaciones())
                .metodoPago(medioPago)
                // Factus deduce la forma de pago de la fecha: hoy = contado, otra = crédito.
                .fechaVencimiento(vence.toString())
                .clienteOrganizacionLegalId(clienteOrganizacion)
                .clienteTributoId(clienteTributo)
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
