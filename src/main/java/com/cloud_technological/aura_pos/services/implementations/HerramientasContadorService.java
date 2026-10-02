package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.entity.PlanCuentaEntity;
import com.cloud_technological.aura_pos.entity.TerceroEntity;
import com.cloud_technological.aura_pos.repositories.contabilidad.HerramientasContadorQueryRepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.HerramientasContadorQueryRepository.Referencia;
import com.cloud_technological.aura_pos.repositories.contabilidad.PlanCuentaJPARepository;
import com.cloud_technological.aura_pos.repositories.terceros.TerceroJPARepository;
import com.cloud_technological.aura_pos.utils.GlobalException;

import lombok.RequiredArgsConstructor;

/**
 * Herramientas del contador (Fase 4): traslado de cuentas y fusión de
 * terceros. Las dos dejan bitácora (V187) y se niegan a tocar meses cerrados.
 */
@Service
@RequiredArgsConstructor
public class HerramientasContadorService {

    private final HerramientasContadorQueryRepository repo;
    private final PlanCuentaJPARepository planRepo;
    private final TerceroJPARepository terceroRepo;

    // ── Traslado de cuentas ─────────────────────────────────────────────

    public record ResumenTraslado(int lineas, BigDecimal debitos, BigDecimal creditos, List<String> periodosCerrados,
            List<Map<String, Object>> movimientos) {
    }

    public ResumenTraslado vistaPreviaTraslado(Integer empresaId, Long origenId, Long destinoId, LocalDate desde,
            LocalDate hasta, Long terceroId) {
        validarTraslado(empresaId, origenId, destinoId, desde, hasta);
        Map<String, Object> r = repo.resumenTraslado(empresaId, origenId, desde, hasta, terceroId);
        return new ResumenTraslado(((Number) r.get("lineas")).intValue(), (BigDecimal) r.get("debitos"),
                (BigDecimal) r.get("creditos"),
                repo.periodosCerradosEnTraslado(empresaId, origenId, desde, hasta, terceroId),
                repo.movimientosTraslado(empresaId, origenId, desde, hasta, terceroId));
    }

    /**
     * Mueve los movimientos de una cuenta a otra en el rango. Solo cambia el
     * mayor: si la cuenta vieja sigue configurada en un concepto o en una
     * categoría, los documentos nuevos seguirán yendo allá.
     */
    @Transactional
    public int trasladar(Integer empresaId, Long origenId, Long destinoId, LocalDate desde, LocalDate hasta,
            Long terceroId, List<Long> detalleIds, String motivo, Long usuarioId) {
        if (motivo == null || motivo.isBlank())
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Escriba el motivo del traslado: queda en la bitácora");
        validarTraslado(empresaId, origenId, destinoId, desde, hasta);
        boolean elegidos = detalleIds != null && !detalleIds.isEmpty();
        // Con movimientos elegidos solo importan los meses de esos; sin elegir,
        // los de todo el rango.
        List<String> cerrados = elegidos
                ? repo.periodosCerradosDeLineas(empresaId, detalleIds)
                : repo.periodosCerradosEnTraslado(empresaId, origenId, desde, hasta, terceroId);
        if (!cerrados.isEmpty())
            throw new GlobalException(HttpStatus.CONFLICT,
                    "El traslado toca meses cerrados (" + String.join(", ", cerrados)
                            + "). Reábralos, acorte el rango o quite esos movimientos");
        int movidas = repo.trasladar(empresaId, origenId, destinoId, desde, hasta, terceroId, detalleIds);
        if (movidas == 0)
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La cuenta no tiene movimientos en ese rango");
        repo.logTraslado(empresaId, origenId, destinoId, desde, hasta, terceroId, movidas, motivo.trim(), usuarioId);
        return movidas;
    }

    public List<Map<String, Object>> historialTraslados(Integer empresaId) {
        return repo.historialTraslados(empresaId);
    }

    private void validarTraslado(Integer empresaId, Long origenId, Long destinoId, LocalDate desde, LocalDate hasta) {
        if (origenId == null || destinoId == null)
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Elija la cuenta de origen y la de destino");
        if (origenId.equals(destinoId))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La cuenta de origen y la de destino son la misma");
        if (desde == null || hasta == null || desde.isAfter(hasta))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El rango de fechas no es válido");
        planRepo.findByIdAndEmpresaId(origenId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "La cuenta de origen no existe"));
        PlanCuentaEntity destino = planRepo.findByIdAndEmpresaId(destinoId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "La cuenta de destino no existe"));
        if (!Boolean.TRUE.equals(destino.getActiva()) || !Boolean.TRUE.equals(destino.getAuxiliar()))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "La cuenta " + destino.getCodigo() + " está inactiva o es de agrupación: elija una auxiliar");
    }

    // ── Fusión de terceros ──────────────────────────────────────────────

    /** Qué se movería: tabla.columna → registros del tercero de origen. */
    public Map<String, Integer> vistaPreviaFusion(Integer empresaId, Long origenId, Long destinoId) {
        validarFusion(empresaId, origenId, destinoId);
        Map<String, Integer> conteo = new LinkedHashMap<>();
        for (Referencia r : repo.referenciasATercero()) {
            int n = repo.contar(r, origenId);
            if (n > 0) conteo.put(r.tabla() + "." + r.columna(), n);
        }
        return conteo;
    }

    /**
     * Deja un solo tercero: todo lo que apuntaba al origen (ventas, compras,
     * cartera, asientos, roles…) pasa al destino, y el origen queda inactivo.
     */
    @Transactional
    public int fusionar(Integer empresaId, Long origenId, Long destinoId, String motivo, Long usuarioId) {
        if (motivo == null || motivo.isBlank())
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Escriba el motivo de la fusión: queda en la bitácora");
        TerceroEntity origen = validarFusion(empresaId, origenId, destinoId);

        List<Referencia> refs = repo.referenciasATercero();
        // Un tercero es un solo empleado: dos fichas no se juntan a ciegas.
        Referencia empleados = new Referencia("empleados", "tercero_id");
        if (refs.contains(empleados) && repo.contar(empleados, origenId) > 0 && repo.contar(empleados, destinoId) > 0)
            throw new GlobalException(HttpStatus.CONFLICT,
                    "Los dos terceros tienen ficha de empleado: una persona no puede ser dos empleados. "
                            + "Retire la ficha duplicada antes de fusionar");

        Map<String, Integer> detalle = new LinkedHashMap<>();
        int total = 0;
        for (Referencia r : refs) {
            int n;
            if ("tercero_rol".equals(r.tabla())) {
                n = repo.contar(r, origenId);
                repo.fusionarRoles(origenId, destinoId);
            } else if ("tercero_credito".equals(r.tabla())) {
                n = repo.contar(r, origenId);
                repo.fusionarCredito(origenId, destinoId);
            } else {
                n = repo.mover(r, origenId, destinoId);
            }
            if (n > 0) {
                detalle.put(r.tabla() + "." + r.columna(), n);
                total += n;
            }
        }
        repo.desactivarTercero(origenId);
        repo.logFusion(empresaId, origenId, destinoId, origen.getNumeroDocumento(), nombre(origen),
                json(detalle), total, motivo.trim(), usuarioId);
        return total;
    }

    public List<Map<String, Object>> historialFusiones(Integer empresaId) {
        return repo.historialFusiones(empresaId);
    }

    private TerceroEntity validarFusion(Integer empresaId, Long origenId, Long destinoId) {
        if (origenId == null || destinoId == null)
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Elija el tercero que desaparece y el que se conserva");
        if (origenId.equals(destinoId))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Es el mismo tercero");
        TerceroEntity origen = terceroRepo.findByIdAndEmpresaId(origenId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "El tercero a fusionar no existe"));
        terceroRepo.findByIdAndEmpresaId(destinoId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "El tercero que se conserva no existe"));
        return origen;
    }

    private static String nombre(TerceroEntity t) {
        if (t.getRazonSocial() != null && !t.getRazonSocial().isBlank()) return t.getRazonSocial();
        return ((t.getNombres() != null ? t.getNombres() : "") + " " + (t.getApellidos() != null ? t.getApellidos() : "")).trim();
    }

    private static String json(Map<String, Integer> detalle) {
        StringBuilder sb = new StringBuilder("{");
        detalle.forEach((k, v) -> {
            if (sb.length() > 1) sb.append(',');
            sb.append('"').append(k).append("\":").append(v);
        });
        return sb.append('}').toString();
    }
}
