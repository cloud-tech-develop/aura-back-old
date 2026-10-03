package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.entity.CotizacionEntity;
import com.cloud_technological.aura_pos.entity.DocumentoRelacionEntity;
import com.cloud_technological.aura_pos.repositories.cotizaciones.CotizacionJPARepository;
import com.cloud_technological.aura_pos.repositories.cotizaciones.CotizacionQueryRepository;
import com.cloud_technological.aura_pos.repositories.cotizaciones.CotizacionQueryRepository.CabeceraBloqueada;
import com.cloud_technological.aura_pos.repositories.cotizaciones.CotizacionQueryRepository.LineaCotizacion;
import com.cloud_technological.aura_pos.repositories.documento.DocumentoRelacionQueryRepository;
import com.cloud_technological.aura_pos.utils.GlobalException;

import lombok.RequiredArgsConstructor;

/**
 * Cotización → venta (cadena documental D1).
 *
 * <p>Cada línea vendida desde una cotización queda como relación por línea en
 * {@code documento_relacion}; el estado de la cotización se deriva de ahí:
 * PENDIENTE (nada vendido), PARCIAL (algo) o CONVERTIDA (todo). Anular la venta
 * anula sus relaciones y la cotización vuelve a PARCIAL o PENDIENTE.
 *
 * <p>Ningún método abre transacción propia: todos corren dentro de la de la
 * venta (crear o anular), así una venta que falla no deja la cotización tocada.
 */
@Service
@RequiredArgsConstructor
public class CotizacionConversionService {

    public static final String PENDIENTE = "PENDIENTE";
    public static final String PARCIAL = "PARCIAL";
    public static final String CONVERTIDA = "CONVERTIDA";

    private static final String COTIZACION = DocumentoRelacionEntity.TIPO_COTIZACION;
    private static final String VENTA = DocumentoRelacionEntity.TIPO_VENTA;

    private final CotizacionQueryRepository cotizacionQuery;
    private final CotizacionJPARepository cotizacionJPA;
    private final DocumentoRelacionService relaciones;
    private final DocumentoRelacionQueryRepository relacionQuery;

    /**
     * Abre la conversión: bloquea la cotización y valida que todavía se pueda
     * vender. Se llama antes de guardar la cabecera de la venta.
     */
    public Conversion iniciar(Long cotizacionId, Integer empresaId) {
        CabeceraBloqueada cab = cotizacionQuery.bloquear(cotizacionId, empresaId);
        if (cab == null) {
            throw new GlobalException(HttpStatus.NOT_FOUND, "Cotización no encontrada");
        }
        validarVendible(cab.numero(), cab.estado(), cab.fechaVencimiento());
        Map<Long, LineaCotizacion> lineas = new LinkedHashMap<>();
        for (LineaCotizacion l : cotizacionQuery.lineas(cotizacionId)) lineas.put(l.id(), l);
        return new Conversion(cotizacionId, cab.numero(), empresaId, lineas);
    }

    /** Reglas para vender (o cargar en el POS) una cotización. */
    public static void validarVendible(String numero, String estado, LocalDate fechaVencimiento) {
        if (!PENDIENTE.equals(estado) && !PARCIAL.equals(estado)) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "La cotización " + numero + " está " + estado + ": no se puede vender");
        }
        if (fechaVencimiento != null && fechaVencimiento.isBefore(LocalDate.now())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "La cotización " + numero + " venció el " + fechaVencimiento + ": reactívela para venderla");
        }
    }

    /**
     * Registra que una línea de la venta sale de una línea de la cotización, por
     * lo vendido hasta el pendiente de esa línea. Las líneas agregadas en el POS
     * ({@code cotizacionDetalleId == null}) se ignoran.
     *
     * @param cantidadBase cantidad vendida en unidades base (la cotización no maneja presentaciones)
     */
    public void aplicarLinea(Conversion c, Integer usuarioId, Long cotizacionDetalleId, Long productoId,
            Long ventaId, Long ventaDetalleId, BigDecimal cantidadBase, BigDecimal valor) {
        if (cotizacionDetalleId == null) return;
        LineaCotizacion l = c.lineas().get(cotizacionDetalleId);
        if (l == null) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "La línea " + cotizacionDetalleId + " no pertenece a la cotización " + c.numero());
        }
        if (!l.productoId().equals(productoId)) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "La línea de '" + l.productoNombre() + "' de la cotización " + c.numero()
                            + " se está vendiendo como otro producto");
        }
        // Lee lo aplicado en esta misma transacción: si dos líneas de la venta
        // salen de la misma línea de la cotización, la segunda ve a la primera.
        BigDecimal pendiente = relaciones.pendiente(COTIZACION, l.id(), l.cantidad());
        if (pendiente.signum() <= 0 || cantidadBase.signum() <= 0) return;

        // Se vende más de lo cotizado (el POS suma el mismo producto en la misma
        // línea): la cotización se consume solo hasta su pendiente y el resto es
        // venta normal. Así nunca se aplica de más y no se le traba la venta al cajero.
        BigDecimal aplicada = cantidadBase.min(pendiente);
        BigDecimal valorAplicado = valor;
        if (valor != null && aplicada.compareTo(cantidadBase) < 0) {
            valorAplicado = valor.multiply(aplicada).divide(cantidadBase, 2, java.math.RoundingMode.HALF_UP);
        }
        relaciones.registrar(c.empresaId(), usuarioId, COTIZACION, c.cotizacionId(), l.id(),
                VENTA, ventaId, ventaDetalleId, aplicada, valorAplicado);
        c.lineasAplicadas++;
    }

    /** Cierra la conversión: deja la cotización en PARCIAL o CONVERTIDA. */
    public void cerrar(Conversion c) {
        if (c.lineasAplicadas == 0) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "La venta indica la cotización " + c.numero()
                            + " pero no vende nada de lo que le queda pendiente");
        }
        recalcularEstado(c.cotizacionId());
    }

    /**
     * Al anular una venta: anula sus relaciones con cotizaciones y las devuelve
     * a PARCIAL o PENDIENTE según lo que les siga aplicado.
     */
    public void alAnularVenta(Long ventaId) {
        List<Long> cotizaciones = relacionQuery.origenesVigentes(VENTA, ventaId, COTIZACION);
        relaciones.anularPorDestino(VENTA, ventaId);
        for (Long id : cotizaciones) recalcularEstado(id);
    }

    /**
     * Deriva el estado de la cotización de lo aplicado por línea. Solo mueve
     * entre PENDIENTE, PARCIAL y CONVERTIDA: una ANULADA o VENCIDA no se toca.
     */
    public void recalcularEstado(Long cotizacionId) {
        CotizacionEntity cot = cotizacionJPA.findById(cotizacionId).orElse(null);
        if (cot == null) return;
        String actual = cot.getEstado();
        if (!PENDIENTE.equals(actual) && !PARCIAL.equals(actual) && !CONVERTIDA.equals(actual)) return;

        List<LineaCotizacion> lineas = cotizacionQuery.lineas(cotizacionId);
        Map<Long, BigDecimal> aplicado = relaciones.aplicadoPorLineas(COTIZACION,
                lineas.stream().map(LineaCotizacion::id).toList());
        boolean alguna = false;
        boolean todas = !lineas.isEmpty();
        for (LineaCotizacion l : lineas) {
            BigDecimal ap = aplicado.getOrDefault(l.id(), BigDecimal.ZERO);
            if (ap.signum() > 0) alguna = true;
            if (ap.compareTo(l.cantidad()) < 0) todas = false;
        }
        String nuevo = todas ? CONVERTIDA : alguna ? PARCIAL : PENDIENTE;
        if (!nuevo.equals(actual)) {
            cot.setEstado(nuevo);
            cotizacionJPA.save(cot);
        }
    }

    /** Estado de una conversión en curso (vive lo que dura la venta). */
    public static final class Conversion {
        private final Long cotizacionId;
        private final String numero;
        private final Integer empresaId;
        private final Map<Long, LineaCotizacion> lineas;
        private int lineasAplicadas;

        Conversion(Long cotizacionId, String numero, Integer empresaId, Map<Long, LineaCotizacion> lineas) {
            this.cotizacionId = cotizacionId;
            this.numero = numero;
            this.empresaId = empresaId;
            this.lineas = lineas;
        }

        public Long cotizacionId() { return cotizacionId; }
        public String numero() { return numero; }
        public Integer empresaId() { return empresaId; }
        public Map<Long, LineaCotizacion> lineas() { return lineas; }
    }
}
