package com.cloud_technological.aura_pos.services.implementations;

import java.time.LocalDate;
import java.time.YearMonth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.contabilidad.application.exception.PeriodoCerradoException;
import com.cloud_technological.aura_pos.entity.PeriodoContableEntity;
import com.cloud_technological.aura_pos.repositories.periodo_contable.PeriodoContableJPARepository;

import lombok.extern.slf4j.Slf4j;

/**
 * A qué período contable pertenece un asiento: al de su fecha, no al que esté
 * abierto (V171).
 *
 * <p>Antes existía un solo período ABIERTO por empresa y todo caía ahí: cerrar
 * enero dejaba el sistema sin período y las ventas de febrero no se podían
 * contabilizar, y un documento de enero registrado en febrero quedaba contado en
 * febrero. Ahora cada mes es su propio período, pueden convivir varios abiertos
 * (así se registra febrero mientras el contador cierra enero) y el mes se abre
 * solo cuando llega su primer asiento.
 *
 * <p>Solo bloquea si el mes de ESA fecha está cerrado, y el mensaje dice qué
 * hacer: corregir la fecha o reabrir el período.
 */
@Slf4j
@Service
public class PeriodoContableResolver {

    /** Hasta dónde adelante se acepta fechar un documento, en meses. */
    private static final int MESES_FUTURO = 1;

    @Autowired
    private PeriodoContableJPARepository periodoRepo;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    /** El período del mes de la fecha, abriéndolo si es la primera vez. */
    public PeriodoContableEntity resolver(Integer empresaId, LocalDate fecha) {
        LocalDate dia = fecha != null ? fecha : LocalDate.now();
        YearMonth mes = YearMonth.from(dia);
        YearMonth tope = YearMonth.from(LocalDate.now()).plusMonths(MESES_FUTURO);
        if (mes.isAfter(tope)) {
            throw new PeriodoCerradoException("La fecha " + dia
                    + " está muy adelante: no se pueden registrar documentos más allá de "
                    + tope + ". Revise la fecha del documento.");
        }

        PeriodoContableEntity periodo = periodoRepo
                .findByEmpresaIdAndAnioAndMes(empresaId, (short) mes.getYear(), (short) mes.getMonthValue())
                .orElseGet(() -> abrirAutomatico(empresaId, mes));

        if (!"ABIERTO".equals(periodo.getEstado())) {
            throw new PeriodoCerradoException("El período contable " + nombre(mes)
                    + " está cerrado: no admite movimientos nuevos. Corrija la fecha del documento"
                    + " o reabra el período desde Períodos contables.");
        }
        return periodo;
    }

    /**
     * Guard para los documentos, antes de guardarlos: falla si el mes de la
     * fecha está cerrado (o demasiado adelante). Solo lee, no abre meses.
     *
     * <p>Sin este control el documento se guardaba y su asiento fallaba después
     * del commit (el motor contabiliza AFTER_COMMIT): la compra o el gasto
     * quedaban vivos, con inventario y caja movidos, y sin asiento en el mayor.
     */
    public void exigirAbierto(Integer empresaId, LocalDate fecha) {
        if (empresaId == null || fecha == null) return;
        YearMonth mes = YearMonth.from(fecha);
        YearMonth tope = YearMonth.from(LocalDate.now()).plusMonths(MESES_FUTURO);
        if (mes.isAfter(tope)) {
            throw new PeriodoCerradoException("La fecha " + fecha
                    + " está muy adelante: no se pueden registrar documentos más allá de "
                    + tope + ". Revise la fecha del documento.");
        }
        java.util.List<String> estado = jdbc.queryForList("""
            SELECT estado FROM periodo_contable
            WHERE empresa_id = :empresaId AND anio = :anio AND mes = :mes
            """, new MapSqlParameterSource("empresaId", empresaId)
                .addValue("anio", mes.getYear()).addValue("mes", mes.getMonthValue()), String.class);
        // Sin período creado el mes está abierto: se abrirá solo con el asiento.
        if (!estado.isEmpty() && !"ABIERTO".equals(estado.get(0))) {
            throw new PeriodoCerradoException("El período contable " + nombre(mes)
                    + " está cerrado: no admite documentos nuevos. Corrija la fecha del documento"
                    + " o reabra el período desde Períodos contables.");
        }
    }

    /** Igual que {@link #resolver}, pero devuelve solo el id. */
    public Long resolverId(Integer empresaId, LocalDate fecha) {
        return resolver(empresaId, fecha).getId();
    }

    /**
     * Abre el mes sin pedirle nada al usuario. El INSERT es
     * {@code ON CONFLICT DO NOTHING} porque dos ventas simultáneas pueden ser las
     * primeras del mes: la que pierda la carrera lee el período de la otra en vez
     * de reventar por la clave única.
     */
    private PeriodoContableEntity abrirAutomatico(Integer empresaId, YearMonth mes) {
        MapSqlParameterSource p = new MapSqlParameterSource("empresaId", empresaId)
                .addValue("anio", mes.getYear())
                .addValue("mes", mes.getMonthValue())
                .addValue("fechaApertura", java.sql.Date.valueOf(mes.atDay(1)));
        jdbc.update("""
            INSERT INTO periodo_contable (empresa_id, anio, mes, estado, fecha_apertura,
                                          creado_automatico, observaciones)
            VALUES (:empresaId, :anio, :mes, 'ABIERTO', :fechaApertura, TRUE,
                    'Abierto automáticamente al registrar el primer documento del mes')
            ON CONFLICT (empresa_id, anio, mes) DO NOTHING
            """, p);
        PeriodoContableEntity periodo = periodoRepo
                .findByEmpresaIdAndAnioAndMes(empresaId, (short) mes.getYear(), (short) mes.getMonthValue())
                .orElseThrow(() -> new PeriodoCerradoException(
                        "No se pudo abrir el período contable de " + nombre(mes) + "."));
        jdbc.update("""
            INSERT INTO periodo_contable_evento (periodo_contable_id, empresa_id, tipo, motivo)
            VALUES (:periodoId, :empresaId, 'APERTURA', 'Apertura automática al llegar el primer documento del mes')
            """, new MapSqlParameterSource("periodoId", periodo.getId()).addValue("empresaId", empresaId));
        reversarSobregiroDelAnterior(empresaId, null);
        log.info(">>> PERÍODO CONTABLE: abierto automáticamente {} para la empresa {}", nombre(mes), empresaId);
        return periodo;
    }

    /**
     * Sobregiro (E2): la reclasificación que dejó el último mes cerrado se
     * reversa al abrir el siguiente, porque es presentación, no un hecho nuevo.
     * Idempotente: no hace nada si ese mes no tuvo sobregiros.
     */
    public void reversarSobregiroDelAnterior(Integer empresaId, Long usuarioId) {
        periodoRepo.findFirstByEmpresaIdAndEstadoOrderByAnioDescMesDesc(empresaId, "CERRADO")
                .ifPresent(anterior -> eventPublisher.publishEvent(
                        new com.cloud_technological.aura_pos.event.ContabilidadReversaEvent(
                                "SOBREGIRO", anterior.getId(), empresaId,
                                usuarioId != null ? usuarioId.intValue() : null)));
    }

    /** Último día del mes que cubre el período; hoy si el período no existe. */
    public LocalDate ultimoDia(Long periodoId) {
        return periodoRepo.findById(periodoId)
                .map(p -> YearMonth.of(p.getAnio(), p.getMes()).atEndOfMonth())
                .orElseGet(LocalDate::now);
    }

    private static String nombre(YearMonth mes) {
        return String.format("%02d/%d", mes.getMonthValue(), mes.getYear());
    }
}
