package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.entity.FacturaVentaDetalleEntity;
import com.cloud_technological.aura_pos.entity.FacturaVentaEntity;
import com.cloud_technological.aura_pos.entity.ProductoEntity;
import com.cloud_technological.aura_pos.repositories.contabilidad.RecargoFormaPagoQueryRepository;
import com.cloud_technological.aura_pos.repositories.empresas.EmpresaJPARepository;
import com.cloud_technological.aura_pos.repositories.productos.ProductoJPARepository;
import com.cloud_technological.aura_pos.repositories.unidad_medida.UnidadMedidaJPARepository;
import com.cloud_technological.aura_pos.utils.GlobalException;

import lombok.RequiredArgsConstructor;

/**
 * Factura AIU (construcción). Las líneas de la obra son el costo directo, sin
 * IVA; sobre ese costo van Administración, Imprevistos y Utilidad como
 * porcentajes, y el IVA se liquida solo sobre la Utilidad.
 *
 * <p>Las tres se agregan como líneas de servicio (productos AIU-…, ocultos en
 * el POS, creados la primera vez): así pasan sin cambios por la venta, el
 * asiento, la factura electrónica de Factus y el PDF.
 */
@Service
@RequiredArgsConstructor
public class FacturaAiuService {

    public static final String ADMINISTRACION = "ADMINISTRACION";
    public static final String IMPREVISTOS = "IMPREVISTOS";
    public static final String UTILIDAD = "UTILIDAD";

    private final RecargoFormaPagoQueryRepository productosQuery;
    private final ProductoJPARepository productoRepo;
    private final EmpresaJPARepository empresaRepo;
    private final UnidadMedidaJPARepository unidadRepo;

    /** Una línea AIU calculada: tipo, porcentaje, base (valor) e IVA. */
    record LineaAiu(String tipo, BigDecimal porcentaje, BigDecimal valor, BigDecimal ivaPct, BigDecimal iva) {
    }

    /** Las tres líneas sobre el costo directo. Las que dan cero no se agregan. */
    static List<LineaAiu> calcular(BigDecimal costoDirecto, BigDecimal admPct, BigDecimal impPct, BigDecimal utiPct,
            BigDecimal ivaPct) {
        List<LineaAiu> r = new ArrayList<>();
        Object[][] filas = { { ADMINISTRACION, admPct, false }, { IMPREVISTOS, impPct, false }, { UTILIDAD, utiPct, true } };
        for (Object[] fila : filas) {
            BigDecimal pct = nz((BigDecimal) fila[1]);
            BigDecimal valor = costoDirecto.multiply(pct).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            if (valor.signum() <= 0) continue;
            boolean conIva = (Boolean) fila[2];
            BigDecimal tarifa = conIva ? nz(ivaPct) : BigDecimal.ZERO;
            BigDecimal iva = valor.multiply(tarifa).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            r.add(new LineaAiu((String) fila[0], pct, valor, tarifa, iva));
        }
        return r;
    }

    /** Valida los porcentajes de la factura. */
    void validar(FacturaVentaEntity f) {
        for (BigDecimal p : new BigDecimal[] { f.getAiuAdministracionPct(), f.getAiuImprevistosPct(),
                f.getAiuUtilidadPct(), f.getAiuIvaPct() }) {
            if (p == null || p.signum() < 0 || p.compareTo(BigDecimal.valueOf(100)) > 0)
                throw new GlobalException(HttpStatus.BAD_REQUEST, "Los porcentajes del AIU deben estar entre 0 y 100");
        }
        if (f.getAiuUtilidadPct().signum() <= 0)
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "En una factura AIU la Utilidad debe ser mayor a cero: sobre ella se liquida el IVA");
    }

    /** Las líneas de detalle de A, I y U para la factura, numeradas después de la obra. */
    List<FacturaVentaDetalleEntity> lineas(FacturaVentaEntity f, BigDecimal costoDirecto, int ordenDesde) {
        List<FacturaVentaDetalleEntity> r = new ArrayList<>();
        int orden = ordenDesde;
        for (LineaAiu l : calcular(costoDirecto, f.getAiuAdministracionPct(), f.getAiuImprevistosPct(),
                f.getAiuUtilidadPct(), f.getAiuIvaPct())) {
            r.add(FacturaVentaDetalleEntity.builder()
                    .productoId(producto(f.getEmpresaId(), l.tipo()))
                    .descripcion(nombre(l.tipo()) + " " + l.porcentaje().stripTrailingZeros().toPlainString() + "% (AIU)")
                    .cantidad(BigDecimal.ONE)
                    .precioUnitario(l.valor())
                    .descuentoValor(BigDecimal.ZERO)
                    .impuestoPorcentaje(l.ivaPct())
                    .impuestoValor(l.iva())
                    .subtotalLinea(l.valor().add(l.iva()))
                    .orden(++orden)
                    .aiuTipo(l.tipo())
                    .build());
        }
        return r;
    }

    static String nombre(String tipo) {
        return switch (tipo) {
            case ADMINISTRACION -> "Administración";
            case IMPREVISTOS -> "Imprevistos";
            default -> "Utilidad";
        };
    }

    /** El servicio de cada componente del AIU; se crea la primera vez, oculto en el POS. */
    private Long producto(Integer empresaId, String tipo) {
        String sku = "AIU-" + tipo;
        Long id = productosQuery.productoRecargoId(empresaId, sku);
        if (id != null) return id;
        Long unidadId = productosQuery.unidadServicioId();
        if (unidadId == null)
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "No hay unidades de medida activas para crear los servicios del AIU");
        ProductoEntity p = new ProductoEntity();
        p.setEmpresa(empresaRepo.findById(empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.INTERNAL_SERVER_ERROR, "Empresa no encontrada")));
        p.setUnidadMedidaBase(unidadRepo.findById(unidadId).orElseThrow());
        p.setSku(sku);
        p.setNombre(nombre(tipo) + " (AIU)");
        p.setDescripcion("Lo agrega la factura AIU (construcción) sobre el costo directo de la obra.");
        p.setTipoProducto("SERVICIO");
        p.setUsoProducto("VENTA");
        p.setClasificacion("SERVICIO");
        p.setManejaInventario(false);
        p.setManejaLotes(false);
        p.setManejaSerial(false);
        p.setPermitirStockNegativo(true);
        p.setCosto(BigDecimal.ZERO);
        p.setPrecio(BigDecimal.ZERO);
        p.setIvaPorcentaje(UTILIDAD.equals(tipo) ? BigDecimal.valueOf(19) : BigDecimal.ZERO);
        p.setIvaIncluido(false);
        p.setActivo(true);
        p.setVisibleEnPos(false);
        p.setVendePorUnidad(true);
        p.setCreatedAt(LocalDateTime.now());
        return productoRepo.save(p).getId();
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
