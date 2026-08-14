package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.entity.ContratoCentroCostoEntity;
import com.cloud_technological.aura_pos.entity.ContratoLaboralEntity;
import com.cloud_technological.aura_pos.entity.ContratoRenovacionEntity;
import com.cloud_technological.aura_pos.entity.ContratoSalarioHistorialEntity;
import com.cloud_technological.aura_pos.repositories.nomina.ContratoLaboralJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina.ContratoRenovacionJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina.ContratoSalarioHistorialJPARepository;
import com.cloud_technological.aura_pos.utils.GlobalException;

import lombok.extern.slf4j.Slf4j;

/**
 * Contratos laborales (Fase 2).
 *
 * <p>La responsabilidad crítica de esta clase: <b>un cambio de salario nunca
 * puede perder el valor anterior</b>. Antes, un aumento sobrescribía
 * {@code empleados.salario_base} y el dato viejo desaparecía. Aquí cada cambio
 * cierra la fila vigente y abre una nueva.
 */
@Slf4j
@Service
public class ContratoLaboralService {

    private static final BigDecimal CIEN = new BigDecimal("100");

    private final ContratoLaboralJPARepository contratoRepo;
    private final ContratoSalarioHistorialJPARepository historialRepo;
    private final ContratoRenovacionJPARepository renovacionRepo;

    public ContratoLaboralService(ContratoLaboralJPARepository contratoRepo,
                                  ContratoSalarioHistorialJPARepository historialRepo,
                                  ContratoRenovacionJPARepository renovacionRepo) {
        this.contratoRepo = contratoRepo;
        this.historialRepo = historialRepo;
        this.renovacionRepo = renovacionRepo;
    }

    // ── Consultas ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ContratoLaboralEntity obtener(Long id, Integer empresaId) {
        return contratoRepo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Contrato no encontrado"));
    }

    /** Cargos ya usados en la empresa, para el autocompletar del form de contrato. */
    @Transactional(readOnly = true)
    public List<String> cargosUsados(Integer empresaId) {
        return contratoRepo.cargosUsados(empresaId);
    }

    @Transactional(readOnly = true)
    public List<ContratoLaboralEntity> activosDe(Long empleadoId) {
        return contratoRepo.findActivosByEmpleado(empleadoId);
    }

    /**
     * Cambia el procedimiento de retención en la fuente ('1' o '2').
     *
     * <p>El procedimiento 2 fija un porcentaje semestral; el 1 se recalcula mes
     * a mes. Es una decisión del empleador que aplica al contrato.
     */
    @Transactional
    public ContratoLaboralEntity cambiarProcedimientoRetefuente(Long contratoId, Integer empresaId,
                                                                String procedimiento) {
        if (!"1".equals(procedimiento) && !"2".equals(procedimiento)) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El procedimiento debe ser '1' o '2'");
        }
        ContratoLaboralEntity contrato = obtener(contratoId, empresaId);
        contrato.setProcedimientoRetefuente(procedimiento);
        return contratoRepo.save(contrato);
    }

    /**
     * Fija el tipo de cotizante (código UGPP) del contrato. Lo exige PILA.
     *
     * <p>Ej.: '01' dependiente, '02' servicio doméstico, '12' aprendiz en lectiva…
     */
    @Transactional
    public ContratoLaboralEntity cambiarTipoCotizante(Long contratoId, Integer empresaId,
                                                      String tipoCotizante, String subtipoCotizante) {
        if (tipoCotizante == null || tipoCotizante.isBlank()) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El tipo de cotizante es obligatorio");
        }
        ContratoLaboralEntity contrato = obtener(contratoId, empresaId);
        contrato.setTipoCotizante(tipoCotizante);
        contrato.setSubtipoCotizante(subtipoCotizante);
        return contratoRepo.save(contrato);
    }

    /** Contratos a liquidar en un período. Incluye ingresos y retiros a mitad de mes. */
    @Transactional(readOnly = true)
    public List<ContratoLaboralEntity> vigentesEnPeriodo(Integer empresaId, LocalDate desde, LocalDate hasta) {
        return contratoRepo.findVigentesEnPeriodo(empresaId, desde, hasta);
    }

    /**
     * Contrato principal activo de un empleado.
     *
     * <p>Puente para el código que todavía razona en términos de "el contrato
     * del empleado". Falla si hay ambigüedad en vez de elegir uno arbitrario.
     */
    @Transactional(readOnly = true)
    public ContratoLaboralEntity principalDe(Long empleadoId) {
        List<ContratoLaboralEntity> activos = contratoRepo.findActivosByEmpleado(empleadoId);
        if (activos.isEmpty()) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "El empleado no tiene contrato activo");
        }
        List<ContratoLaboralEntity> principales = activos.stream()
                .filter(c -> Boolean.TRUE.equals(c.getEsPrincipal()))
                .toList();
        if (principales.size() > 1) {
            throw new GlobalException(HttpStatus.CONFLICT,
                    "El empleado tiene " + principales.size() + " contratos marcados como principal");
        }
        return principales.isEmpty() ? activos.get(0) : principales.get(0);
    }

    // ── Alta ────────────────────────────────────────────────────────────────

    @Transactional
    public ContratoLaboralEntity crear(ContratoLaboralEntity contrato) {
        validarFechas(contrato);
        validarPrincipalUnico(contrato);

        ContratoLaboralEntity guardado = contratoRepo.save(contrato);

        // Primera fila del histórico: sin ella, el contrato nace sin memoria.
        ContratoSalarioHistorialEntity inicial = new ContratoSalarioHistorialEntity();
        inicial.setContrato(guardado);
        inicial.setSalario(guardado.getSalarioBase());
        inicial.setFechaDesde(guardado.getFechaInicio());
        inicial.setMotivo("Salario inicial");
        historialRepo.save(inicial);

        return guardado;
    }

    /**
     * Edita los datos del contrato (corrección de errores).
     *
     * <p>No toca el salario: eso pasa por {@link #cambiarSalario} para preservar
     * el histórico. Aquí se corrigen cargo, tipo, fechas, ARL, procedimiento, etc.
     */
    @Transactional
    public ContratoLaboralEntity editar(Long contratoId, Integer empresaId,
                                        ContratoLaboralEntity datos) {
        ContratoLaboralEntity contrato = obtener(contratoId, empresaId);
        contrato.setTipoContrato(datos.getTipoContrato());
        contrato.setCargo(datos.getCargo());
        contrato.setFechaInicio(datos.getFechaInicio());
        contrato.setFechaFin(datos.getFechaFin());
        contrato.setEsSalarioIntegral(datos.getEsSalarioIntegral());
        contrato.setFase(datos.getFase());
        contrato.setPeriodicidad(datos.getPeriodicidad());
        contrato.setEsPrincipal(datos.getEsPrincipal());
        contrato.setProcedimientoRetefuente(datos.getProcedimientoRetefuente());
        contrato.setNivelRiesgoArl(datos.getNivelRiesgoArl());
        contrato.setObservacion(datos.getObservacion());

        validarFechas(contrato);
        validarPrincipalUnico(contrato);
        return contratoRepo.save(contrato);
    }

    // ── Cambio de salario ───────────────────────────────────────────────────

    /**
     * Cambia el salario preservando el histórico.
     *
     * <p>Cierra la fila vigente en {@code fechaDesde − 1 día} y abre una nueva.
     * De aquí sale la bandera {@code vsp} de PILA y la base de los retroactivos.
     *
     * @param fechaDesde desde cuándo rige. Puede ser retroactiva.
     */
    @Transactional
    public ContratoLaboralEntity cambiarSalario(Long contratoId, Integer empresaId,
                                                BigDecimal nuevoSalario, LocalDate fechaDesde,
                                                String motivo, Long usuarioId) {
        ContratoLaboralEntity contrato = obtener(contratoId, empresaId);

        if (nuevoSalario == null || nuevoSalario.signum() <= 0) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El salario debe ser mayor a cero");
        }
        if (fechaDesde.isBefore(contrato.getFechaInicio())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "La fecha del cambio no puede ser anterior al inicio del contrato");
        }

        historialRepo.findVigente(contratoId).ifPresent(vigente -> {
            if (!fechaDesde.isAfter(vigente.getFechaDesde())) {
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "Ya existe un salario vigente desde " + vigente.getFechaDesde()
                        + ". El cambio debe ser posterior.");
            }
            vigente.setFechaHasta(fechaDesde.minusDays(1));
            historialRepo.save(vigente);
        });

        ContratoSalarioHistorialEntity nuevo = new ContratoSalarioHistorialEntity();
        nuevo.setContrato(contrato);
        nuevo.setSalario(nuevoSalario);
        nuevo.setFechaDesde(fechaDesde);
        nuevo.setMotivo(motivo);
        nuevo.setCreatedBy(usuarioId);
        historialRepo.save(nuevo);

        // Denormalización de lectura: la verdad histórica está en el histórico.
        contrato.setSalarioBase(nuevoSalario);
        return contratoRepo.save(contrato);
    }

    /** Salario que regía en una fecha. Para retroactivos, PILA y liquidación definitiva. */
    @Transactional(readOnly = true)
    public BigDecimal salarioEnFecha(Long contratoId, LocalDate fecha) {
        return historialRepo.findEnFecha(contratoId, fecha)
                .map(ContratoSalarioHistorialEntity::getSalario)
                .orElseThrow(() -> new GlobalException(HttpStatus.CONFLICT,
                        "El contrato " + contratoId + " no tiene salario registrado en " + fecha
                        + ". Historial incompleto."));
    }

    /** ¿Hubo cambio de salario en el período? Alimenta la bandera `vsp` de PILA. */
    @Transactional(readOnly = true)
    public boolean tuvoVariacionSalarialEn(Long contratoId, LocalDate desde, LocalDate hasta) {
        return !historialRepo.findCambiosEnPeriodo(contratoId, desde, hasta).isEmpty();
    }

    // ── Terminación ─────────────────────────────────────────────────────────

    @Transactional
    public ContratoLaboralEntity terminar(Long contratoId, Integer empresaId,
                                          LocalDate fechaFin, String causaRetiro) {
        ContratoLaboralEntity contrato = obtener(contratoId, empresaId);
        if (fechaFin.isBefore(contrato.getFechaInicio())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "La fecha de terminación no puede ser anterior al inicio");
        }
        contrato.setFechaFin(fechaFin);
        contrato.setCausaRetiro(causaRetiro);   // define la indemnización (Fase 8)
        contrato.setEstado("TERMINADO");

        historialRepo.findVigente(contratoId).ifPresent(vigente -> {
            vigente.setFechaHasta(fechaFin);
            historialRepo.save(vigente);
        });

        return contratoRepo.save(contrato);
    }

    // ── Validaciones ────────────────────────────────────────────────────────

    private void validarFechas(ContratoLaboralEntity c) {
        if (c.getFechaInicio() == null) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La fecha de inicio es obligatoria");
        }
        if (c.getFechaFin() != null && c.getFechaFin().isBefore(c.getFechaInicio())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "La fecha de fin no puede ser anterior a la de inicio");
        }
        if ("FIJO".equals(c.getTipoContrato()) && c.getFechaFin() == null) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Un contrato a término fijo exige fecha de fin");
        }
        if ("INDEFINIDO".equals(c.getTipoContrato()) && c.getFechaFin() != null) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Un contrato indefinido no lleva fecha de fin");
        }
    }

    private void validarPrincipalUnico(ContratoLaboralEntity nuevo) {
        if (!Boolean.TRUE.equals(nuevo.getEsPrincipal()) || nuevo.getEmpleado() == null) return;
        boolean yaHayPrincipal = contratoRepo.findActivosByEmpleado(nuevo.getEmpleado().getId())
                .stream()
                .filter(c -> !c.getId().equals(nuevo.getId()))
                .anyMatch(c -> Boolean.TRUE.equals(c.getEsPrincipal()));
        if (yaHayPrincipal) {
            throw new GlobalException(HttpStatus.CONFLICT,
                    "El empleado ya tiene un contrato principal activo. "
                    + "Marque este como no principal o termine el anterior.");
        }
    }

    /**
     * Los porcentajes de centro de costo deben sumar 100.
     *
     * <p>No se puede validar con un CHECK: no puede mirar otras filas.
     */
    @Transactional(readOnly = true)
    public void validarDistribucionCentrosCosto(ContratoLaboralEntity contrato) {
        List<ContratoCentroCostoEntity> ccs = contrato.getCentrosCosto();
        if (ccs == null || ccs.isEmpty()) return;   // sin distribución: válido

        BigDecimal suma = ccs.stream()
                .map(ContratoCentroCostoEntity::getPorcentaje)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (suma.compareTo(CIEN) != 0) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Los porcentajes de centro de costo suman " + suma + ", deben sumar 100");
        }
    }

    // ── Renovaciones ────────────────────────────────────────────────────────

    /**
     * Prórroga de un contrato a término fijo.
     *
     * <p>Deja rastro en vez de sobrescribir {@code fecha_fin}: hay consecuencias
     * legales atadas al número de prórrogas de un contrato a término fijo.
     */
    @Transactional
    public ContratoRenovacionEntity renovar(Long contratoId, Integer empresaId,
                                            LocalDate desde, LocalDate hasta,
                                            String descripcion, Long usuarioId) {
        ContratoLaboralEntity contrato = obtener(contratoId, empresaId);

        if (!"FIJO".equals(contrato.getTipoContrato())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Solo se renuevan los contratos a término fijo. Este es "
                    + contrato.getTipoContrato());
        }
        if (hasta.isBefore(desde)) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "La fecha final de la renovación no puede ser anterior a la inicial");
        }
        if (contrato.getFechaFin() != null && desde.isBefore(contrato.getFechaFin())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "La renovación debe empezar después del vencimiento actual ("
                    + contrato.getFechaFin() + ")");
        }

        ContratoRenovacionEntity r = new ContratoRenovacionEntity();
        r.setContrato(contrato);
        r.setFechaInicial(desde);
        r.setFechaFinal(hasta);
        r.setDescripcion(descripcion);
        r.setCreatedBy(usuarioId);
        renovacionRepo.save(r);

        // El contrato extiende su vigencia; la renovación queda como rastro.
        contrato.setFechaFin(hasta);
        return renovacionRepo.save(r);
    }

    @Transactional(readOnly = true)
    public List<ContratoRenovacionEntity> renovacionesDe(Long contratoId) {
        return renovacionRepo.findByContratoIdOrderByFechaInicialDesc(contratoId);
    }

    @Transactional(readOnly = true)
    public List<ContratoSalarioHistorialEntity> historialDe(Long contratoId) {
        return historialRepo.findByContratoIdOrderByFechaDesdeDesc(contratoId);
    }

    /** Contratos a término fijo que vencen dentro de N días. */
    @Transactional(readOnly = true)
    public List<ContratoLaboralEntity> porVencer(Integer empresaId, int dias) {
        LocalDate hoy = LocalDate.now();
        return contratoRepo.findPorVencer(empresaId, hoy, hoy.plusDays(dias));
    }
}
