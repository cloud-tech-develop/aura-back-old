package com.cloud_technological.aura_pos.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.contabilidad.application.resolucion.ResolucionCuentaProducto;
import com.cloud_technological.aura_pos.contabilidad.application.resolucion.ResolucionCuentaProducto.CompraProducto;
import com.cloud_technological.aura_pos.entity.ActivoFijoEntity;
import com.cloud_technological.aura_pos.entity.CompraDetalleEntity;
import com.cloud_technological.aura_pos.entity.CompraEntity;
import com.cloud_technological.aura_pos.entity.DiferidoEntity;
import com.cloud_technological.aura_pos.entity.ProductoEntity;
import com.cloud_technological.aura_pos.repositories.activos_fijos.ActivoFijoJPARepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.DiferidoJPARepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.DiferidoQueryRepository;
import com.cloud_technological.aura_pos.utils.ClasificacionItem;
import com.cloud_technological.aura_pos.utils.GlobalException;

import lombok.RequiredArgsConstructor;

/**
 * Lo que una compra crea según la clasificación de sus líneas (V185): una
 * ficha de activo por unidad para ACTIVO_FIJO/INTANGIBLE y un diferido por
 * línea para DIFERIDO. El asiento de la compra ya debitó la cuenta de cada
 * clasificación; aquí solo nace el registro que la va a depreciar o amortizar,
 * con el mismo valor que entró al mayor.
 */
@Service
@RequiredArgsConstructor
public class CompraClasificacionService {

    /** Vida útil cuando ni la categoría la trae: 5 años, la de muebles y equipo de oficina. */
    private static final int VIDA_UTIL_POR_DEFECTO = 60;
    /** Meses del diferido cuando la categoría no los trae: una póliza anual. */
    private static final int MESES_DIFERIDO_POR_DEFECTO = 12;
    /** Más fichas que esto en una línea es casi seguro un error de cantidad. */
    private static final int MAX_FICHAS_POR_LINEA = 200;

    private final ResolucionCuentaProducto resolucion;
    private final ActivoFijoJPARepository activoRepo;
    private final DiferidoJPARepository diferidoRepo;
    private final DiferidoQueryRepository diferidoQuery;

    /** Clasificación con que se registró la línea; las compras previas a V185 son PRODUCTO. */
    public static ClasificacionItem de(CompraDetalleEntity detalle) {
        return ClasificacionItem.de(detalle.getClasificacion());
    }

    /**
     * Una nota crédito sobre un activo o un diferido no tiene cómo deshacer la
     * ficha o las cuotas a medias: se anula la compra (si nada se ha movido) o
     * se da de baja el activo.
     */
    public void validarNotaCredito(ProductoEntity producto) {
        ClasificacionItem c = ClasificacionItem.de(producto.getClasificacion());
        if (c.creaActivo() || c.creaDiferido()) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    producto.getNombre() + " es " + c.name().replace('_', ' ').toLowerCase()
                            + ": no se acredita con nota crédito. Anule la compra si el activo o el"
                            + " diferido no se ha movido, o dé de baja el activo.");
        }
    }

    /**
     * Crea fichas y diferidos de las líneas de la compra.
     *
     * @param fletes        fletes de la compra: se reparten por el neto de cada línea,
     *                      igual que en el asiento y en el costo promedio
     * @param netoDocumento neto de todas las líneas (base del reparto)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void crearDesdeCompra(CompraEntity compra, List<CompraDetalleEntity> lineas,
            BigDecimal fletes, BigDecimal netoDocumento, Integer empresaId) {
        LocalDate fecha = compra.getFecha() != null ? compra.getFecha().toLocalDate() : LocalDate.now();
        Long proveedorId = compra.getProveedor() != null ? compra.getProveedor().getId() : null;

        for (CompraDetalleEntity linea : lineas) {
            ClasificacionItem clasificacion = de(linea);
            if (!clasificacion.creaActivo() && !clasificacion.creaDiferido()) {
                continue;
            }
            BigDecimal neto = nz(linea.getSubtotalLinea());
            BigDecimal flete = fletes.signum() != 0 && netoDocumento.signum() != 0
                    ? fletes.multiply(neto).divide(netoDocumento, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            BigDecimal valorLinea = neto.add(flete);
            if (valorLinea.signum() <= 0) {
                continue;
            }
            CompraProducto cuentas = resolucion.resolverCompra(linea.getProducto().getId(), empresaId);

            if (clasificacion.creaActivo()) {
                crearFichas(compra, linea, cuentas, valorLinea, fecha, proveedorId, empresaId);
            } else {
                diferidoRepo.save(DiferidoEntity.builder()
                        .empresaId(empresaId)
                        .origenTipo(DiferidoEntity.ORIGEN_COMPRA)
                        .origenId(compra.getId())
                        .compraDetalleId(linea.getId())
                        .productoId(linea.getProducto().getId())
                        .descripcion(recortar(linea.getProducto().getNombre(), 200))
                        .monto(valorLinea)
                        .meses(cuentas.mesesDiferido() != null ? cuentas.mesesDiferido() : MESES_DIFERIDO_POR_DEFECTO)
                        .fechaInicio(fecha)
                        .cuentaGastoId(cuentas.cuentaGastoId())
                        .cuentaDiferidoId(cuentas.cuentaCompraId())
                        .terceroId(proveedorId)
                        .centroCostoId(compra.getCentroCostoId())
                        .build());
            }
        }
    }

    private void crearFichas(CompraEntity compra, CompraDetalleEntity linea, CompraProducto cuentas,
            BigDecimal valorLinea, LocalDate fecha, Long proveedorId, Integer empresaId) {
        BigDecimal cantidad = nz(linea.getCantidad()).abs();
        if (cantidad.signum() <= 0) {
            return;
        }
        // Una ficha por unidad; una cantidad fraccionaria (3,5 m de estantería)
        // es un solo activo.
        boolean entera = cantidad.stripTrailingZeros().scale() <= 0;
        int fichas = entera ? cantidad.intValueExact() : 1;
        if (fichas > MAX_FICHAS_POR_LINEA) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "La línea de " + linea.getProducto().getNombre() + " crearía " + fichas
                            + " fichas de activo (máximo " + MAX_FICHAS_POR_LINEA + " por línea). Revise la cantidad.");
        }

        BigDecimal valorUnidad = valorLinea.divide(BigDecimal.valueOf(fichas), 2, RoundingMode.HALF_UP);
        // La última ficha absorbe el redondeo: la suma de fichas = débito de la compra.
        BigDecimal ultima = valorLinea.subtract(valorUnidad.multiply(BigDecimal.valueOf(fichas - 1)));
        long consecutivo = diferidoQuery.siguienteConsecutivoActivo(empresaId);

        for (int i = 0; i < fichas; i++) {
            ActivoFijoEntity activo = new ActivoFijoEntity();
            activo.setEmpresaId(empresaId);
            activo.setCodigo(String.format("AF-%06d", consecutivo + i));
            String nombre = linea.getProducto().getNombre();
            activo.setDescripcion(recortar(fichas > 1 ? nombre + " (" + (i + 1) + "/" + fichas + ")" : nombre, 200));
            activo.setCategoria(de(linea) == ClasificacionItem.INTANGIBLE ? "INTANGIBLE" : "EQUIPO");
            activo.setFechaAdquisicion(fecha);
            activo.setValorCompra(i == fichas - 1 ? ultima : valorUnidad);
            activo.setVidaUtilMeses(cuentas.vidaUtilMeses() != null ? cuentas.vidaUtilMeses() : VIDA_UTIL_POR_DEFECTO);
            activo.setMetodoDepreciacion("LINEA_RECTA");
            activo.setDepreciacionAcumulada(BigDecimal.ZERO);
            activo.setValorResidual(BigDecimal.ZERO);
            activo.setEstado("ACTIVO");
            activo.setCuentaActivoId(cuentas.cuentaCompraId());
            activo.setCuentaDepreciacionId(cuentas.cuentaDepreciacionId());
            activo.setCuentaGastoDepId(cuentas.cuentaGastoDepreciacionId());
            activo.setCentroCostoId(compra.getCentroCostoId());
            activo.setTerceroId(proveedorId);
            activo.setCompraId(compra.getId());
            activo.setCompraDetalleId(linea.getId());
            activo.setProductoId(linea.getProducto().getId());
            activo.setObservaciones("Creado por la compra #" + compra.getId()
                    + (compra.getNumeroCompra() != null ? " (factura " + compra.getNumeroCompra() + ")" : ""));
            activoRepo.save(activo);
        }
    }

    /**
     * Deshace lo que la compra creó, antes de anularla o de rehacer sus líneas.
     * Se niega si algún activo ya se depreció, se dio de baja o se vendió, o si
     * algún diferido ya generó cuotas: esos registros viven por su cuenta.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void revertirDeCompra(Long compraId, Integer empresaId, String accion) {
        if (diferidoQuery.activosDeCompraConMovimiento(empresaId, compraId) > 0) {
            throw new GlobalException(HttpStatus.CONFLICT,
                    "No se puede " + accion + ": un activo fijo que creó esta compra ya se depreció,"
                            + " se dio de baja o se vendió");
        }
        if (diferidoQuery.cuotasDeOrigen(empresaId, DiferidoEntity.ORIGEN_COMPRA, compraId) > 0) {
            throw new GlobalException(HttpStatus.CONFLICT,
                    "No se puede " + accion + ": un diferido que creó esta compra ya tiene cuotas amortizadas");
        }

        LocalDateTime ahora = LocalDateTime.now();
        for (Long id : diferidoQuery.activosDeCompra(empresaId, compraId)) {
            activoRepo.findById(id).ifPresent(a -> {
                a.setDeletedAt(ahora);
                a.setObservaciones((a.getObservaciones() != null ? a.getObservaciones() + " | " : "")
                        + "Eliminado al " + accion);
                activoRepo.save(a);
            });
        }
        for (Long id : diferidoQuery.deOrigen(empresaId, DiferidoEntity.ORIGEN_COMPRA, compraId)) {
            diferidoRepo.findById(id).ifPresent(d -> {
                d.setEstado(DiferidoEntity.ANULADO);
                diferidoRepo.save(d);
            });
        }
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private static String recortar(String texto, int max) {
        if (texto == null) {
            return "";
        }
        return texto.length() <= max ? texto : texto.substring(0, max);
    }
}
