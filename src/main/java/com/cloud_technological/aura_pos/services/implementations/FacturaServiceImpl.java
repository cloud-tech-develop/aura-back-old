package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.facturacion.FacturaDto;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.FacturaEntity;
import com.cloud_technological.aura_pos.entity.ReciboPagoEntity;
import com.cloud_technological.aura_pos.entity.TerceroEntity;
import com.cloud_technological.aura_pos.entity.UsuarioEntity;
import com.cloud_technological.aura_pos.entity.VentaEntity;
import com.cloud_technological.aura_pos.entity.VentaPagoEntity;
import com.cloud_technological.aura_pos.event.FacturaLogEvent;
import com.cloud_technological.aura_pos.mappers.FacturaMapper;
import com.cloud_technological.aura_pos.repositories.empresas.EmpresaJPARepository;
import com.cloud_technological.aura_pos.repositories.facturacion.FacturaJPARepository;
import com.cloud_technological.aura_pos.repositories.facturacion.FacturaQueryRepository;
import com.cloud_technological.aura_pos.repositories.facturacion.ReciboPagoJPARepository;
import com.cloud_technological.aura_pos.repositories.terceros.TerceroJPARepository;
import com.cloud_technological.aura_pos.repositories.users.UsuarioJPARepository;
import com.cloud_technological.aura_pos.repositories.ventas.VentaJPARepository;
import com.cloud_technological.aura_pos.repositories.venta_pago.VentaPagoJPARepository;
import com.cloud_technological.aura_pos.services.FacturaService;
import com.cloud_technological.aura_pos.utils.FacturaLogEvento;
import com.cloud_technological.aura_pos.utils.GlobalException;

@Service
public class FacturaServiceImpl implements FacturaService {

    private final FacturaJPARepository facturaJPARepository;
    private final FacturaQueryRepository facturaQueryRepository;
    private final ReciboPagoJPARepository reciboPagoJPARepository;
    private final VentaJPARepository ventaJPARepository;
    private final VentaPagoJPARepository ventaPagoJPARepository;
    private final EmpresaJPARepository empresaJPARepository;
    private final UsuarioJPARepository usuarioJPARepository;
    private final FacturaMapper facturaMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${app.facturacion.clave-tecnica:default-clave-tecnica}")
    private String claveTecnica;

    @Value("${app.facturacion.prefijo-default:FV}")
    private String prefijoDefault;

    @Value("${app.facturacion.tipo-ambiente:dev}")
    private String tipoAmbiente;

    @Autowired
    public FacturaServiceImpl(FacturaJPARepository facturaJPARepository,
            FacturaQueryRepository facturaQueryRepository,
            ReciboPagoJPARepository reciboPagoJPARepository,
            VentaJPARepository ventaJPARepository,
            VentaPagoJPARepository ventaPagoJPARepository,
            EmpresaJPARepository empresaJPARepository,
            UsuarioJPARepository usuarioJPARepository,
            FacturaMapper facturaMapper,
            ApplicationEventPublisher eventPublisher) {
        this.facturaJPARepository = facturaJPARepository;
        this.facturaQueryRepository = facturaQueryRepository;
        this.reciboPagoJPARepository = reciboPagoJPARepository;
        this.ventaJPARepository = ventaJPARepository;
        this.ventaPagoJPARepository = ventaPagoJPARepository;
        this.empresaJPARepository = empresaJPARepository;
        this.usuarioJPARepository = usuarioJPARepository;
        this.facturaMapper = facturaMapper;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public FacturaDto crearDesdeVenta(Long ventaId, Integer empresaId, Integer usuarioId) {
        // Validar empresa
        EmpresaEntity empresa = empresaJPARepository.findById(empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Empresa no encontrada"));

        // Validar usuario
        UsuarioEntity usuario = usuarioJPARepository.findById(usuarioId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));

        // Validar venta
        VentaEntity venta = ventaJPARepository.findById(ventaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Venta no encontrada"));

        // Verificar si ya existe factura para esta venta
        if (facturaJPARepository.findByVentaId(ventaId).isPresent()) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Ya existe una factura para esta venta");
        }

        /**
         * TODO: posible error al tratar de obtener el siguiente consecutivo
         * * porque varias instancias de la aplicacion podrian estar usando el mismo
         * * consecutivo
         */
        // Generar número de factura
        String prefijo = prefijoDefault;
        Long consecutivo = facturaQueryRepository.obtenerSiguienteConsecutivo(empresaId);
        
        // Generar CUFE
        LocalDateTime fechaEmision = LocalDateTime.now();
        String cufe = generarCufe(prefijo, consecutivo, fechaEmision, empresa, venta);

        // Crear factura
        FacturaEntity factura = new FacturaEntity();
        factura.setPrefijo(prefijo);
        factura.setConsecutivo(consecutivo);
        factura.setValor(venta.getTotalPagar());
        factura.setDescuento(venta.getDescuentoTotal() != null ? venta.getDescuentoTotal() : BigDecimal.ZERO);
        factura.setDescripcion("Factura generada automáticamente desde venta #" + venta.getId());
        factura.setCufe(cufe);
        factura.setFechaHoraEmision(fechaEmision);
        factura.setEstadoDian("PENDIENTE");
        factura.setTipoAmbiente(tipoAmbiente);
        factura.setEmpresa(empresa);
        factura.setUsuario(usuario);
        factura.setVenta(venta);
        factura.setCreatedAt(LocalDateTime.now());

        // Determinar método de pago principal desde los pagos de la venta
        List<VentaPagoEntity> pagosVenta = ventaPagoJPARepository.findByVentaId(ventaId);
        if (!pagosVenta.isEmpty()) {
            // Usar el método de pago del primer pago
            factura.setMetodoPago(pagosVenta.get(0).getMetodoPago());
        } else {
            factura.setMetodoPago("efectivo");
        }

        factura = facturaJPARepository.save(factura);

        // Registrar evento de creación de factura (Post-Commit)
        java.util.Map<String, Object> retryPayload = new java.util.HashMap<>();
        retryPayload.put("action", "crearDesdeVenta");
        retryPayload.put("ventaId", ventaId);
        retryPayload.put("empresaId", empresaId);
        retryPayload.put("usuarioId", usuarioId);

        eventPublisher.publishEvent(new FacturaLogEvent(
            factura.getId(),
            FacturaLogEvento.CREACION,
            null,
            "PENDIENTE",
            buildFacturaData(factura),
            usuarioId,
            "Factura creada automáticamente desde venta #" + ventaId,
            retryPayload
        ));

        // Copiar pagos de VentaPago a ReciboPago
        for (VentaPagoEntity pagoVenta : pagosVenta) {
            ReciboPagoEntity reciboPago = new ReciboPagoEntity();
            reciboPago.setFactura(factura);
            reciboPago.setValor(pagoVenta.getMonto());
            reciboPago.setMetodoPago(pagoVenta.getMetodoPago());
            reciboPago.setDescripcion(pagoVenta.getReferencia());
            reciboPago.setUsuarioId(usuarioId);
            reciboPago.setCreatedAt(LocalDateTime.now());
            reciboPagoJPARepository.save(reciboPago);
        }

        return facturaMapper.toDto(factura);
    }

    /**
     * Genera el CUFE usando SHA-384
     * CUFE = SHA-384(numFactura + fechaEmision + nitEmisor + nitReceptor + valorTotal + ivaTotal + timestamp + claveTecnica)
     */
    private String generarCufe(String prefijo, Long consecutive, LocalDateTime fechaEmision, 
            EmpresaEntity empresa, VentaEntity venta) {
        try {
            String numFactura = prefijo + consecutive;
            String fecha = fechaEmision.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String nitEmisor = empresa.getNit() != null ? empresa.getNit() : "0";
            
            // Obtener nit del cliente
            String nitReceptor = "0";
            if (venta.getCliente() != null) {
                TerceroEntity cliente = venta.getCliente();
                if (cliente.getNumeroDocumento() != null && !cliente.getNumeroDocumento().isBlank()) {
                    nitReceptor = cliente.getNumeroDocumento();
                }
            }
            
            BigDecimal valorTotal = venta.getTotalPagar() != null ? venta.getTotalPagar() : BigDecimal.ZERO;
            BigDecimal ivaTotal = venta.getImpuestosTotal() != null ? venta.getImpuestosTotal() : BigDecimal.ZERO;
            String timestamp = String.valueOf(System.currentTimeMillis());
            
            String data = numFactura + fecha + nitEmisor + nitReceptor + 
                    valorTotal.toPlainString() + ivaTotal.toPlainString() + 
                    timestamp + claveTecnica;
            
            MessageDigest digest = MessageDigest.getInstance("SHA-384");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            
            return hexString.toString();
            
        } catch (NoSuchAlgorithmException e) {
            throw new GlobalException(HttpStatus.INTERNAL_SERVER_ERROR, "Error al generar CUFE");
        }
    }

    /**
     * Construye un mapa con los datos de la factura para el log
     */
    private java.util.Map<String, Object> buildFacturaData(FacturaEntity factura) {
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("id", factura.getId());
        data.put("prefijo", factura.getPrefijo());
        data.put("consecutivo", factura.getConsecutivo());
        data.put("valor", factura.getValor());
        data.put("descuento", factura.getDescuento());
        data.put("cufe", factura.getCufe());
        data.put("estadoDian", factura.getEstadoDian());
        data.put("metodoPago", factura.getMetodoPago());
        data.put("fechaHoraEmision", factura.getFechaHoraEmision());
        data.put("ventaId", factura.getVenta() != null ? factura.getVenta().getId() : null);
        data.put("empresaId", factura.getEmpresa() != null ? factura.getEmpresa().getId() : null);
        return data;
    }
}
