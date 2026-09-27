package com.cloud_technological.aura_pos.services.implementations;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.periodo_contable.AbrirPeriodoDto;
import com.cloud_technological.aura_pos.dto.periodo_contable.CerrarPeriodoDto;
import com.cloud_technological.aura_pos.dto.periodo_contable.PeriodoContableTableDto;
import com.cloud_technological.aura_pos.entity.PeriodoContableEntity;
import com.cloud_technological.aura_pos.repositories.periodo_contable.PeriodoContableJPARepository;
import com.cloud_technological.aura_pos.repositories.periodo_contable.PeriodoContableQueryRepository;
import com.cloud_technological.aura_pos.services.PeriodoContableService;
import com.cloud_technological.aura_pos.utils.GlobalException;

@Service
public class PeriodoContableServiceImpl implements PeriodoContableService {

    @Autowired private PeriodoContableJPARepository jpaRepo;
    @Autowired private PeriodoContableQueryRepository queryRepo;
    @Autowired private com.cloud_technological.aura_pos.services.ContabilidadAutoService autoService;
    @Autowired private org.springframework.context.ApplicationEventPublisher eventPublisher;
    @Autowired private com.cloud_technological.aura_pos.repositories.contabilidad.AsientoContableJPARepository asientoRepo;
    @Autowired private com.cloud_technological.aura_pos.repositories.contabilidad.ExtractoBancarioJPARepository extractoRepo;
    @Autowired private com.cloud_technological.aura_pos.repositories.tesoreria.CuentaBancariaJPARepository cuentaBancariaRepo;
    @Autowired private com.cloud_technological.aura_pos.repositories.contabilidad.CierreAnualJPARepository cierreAnualRepo;
    @Autowired private PeriodoContableResolver periodoResolver;
    @Autowired private org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate jdbc;

    @Override
    public List<PeriodoContableTableDto> listar(Integer empresaId) {
        return queryRepo.listar(empresaId);
    }

    @Override
    @Transactional
    public PeriodoContableTableDto abrirPeriodo(AbrirPeriodoDto dto, Integer empresaId, Long usuarioId) {
        Short anio = dto.getAnio().shortValue();
        Short mes  = dto.getMes().shortValue();

        // Desde V171 pueden convivir varios períodos abiertos: se registra febrero
        // mientras el contador todavía cierra enero. Lo único que no se admite es
        // abrir un mes muy adelante, que siempre es un error de digitación.
        java.time.YearMonth pedido = java.time.YearMonth.of(anio, mes);
        java.time.YearMonth tope = java.time.YearMonth.now().plusMonths(1);
        if (pedido.isAfter(tope)) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                "No se puede abrir " + mes + "/" + anio + " todavía: como máximo se abre hasta "
                + tope.getMonthValue() + "/" + tope.getYear() + ".");
        }

        // Sobregiro (E2): la reclasificación del período anterior se reversa en el
        // nuevo período. AFTER_COMMIT (el listener necesita ver este período ABIERTO
        // ya persistido); idempotente — no-op si no hubo reclasificación.
        periodoResolver.reversarSobregiroDelAnterior(empresaId, usuarioId);

        // No duplicar el mismo mes/año
        if (jpaRepo.existsByEmpresaIdAndAnioAndMes(empresaId, anio, mes)) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                "Ya existe un período contable para " + mes + "/" + anio);
        }

        PeriodoContableEntity periodo = PeriodoContableEntity.builder()
                .empresaId(empresaId)
                .anio(anio)
                .mes(mes)
                .estado("ABIERTO")
                .fechaApertura(LocalDate.now())
                .usuarioAperturaId(usuarioId)
                .observaciones(dto.getObservaciones())
                .build();

        jpaRepo.saveAndFlush(periodo);
        registrarEvento(periodo, "APERTURA", dto.getObservaciones(), usuarioId);
        return devolver(periodo.getId(), empresaId);
    }

    @Override
    @Transactional
    public PeriodoContableTableDto cerrarPeriodo(Long id, CerrarPeriodoDto dto, Integer empresaId, Long usuarioId) {
        PeriodoContableEntity periodo = jpaRepo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Período contable no encontrado"));

        if ("CERRADO".equals(periodo.getEstado())) {
            throw new GlobalException(HttpStatus.CONFLICT, "El período ya está cerrado");
        }

        // Los meses se cierran en orden: cerrar marzo dejando febrero abierto deja
        // la utilidad del ejercicio mal acumulada y un balance que nadie puede firmar.
        List<PeriodoContableEntity> anteriores = jpaRepo
                .findByEmpresaIdAndEstadoOrderByAnioAscMesAsc(empresaId, "ABIERTO").stream()
                .filter(p -> !p.getId().equals(id) && esAnterior(p, periodo))
                .toList();
        if (!anteriores.isEmpty()) {
            PeriodoContableEntity primero = anteriores.get(0);
            throw new GlobalException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Antes de cerrar " + nombre(periodo) + " debe cerrar " + nombre(primero)
                + (anteriores.size() > 1 ? " y los demás meses anteriores que siguen abiertos." : "."));
        }

        // Un mes que todavía no ha terminado no se cierra: entrarían documentos después.
        java.time.YearMonth suyo = java.time.YearMonth.of(periodo.getAnio(), periodo.getMes());
        if (suyo.isAfter(java.time.YearMonth.now())) {
            throw new GlobalException(HttpStatus.UNPROCESSABLE_ENTITY,
                "El período " + nombre(periodo) + " todavía no ha empezado.");
        }

        // E3: no se cierra con comprobantes en borrador — quedarían huérfanos
        // (el período cerrado bloquea contabilizarlos después).
        if (asientoRepo.existsByEmpresaIdAndPeriodoContableIdAndEstado(empresaId, id, "BORRADOR")) {
            throw new GlobalException(HttpStatus.UNPROCESSABLE_ENTITY,
                "No se puede cerrar el período: hay comprobantes en BORRADOR pendientes de "
                + "aprobación. Contabilícelos o anúlelos desde Revisión de comprobantes, y las "
                + "notas contables en borrador desde Notas contables.");
        }

        // E9: si el mes tiene extractos bancarios importados, deben quedar
        // conciliados antes de cerrar — un extracto ABIERTO significa que el
        // saldo del banco en libros aún no está certificado y podrían faltar
        // ajustes (comisiones, GMF, intereses) que pertenecen a este período.
        String periodoExtracto = String.format("%04d-%02d", periodo.getAnio(), periodo.getMes());
        List<com.cloud_technological.aura_pos.entity.ExtractoBancarioEntity> extractosAbiertos =
                extractoRepo.findByEmpresaIdAndPeriodoAndEstado(empresaId, periodoExtracto,
                        com.cloud_technological.aura_pos.entity.ExtractoBancarioEntity.ESTADO_ABIERTO);
        if (!extractosAbiertos.isEmpty()) {
            List<String> cuentas = extractosAbiertos.stream()
                    .map(e -> cuentaBancariaRepo.findById(e.getCuentaBancariaId())
                            .map(c -> c.getNombre())
                            .orElse("cuenta #" + e.getCuentaBancariaId()))
                    .toList();
            throw new GlobalException(HttpStatus.UNPROCESSABLE_ENTITY,
                "No se puede cerrar el período: hay extractos bancarios de " + periodoExtracto
                + " sin conciliar (" + String.join(", ", cuentas)
                + "). Concilie y cierre los extractos, o elimínelos si se crearon por error.");
        }

        // Validar que todos los asientos cuadren antes de cerrar
        List<String> sinCuadre = queryRepo.comprobantesSinCuadre(id);
        if (!sinCuadre.isEmpty()) {
            throw new GlobalException(HttpStatus.UNPROCESSABLE_ENTITY,
                "No se puede cerrar el período. Los siguientes comprobantes no cuadran (débito ≠ crédito): "
                + String.join(", ", sinCuadre));
        }

        // Asiento de cierre: cancela ingresos/costos/gastos contra la utilidad del
        // ejercicio. Dentro de la misma transacción: si falla, no se cierra el período.
        autoService.generarCierre(id, empresaId, usuarioId != null ? usuarioId.intValue() : null);

        // Sobregiro (E2): bancos con saldo crédito quedan presentados en 21xx.
        autoService.generarReclasificacionSobregiro(id, empresaId,
                usuarioId != null ? usuarioId.intValue() : null);

        periodo.setEstado("CERRADO");
        periodo.setFechaCierre(LocalDate.now());
        periodo.setUsuarioCierreId(usuarioId);
        if (dto.getObservaciones() != null && !dto.getObservaciones().isBlank()) {
            periodo.setObservaciones(dto.getObservaciones());
        }

        jpaRepo.saveAndFlush(periodo);
        registrarEvento(periodo, "CIERRE", dto.getObservaciones(), usuarioId);
        return devolver(id, empresaId);
    }

    /**
     * Reabre un mes cerrado. Es una operación excepcional y queda registrada: se
     * anulan los asientos que produjo el cierre (cancelación de resultados y
     * reclasificación de sobregiro) y el mes vuelve a admitir movimientos.
     */
    @Override
    @Transactional
    public PeriodoContableTableDto reabrirPeriodo(Long id, CerrarPeriodoDto dto, Integer empresaId, Long usuarioId) {
        String motivo = dto != null && dto.getObservaciones() != null ? dto.getObservaciones().trim() : "";
        if (motivo.isEmpty()) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Indique por qué se reabre el período");
        }
        PeriodoContableEntity periodo = jpaRepo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Período contable no encontrado"));
        if (!"CERRADO".equals(periodo.getEstado())) {
            throw new GlobalException(HttpStatus.CONFLICT, "El período no está cerrado");
        }

        // Se reabre de atrás para adelante: si marzo ya cerró, febrero no se toca
        // sin reabrir marzo primero.
        List<PeriodoContableEntity> posteriores = jpaRepo
                .findByEmpresaIdAndEstadoOrderByAnioAscMesAsc(empresaId, "CERRADO").stream()
                .filter(p -> !p.getId().equals(id) && esAnterior(periodo, p))
                .toList();
        if (!posteriores.isEmpty()) {
            PeriodoContableEntity siguiente = posteriores.get(posteriores.size() - 1);
            throw new GlobalException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Para reabrir " + nombre(periodo) + " primero debe reabrir " + nombre(siguiente)
                + " y los meses cerrados que le siguen.");
        }

        // El cierre fiscal del año ya usó estos saldos: reabrir sin deshacerlo
        // dejaría la provisión de renta y el traslado de utilidad mintiendo.
        int anio = periodo.getAnio();
        if (cierreAnualRepo.existsByEmpresaIdAndAnioAndTipo(empresaId, anio, "TRASLADO")
                || cierreAnualRepo.existsByEmpresaIdAndAnioAndTipo(empresaId, anio, "PROVISION_RENTA")) {
            throw new GlobalException(HttpStatus.UNPROCESSABLE_ENTITY,
                "El año " + anio + " ya tiene cierre fiscal (provisión de renta o traslado de utilidad). "
                + "Deshágalo antes de reabrir " + nombre(periodo) + ".");
        }

        // Los asientos que nacieron del cierre se anulan: sin ellos las cuentas de
        // resultado vuelven a mostrar el movimiento del mes.
        anularAsientoDeCierre("CIERRE", id, empresaId);
        anularAsientoDeCierre("SOBREGIRO", id, empresaId);

        periodo.setEstado("ABIERTO");
        periodo.setFechaCierre(null);
        periodo.setUsuarioCierreId(null);
        periodo.setFechaReapertura(java.time.LocalDateTime.now());
        periodo.setUsuarioReaperturaId(usuarioId);
        periodo.setMotivoReapertura(motivo.length() > 300 ? motivo.substring(0, 300) : motivo);
        periodo.setReaperturas((periodo.getReaperturas() != null ? periodo.getReaperturas() : 0) + 1);
        jpaRepo.saveAndFlush(periodo);
        registrarEvento(periodo, "REAPERTURA", motivo, usuarioId);
        return devolver(id, empresaId);
    }

    /** El período del mes en curso, si ya existe (la campana y el front lo usan). */
    @Override
    public Optional<PeriodoContableEntity> getPeriodoAbierto(Integer empresaId) {
        LocalDate hoy = LocalDate.now();
        return jpaRepo.findByEmpresaIdAndAnioAndMes(empresaId,
                (short) hoy.getYear(), (short) hoy.getMonthValue());
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private void anularAsientoDeCierre(String tipoOrigen, Long periodoId, Integer empresaId) {
        asientoRepo.findFirstByTipoOrigenAndOrigenIdAndEmpresaIdAndEstado(
                tipoOrigen, periodoId, empresaId, "CONTABILIZADO")
                .ifPresent(a -> {
                    a.setEstado("ANULADO");
                    asientoRepo.save(a);
                });
    }

    private void registrarEvento(PeriodoContableEntity periodo, String tipo, String motivo, Long usuarioId) {
        jdbc.update("""
            INSERT INTO periodo_contable_evento (periodo_contable_id, empresa_id, tipo, motivo, usuario_id)
            VALUES (:periodoId, :empresaId, :tipo, :motivo, :usuarioId)
            """, new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                .addValue("periodoId", periodo.getId())
                .addValue("empresaId", periodo.getEmpresaId())
                .addValue("tipo", tipo)
                .addValue("motivo", motivo != null && motivo.length() > 300 ? motivo.substring(0, 300) : motivo)
                .addValue("usuarioId", usuarioId));
    }

    private PeriodoContableTableDto devolver(Long id, Integer empresaId) {
        return queryRepo.listar(empresaId).stream()
                .filter(p -> p.getId().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private static boolean esAnterior(PeriodoContableEntity a, PeriodoContableEntity b) {
        return a.getAnio() < b.getAnio()
                || (a.getAnio().equals(b.getAnio()) && a.getMes() < b.getMes());
    }

    private static String nombre(PeriodoContableEntity p) {
        return String.format("%02d/%d", p.getMes(), p.getAnio());
    }
}
