package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.cloud_technological.aura_pos.dto.contabilidad.AsientoContableTableDto;
import com.cloud_technological.aura_pos.entity.AsientoContableEntity;
import com.cloud_technological.aura_pos.entity.AsientoDetalleEntity;
import com.cloud_technological.aura_pos.entity.CompraEntity;
import com.cloud_technological.aura_pos.entity.PlanCuentaEntity;
import com.cloud_technological.aura_pos.entity.VentaEntity;
import com.cloud_technological.aura_pos.repositories.contabilidad.AsientoContableJPARepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.AsientoContableQueryRepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.PlanCuentaJPARepository;
import com.cloud_technological.aura_pos.repositories.compras.CompraJPARepository;
import com.cloud_technological.aura_pos.repositories.ventas.VentaJPARepository;
import com.cloud_technological.aura_pos.services.ContabilidadAutoService;

@Service
public class ContabilidadAutoServiceImpl implements ContabilidadAutoService {

    @Autowired private VentaJPARepository ventaRepo;
    @Autowired private CompraJPARepository compraRepo;
    @Autowired private PlanCuentaJPARepository planRepo;
    @Autowired private AsientoContableJPARepository asientoRepo;
    @Autowired private AsientoContableQueryRepository queryRepo;

    // ────────────────────────────────────────────────────────────────────────
    //  Prefijos de comprobante (Regla 1)
    //  VT = Venta  |  CO = Compra  |  CD = Comprobante de Diario (manual)
    // ────────────────────────────────────────────────────────────────────────
    private static final String PREFIX_VENTA  = "VT";
    private static final String PREFIX_COMPRA = "CO";

    // Códigos PUC estándar usados para la generación automática
    private static final String COD_CLIENTES        = "1305";
    private static final String COD_IVA_PAGAR       = "2408";
    private static final String COD_INGRESOS_VENTAS = "4135";
    private static final String COD_COSTO_VENTAS    = "6135";
    private static final String COD_INVENTARIO      = "1435";
    private static final String COD_PROVEEDORES     = "2205";

    @Override
    @Transactional
    public AsientoContableTableDto generarDesdeVenta(Long ventaId, Integer empresaId,
            Integer usuarioId) {
        // Idempotencia: no duplicar si ya existe
        if (asientoRepo.existsByTipoOrigenAndOrigenIdAndEmpresaId("VENTA", ventaId, empresaId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un asiento contable para la venta #" + ventaId);
        }

        VentaEntity venta = ventaRepo.findByIdAndEmpresaId(ventaId, empresaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Venta no encontrada"));

        BigDecimal totalVenta    = venta.getTotalPagar() != null ? venta.getTotalPagar() : BigDecimal.ZERO;
        BigDecimal impuestos     = venta.getImpuestosTotal() != null ? venta.getImpuestosTotal() : BigDecimal.ZERO;
        BigDecimal subtotal      = venta.getSubtotal() != null ? venta.getSubtotal() : BigDecimal.ZERO;

        List<AsientoDetalleEntity> detalles = new ArrayList<>();

        // DB: Clientes (1305) = totalVenta
        cuentaPorCodigo(empresaId, COD_CLIENTES).ifPresent(c ->
            detalles.add(linea(c.getId(), "Venta factura", totalVenta, BigDecimal.ZERO)));

        // CR: Ingresos por ventas (4135) = subtotal
        cuentaPorCodigo(empresaId, COD_INGRESOS_VENTAS).ifPresent(c ->
            detalles.add(linea(c.getId(), "Ingresos venta", BigDecimal.ZERO, subtotal)));

        // CR: IVA por pagar (2408) = impuestosTotal  (solo si > 0)
        if (impuestos.compareTo(BigDecimal.ZERO) > 0) {
            cuentaPorCodigo(empresaId, COD_IVA_PAGAR).ifPresent(c ->
                detalles.add(linea(c.getId(), "IVA generado", BigDecimal.ZERO, impuestos)));
        }

        if (detalles.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "No se encontraron las cuentas contables necesarias en el plan de cuentas. "
                  + "Verifique que existan las cuentas: " + COD_CLIENTES + ", "
                  + COD_INGRESOS_VENTAS + ", " + COD_IVA_PAGAR);
        }

        String comprobante = queryRepo.siguienteNumeroComprobante(empresaId, PREFIX_VENTA);
        AsientoContableEntity asiento = buildAsiento(empresaId, usuarioId,
                venta.getFechaEmision().toLocalDate(), comprobante,
                "Venta #" + ventaId + (venta.getConsecutivo() != null
                        ? " — " + venta.getPrefijo() + "-" + venta.getConsecutivo() : ""),
                "VENTA", ventaId, detalles);
        detalles.forEach(d -> d.setAsiento(asiento));

        AsientoContableEntity saved = asientoRepo.save(asiento);
        AsientoContableTableDto result = toDto(saved);
        result.setDetalles(queryRepo.obtenerDetalles(saved.getId()));
        return result;
    }

    @Override
    @Transactional
    public AsientoContableTableDto generarDesdeCompra(Long compraId, Integer empresaId,
            Integer usuarioId) {
        if (asientoRepo.existsByTipoOrigenAndOrigenIdAndEmpresaId("COMPRA", compraId, empresaId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un asiento contable para la compra #" + compraId);
        }

        CompraEntity compra = compraRepo.findByIdAndEmpresaId(compraId, empresaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Compra no encontrada"));

        BigDecimal netaAPagar = compra.getNetaAPagar() != null
                ? compra.getNetaAPagar() : compra.getTotal();
        BigDecimal subtotal   = compra.getSubtotal() != null ? compra.getSubtotal() : BigDecimal.ZERO;
        BigDecimal impuestos  = compra.getImpuestosTotal() != null ? compra.getImpuestosTotal() : BigDecimal.ZERO;

        List<AsientoDetalleEntity> detalles = new ArrayList<>();

        // DB: Inventario (1435) = subtotal
        cuentaPorCodigo(empresaId, COD_INVENTARIO).ifPresent(c ->
            detalles.add(linea(c.getId(), "Inventario compra", subtotal, BigDecimal.ZERO)));

        // DB: IVA descontable — reutilizamos 2408 en el lado débito (IVA pagado)
        if (impuestos.compareTo(BigDecimal.ZERO) > 0) {
            cuentaPorCodigo(empresaId, COD_IVA_PAGAR).ifPresent(c ->
                detalles.add(linea(c.getId(), "IVA compra (descontable)", impuestos, BigDecimal.ZERO)));
        }

        // CR: Proveedores (2205) = netaAPagar
        cuentaPorCodigo(empresaId, COD_PROVEEDORES).ifPresent(c ->
            detalles.add(linea(c.getId(), "Cuentas por pagar proveedor", BigDecimal.ZERO, netaAPagar)));

        if (detalles.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "No se encontraron las cuentas contables necesarias. "
                  + "Verifique: " + COD_INVENTARIO + ", " + COD_PROVEEDORES);
        }

        String comprobante = queryRepo.siguienteNumeroComprobante(empresaId, PREFIX_COMPRA);
        AsientoContableEntity asiento = buildAsiento(empresaId, usuarioId,
                compra.getFecha().toLocalDate(), comprobante,
                "Compra #" + compraId + (compra.getNumeroCompra() != null
                        ? " — " + compra.getNumeroCompra() : ""),
                "COMPRA", compraId, detalles);
        detalles.forEach(d -> d.setAsiento(asiento));

        AsientoContableEntity saved = asientoRepo.save(asiento);
        AsientoContableTableDto result = toDto(saved);
        result.setDetalles(queryRepo.obtenerDetalles(saved.getId()));
        return result;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Optional<PlanCuentaEntity> cuentaPorCodigo(Integer empresaId, String codigo) {
        return planRepo.findByEmpresaIdOrderByCodigoAsc(empresaId).stream()
                .filter(c -> c.getCodigo().equals(codigo) && Boolean.TRUE.equals(c.getActiva()))
                .findFirst();
    }

    private AsientoDetalleEntity linea(Long cuentaId, String desc,
            BigDecimal debito, BigDecimal credito) {
        return AsientoDetalleEntity.builder()
                .cuentaId(cuentaId)
                .descripcion(desc)
                .debito(debito)
                .credito(credito)
                .build();
    }

    private AsientoContableEntity buildAsiento(Integer empresaId, Integer usuarioId,
            java.time.LocalDate fecha, String comprobante, String descripcion,
            String tipoOrigen, Long origenId, List<AsientoDetalleEntity> detalles) {

        BigDecimal totalDb = detalles.stream().map(AsientoDetalleEntity::getDebito)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCr = detalles.stream().map(AsientoDetalleEntity::getCredito)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return AsientoContableEntity.builder()
                .empresaId(empresaId)
                .fecha(fecha)
                .descripcion(descripcion)
                .tipoOrigen(tipoOrigen)
                .origenId(origenId)
                .numeroComprobante(comprobante)
                .totalDebito(totalDb)
                .totalCredito(totalCr)
                .estado("CONTABILIZADO")
                .usuarioId(usuarioId)
                .detalles(detalles)
                .build();
    }

    private AsientoContableTableDto toDto(AsientoContableEntity e) {
        AsientoContableTableDto dto = new AsientoContableTableDto();
        dto.setId(e.getId());
        dto.setNumeroComprobante(e.getNumeroComprobante());
        dto.setFecha(e.getFecha() != null ? e.getFecha().toString() : null);
        dto.setDescripcion(e.getDescripcion());
        dto.setTipoOrigen(e.getTipoOrigen());
        dto.setOrigenId(e.getOrigenId());
        dto.setTotalDebito(e.getTotalDebito());
        dto.setTotalCredito(e.getTotalCredito());
        dto.setEstado(e.getEstado());
        dto.setCreatedAt(e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
        return dto;
    }
}
