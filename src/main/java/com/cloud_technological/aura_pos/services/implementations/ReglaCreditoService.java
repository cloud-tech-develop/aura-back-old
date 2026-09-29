package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.cartera.credito.ReglaCreditoDto;
import com.cloud_technological.aura_pos.dto.cartera.credito.SimulacionReglaDto;
import com.cloud_technological.aura_pos.entity.HistorialCreditoEntity;
import com.cloud_technological.aura_pos.entity.ReglaCreditoEntity;
import com.cloud_technological.aura_pos.entity.TerceroCreditoEntity;
import com.cloud_technological.aura_pos.repositories.cartera.CarteraQueryRepository;
import com.cloud_technological.aura_pos.repositories.cartera.HistorialCreditoJPARepository;
import com.cloud_technological.aura_pos.repositories.cartera.ReglaCreditoJPARepository;
import com.cloud_technological.aura_pos.repositories.cartera.TerceroCreditoJPARepository;
import com.cloud_technological.aura_pos.repositories.empresas.EmpresaJPARepository;
import com.cloud_technological.aura_pos.services.CarteraService;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Reglas automáticas de crédito: CRUD, simulación y evaluación.
 *
 * <p>Las condiciones se guardan en JSON con las claves que ya usaba el motor
 * (score_minimo, sin_mora_dias, pagos_consecutivos_a_tiempo, estado_credito) más
 * las nuevas; hacia afuera viajan como campos. Una regla no vuelve a actuar
 * sobre el mismo cliente antes de sus días de espera: sin eso, "subir 10 % el
 * cupo al pagar" lo subiría en cada abono.
 */
@Slf4j
@Service
public class ReglaCreditoService {

    public static final Set<String> TIPOS = Set.of("AUMENTO_CUPO", "REDUCCION_CUPO", "SUSPENSION", "BLOQUEO", "ALERTA");
    public static final Set<String> EVENTOS = Set.of("AL_PAGAR", "AL_VENDER", "PERIODICO");

    @Autowired private ReglaCreditoJPARepository reglaRepo;
    @Autowired private TerceroCreditoJPARepository creditoRepo;
    @Autowired private HistorialCreditoJPARepository historialRepo;
    @Autowired private EmpresaJPARepository empresaRepo;
    @Autowired private CarteraQueryRepository carteraQuery;
    @Autowired private NamedParameterJdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired @Lazy private CarteraService carteraService;
    @Autowired @Lazy private ReglaCreditoService self;

    /** Lo que se sabe del cliente al evaluar. */
    record Metricas(int score, String estado, int diasMora, int pagosATiempo, int promesasIncumplidas,
            int usoCupoPct, BigDecimal cupo) {
    }

    // ── CRUD ────────────────────────────────────────────────────────────
    public List<ReglaCreditoDto> listar(Integer empresaId) {
        Map<Long, Object[]> stats = new java.util.HashMap<>();
        jdbc.query("""
            SELECT h.regla_id, COUNT(*) AS veces, MAX(h.created_at) AS ultima
            FROM historial_credito h
            WHERE h.empresa_id = :empresaId AND h.regla_id IS NOT NULL
            GROUP BY h.regla_id
            """, new MapSqlParameterSource("empresaId", empresaId), rs -> {
            stats.put(rs.getLong("regla_id"), new Object[] { rs.getInt("veces"),
                    rs.getTimestamp("ultima") != null ? rs.getTimestamp("ultima").toLocalDateTime() : null });
        });
        List<ReglaCreditoDto> lista = new ArrayList<>();
        for (ReglaCreditoEntity r : reglaRepo.findByEmpresaIdOrderByOrdenAscIdAsc(empresaId)) {
            ReglaCreditoDto dto = aDto(r);
            Object[] st = stats.get(r.getId());
            dto.setVecesAplicada(st != null ? (Integer) st[0] : 0);
            dto.setUltimaAplicacion(st != null ? (LocalDateTime) st[1] : null);
            lista.add(dto);
        }
        return lista;
    }

    @Transactional
    public ReglaCreditoDto guardar(Long id, ReglaCreditoDto dto, Integer empresaId) {
        validar(dto);
        ReglaCreditoEntity r = id == null
                ? ReglaCreditoEntity.builder().empresa(empresaRepo.getReferenceById(empresaId)).build()
                : buscar(id, empresaId);
        r.setNombre(dto.getNombre().trim());
        r.setDescripcion(dto.getDescripcion() != null && !dto.getDescripcion().isBlank() ? dto.getDescripcion().trim() : null);
        r.setTipo(dto.getTipo());
        r.setEvento(dto.getEvento());
        r.setActivo(dto.getActivo() == null || dto.getActivo());
        r.setOrden(dto.getOrden() != null ? dto.getOrden() : 1);
        r.setDiasEntreAplicaciones(dto.getDiasEntreAplicaciones() != null ? dto.getDiasEntreAplicaciones() : 30);
        r.setCondicionJson(json(condicionesAMapa(dto.getCondiciones())));
        r.setAccionJson(json(accionAMapa(dto.getTipo(), dto.getAccion())));
        return aDto(reglaRepo.save(r));
    }

    @Transactional
    public ReglaCreditoDto cambiarActivo(Long id, boolean activo, Integer empresaId) {
        ReglaCreditoEntity r = buscar(id, empresaId);
        r.setActivo(activo);
        return aDto(reglaRepo.save(r));
    }

    @Transactional
    public void eliminar(Long id, Integer empresaId) {
        reglaRepo.delete(buscar(id, empresaId));
    }

    /** Qué pasaría hoy si la regla corriera sobre todos los clientes con crédito. */
    public List<SimulacionReglaDto> simular(ReglaCreditoDto dto, Integer empresaId) {
        validar(dto);
        Map<String, Object> cond = condicionesAMapa(dto.getCondiciones());
        Map<String, Object> accion = accionAMapa(dto.getTipo(), dto.getAccion());
        int espera = dto.getDiasEntreAplicaciones() != null ? dto.getDiasEntreAplicaciones() : 30;
        List<SimulacionReglaDto> salida = new ArrayList<>();
        for (TerceroCreditoEntity c : creditoRepo.findByEmpresaId(empresaId)) {
            Metricas m = metricas(c, empresaId);
            if (!cumple(cond, m)) continue;
            BigDecimal cupoNuevo = c.getCupoCreditoActual();
            String estadoNuevo = c.getEstadoCredito();
            switch (dto.getTipo()) {
                case "AUMENTO_CUPO" -> cupoNuevo = aumentar(c.getCupoCreditoActual(), accion);
                case "REDUCCION_CUPO" -> cupoNuevo = reducir(c.getCupoCreditoActual(), accion);
                case "SUSPENSION" -> estadoNuevo = "BLOQUEADO".equals(estadoNuevo) ? estadoNuevo : "SUSPENDIDO";
                case "BLOQUEO" -> estadoNuevo = "BLOQUEADO";
                default -> { }
            }
            boolean enEspera = dto.getId() != null && enEspera(dto.getId(), c.getTercero().getId(), espera);
            salida.add(new SimulacionReglaDto(c.getTercero().getId(), nombre(c), m.score(), m.diasMora(),
                    c.getEstadoCredito(), c.getCupoCreditoActual(), cupoNuevo, estadoNuevo, enEspera));
        }
        return salida;
    }

    // ── Evaluación ──────────────────────────────────────────────────────
    /**
     * Pone al día score y estado del cliente y corre las reglas del evento.
     * Transacción propia: una regla rota no tumba el pago o la venta que la disparó.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int evaluarCliente(Long terceroId, Integer empresaId, String evento, boolean recalcularScore) {
        TerceroCreditoEntity credito = creditoRepo.findByTerceroIdAndEmpresaId(terceroId, empresaId).orElse(null);
        if (credito == null) return 0;
        if (recalcularScore) carteraService.recalcularScore(terceroId, empresaId);
        credito = creditoRepo.findByTerceroIdAndEmpresaId(terceroId, empresaId).orElse(credito);

        int aplicadas = 0;
        for (ReglaCreditoEntity regla : reglaRepo.findByEmpresaIdAndActivoTrueAndEventoOrderByOrdenAsc(empresaId, evento)) {
            try {
                Map<String, Object> cond = mapa(regla.getCondicionJson());
                Map<String, Object> accion = mapa(regla.getAccionJson());
                if (!cumple(cond, metricas(credito, empresaId))) continue;
                if (enEspera(regla.getId(), terceroId, regla.getDiasEntreAplicaciones())) continue;
                if (aplicar(regla, accion, credito)) aplicadas++;
            } catch (Exception e) {
                log.warn("Regla de crédito {} ({}) no se pudo evaluar: {}", regla.getId(), regla.getNombre(), e.getMessage());
            }
        }
        return aplicadas;
    }

    /** Pasada nocturna: score, estado por mora y reglas PERIODICO de todos los clientes con crédito. */
    public int evaluarTodos() {
        List<Map<String, Object>> clientes = jdbc.queryForList(
                "SELECT empresa_id, tercero_id FROM tercero_credito", new MapSqlParameterSource());
        int total = 0;
        for (Map<String, Object> c : clientes) {
            try {
                total += self.evaluarCliente(((Number) c.get("tercero_id")).longValue(),
                        ((Number) c.get("empresa_id")).intValue(), "PERIODICO", true);
            } catch (Exception e) {
                log.warn("No se pudo evaluar el crédito del tercero {}: {}", c.get("tercero_id"), e.getMessage());
            }
        }
        return total;
    }

    private boolean aplicar(ReglaCreditoEntity regla, Map<String, Object> accion, TerceroCreditoEntity credito) {
        BigDecimal cupoAnterior = credito.getCupoCreditoActual();
        String motivo = "Regla automática: " + regla.getNombre();
        switch (regla.getTipo()) {
            case "AUMENTO_CUPO" -> {
                BigDecimal nuevo = aumentar(cupoAnterior, accion);
                if (nuevo.compareTo(cupoAnterior) <= 0) return false;
                credito.setCupoCreditoActual(nuevo);
                creditoRepo.save(credito);
                historial(credito, "AUMENTO_CUPO", cupoAnterior, nuevo, motivo, regla.getId());
            }
            case "REDUCCION_CUPO" -> {
                BigDecimal nuevo = reducir(cupoAnterior, accion);
                if (nuevo.compareTo(cupoAnterior) >= 0) return false;
                credito.setCupoCreditoActual(nuevo);
                creditoRepo.save(credito);
                historial(credito, "REDUCCION_CUPO", cupoAnterior, nuevo, motivo, regla.getId());
            }
            case "SUSPENSION" -> {
                if ("SUSPENDIDO".equals(credito.getEstadoCredito()) || "BLOQUEADO".equals(credito.getEstadoCredito())) return false;
                credito.setEstadoCredito("SUSPENDIDO");
                creditoRepo.save(credito);
                historial(credito, "SUSPENSION", null, null, motivo, regla.getId());
            }
            case "BLOQUEO" -> {
                if ("BLOQUEADO".equals(credito.getEstadoCredito())) return false;
                credito.setEstadoCredito("BLOQUEADO");
                creditoRepo.save(credito);
                historial(credito, "BLOQUEO", null, null, motivo, regla.getId());
            }
            case "ALERTA" -> historial(credito, "ALERTA", null, null,
                    motivo + (accion.get("mensaje") != null ? " — " + accion.get("mensaje") : ""), regla.getId());
            default -> {
                return false;
            }
        }
        return true;
    }

    private Metricas metricas(TerceroCreditoEntity c, Integer empresaId) {
        Long terceroId = c.getTercero().getId();
        BigDecimal saldo = carteraQuery.saldoCarteraTercero(terceroId, empresaId);
        BigDecimal cupo = c.getCupoCreditoActual() != null ? c.getCupoCreditoActual() : BigDecimal.ZERO;
        int uso = cupo.signum() > 0 ? saldo.multiply(BigDecimal.valueOf(100)).divide(cupo, 0, RoundingMode.HALF_UP).intValue() : 0;
        return new Metricas(
                c.getScoreCrediticio() != null ? c.getScoreCrediticio() : 500,
                c.getEstadoCredito(),
                carteraQuery.diasMoraMaxima(terceroId, empresaId),
                carteraQuery.pagosConsecutivosATiempo(terceroId, empresaId),
                carteraQuery.promesasIncumplidasRecientes(terceroId, empresaId),
                uso, cupo);
    }

    private boolean cumple(Map<String, Object> c, Metricas m) {
        if (entero(c, "score_minimo") != null && m.score() < entero(c, "score_minimo")) return false;
        if (entero(c, "score_maximo") != null && m.score() > entero(c, "score_maximo")) return false;
        if (entero(c, "sin_mora_dias") != null && m.diasMora() > entero(c, "sin_mora_dias")) return false;
        if (entero(c, "mora_mayor_dias") != null && m.diasMora() <= entero(c, "mora_mayor_dias")) return false;
        if (entero(c, "pagos_consecutivos_a_tiempo") != null && m.pagosATiempo() < entero(c, "pagos_consecutivos_a_tiempo")) return false;
        if (entero(c, "promesas_incumplidas_minimo") != null && m.promesasIncumplidas() < entero(c, "promesas_incumplidas_minimo")) return false;
        if (entero(c, "uso_cupo_minimo_pct") != null && m.usoCupoPct() < entero(c, "uso_cupo_minimo_pct")) return false;
        Object estado = c.get("estado_credito");
        if (estado != null && !String.valueOf(estado).equals(m.estado())) return false;
        return true;
    }

    private boolean enEspera(Long reglaId, Long terceroId, Integer dias) {
        if (dias == null || dias <= 0) return false;
        Integer n = jdbc.queryForObject("""
            SELECT COUNT(*) FROM historial_credito
            WHERE regla_id = :reglaId AND tercero_id = :terceroId
              AND created_at >= NOW() - (:dias * INTERVAL '1 day')
            """, new MapSqlParameterSource("reglaId", reglaId).addValue("terceroId", terceroId).addValue("dias", dias),
                Integer.class);
        return n != null && n > 0;
    }

    private static BigDecimal aumentar(BigDecimal cupo, Map<String, Object> accion) {
        BigDecimal pct = decimal(accion, "aumentar_pct");
        if (pct == null) return cupo;
        BigDecimal nuevo = cupo.add(cupo.multiply(pct).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
        BigDecimal max = decimal(accion, "cupo_maximo");
        return max != null ? nuevo.min(max.max(cupo)) : nuevo;
    }

    private static BigDecimal reducir(BigDecimal cupo, Map<String, Object> accion) {
        BigDecimal pct = decimal(accion, "reducir_pct");
        if (pct == null) return cupo;
        BigDecimal nuevo = cupo.subtract(cupo.multiply(pct).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP))
                .max(BigDecimal.ZERO);
        BigDecimal min = decimal(accion, "cupo_minimo");
        return min != null ? nuevo.max(min.min(cupo)) : nuevo;
    }

    private void historial(TerceroCreditoEntity c, String tipo, BigDecimal cupoAnt, BigDecimal cupoNuevo,
            String motivo, Long reglaId) {
        historialRepo.save(HistorialCreditoEntity.builder()
                .empresa(c.getEmpresa()).tercero(c.getTercero()).tipoEvento(tipo)
                .cupoAnterior(cupoAnt).cupoNuevo(cupoNuevo)
                .scoreAnterior(c.getScoreCrediticio()).scoreNuevo(c.getScoreCrediticio())
                .motivo(motivo).reglaId(reglaId).build());
    }

    // ── Validación y conversión ─────────────────────────────────────────
    private void validar(ReglaCreditoDto dto) {
        if (dto.getNombre() == null || dto.getNombre().isBlank())
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Ponle un nombre a la regla");
        if (!TIPOS.contains(dto.getTipo()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Elige qué hace la regla");
        if (!EVENTOS.contains(dto.getEvento()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Elige cuándo se revisa la regla");
        if (condicionesAMapa(dto.getCondiciones()).isEmpty())
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Agrega al menos una condición: sin condiciones la regla actuaría sobre todos los clientes");
        ReglaCreditoDto.Accion a = dto.getAccion() != null ? dto.getAccion() : new ReglaCreditoDto.Accion();
        if ("AUMENTO_CUPO".equals(dto.getTipo()) && !positivo(a.getAumentarPct()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Indica en qué porcentaje sube el cupo");
        if ("REDUCCION_CUPO".equals(dto.getTipo())
                && (!positivo(a.getReducirPct()) || a.getReducirPct().compareTo(BigDecimal.valueOf(100)) > 0))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Indica en qué porcentaje baja el cupo (1 a 100)");
        if (dto.getDiasEntreAplicaciones() != null && (dto.getDiasEntreAplicaciones() < 0 || dto.getDiasEntreAplicaciones() > 365))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Los días de espera van de 0 a 365");
    }

    private ReglaCreditoDto aDto(ReglaCreditoEntity r) {
        ReglaCreditoDto dto = new ReglaCreditoDto();
        dto.setId(r.getId());
        dto.setNombre(r.getNombre());
        dto.setDescripcion(r.getDescripcion());
        dto.setTipo(r.getTipo());
        dto.setEvento(r.getEvento());
        dto.setActivo(r.getActivo());
        dto.setOrden(r.getOrden());
        dto.setDiasEntreAplicaciones(r.getDiasEntreAplicaciones());
        Map<String, Object> c = mapa(r.getCondicionJson());
        ReglaCreditoDto.Condiciones cond = dto.getCondiciones();
        cond.setScoreMinimo(entero(c, "score_minimo"));
        cond.setScoreMaximo(entero(c, "score_maximo"));
        cond.setMoraMaximaDias(entero(c, "sin_mora_dias"));
        cond.setMoraMayorDias(entero(c, "mora_mayor_dias"));
        cond.setPagosConsecutivosATiempo(entero(c, "pagos_consecutivos_a_tiempo"));
        cond.setPromesasIncumplidasMinimo(entero(c, "promesas_incumplidas_minimo"));
        cond.setUsoCupoMinimoPct(entero(c, "uso_cupo_minimo_pct"));
        cond.setEstadoCredito(c.get("estado_credito") != null ? String.valueOf(c.get("estado_credito")) : null);
        Map<String, Object> a = mapa(r.getAccionJson());
        dto.getAccion().setAumentarPct(decimal(a, "aumentar_pct"));
        dto.getAccion().setCupoMaximo(decimal(a, "cupo_maximo"));
        dto.getAccion().setReducirPct(decimal(a, "reducir_pct"));
        dto.getAccion().setCupoMinimo(decimal(a, "cupo_minimo"));
        dto.getAccion().setMensaje(a.get("mensaje") != null ? String.valueOf(a.get("mensaje")) : null);
        return dto;
    }

    private static Map<String, Object> condicionesAMapa(ReglaCreditoDto.Condiciones c) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (c == null) return m;
        poner(m, "score_minimo", c.getScoreMinimo());
        poner(m, "score_maximo", c.getScoreMaximo());
        poner(m, "sin_mora_dias", c.getMoraMaximaDias());
        poner(m, "mora_mayor_dias", c.getMoraMayorDias());
        poner(m, "pagos_consecutivos_a_tiempo", c.getPagosConsecutivosATiempo());
        poner(m, "promesas_incumplidas_minimo", c.getPromesasIncumplidasMinimo());
        poner(m, "uso_cupo_minimo_pct", c.getUsoCupoMinimoPct());
        if (c.getEstadoCredito() != null && !c.getEstadoCredito().isBlank()) m.put("estado_credito", c.getEstadoCredito());
        return m;
    }

    private static Map<String, Object> accionAMapa(String tipo, ReglaCreditoDto.Accion a) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (a == null) return m;
        if ("AUMENTO_CUPO".equals(tipo)) {
            poner(m, "aumentar_pct", a.getAumentarPct());
            poner(m, "cupo_maximo", a.getCupoMaximo());
        } else if ("REDUCCION_CUPO".equals(tipo)) {
            poner(m, "reducir_pct", a.getReducirPct());
            poner(m, "cupo_minimo", a.getCupoMinimo());
        } else if ("ALERTA".equals(tipo) && a.getMensaje() != null && !a.getMensaje().isBlank()) {
            m.put("mensaje", a.getMensaje().trim());
        }
        return m;
    }

    private static void poner(Map<String, Object> m, String k, Object v) {
        if (v != null) m.put(k, v);
    }

    private static boolean positivo(BigDecimal v) {
        return v != null && v.signum() > 0;
    }

    private static Integer entero(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v instanceof Number n ? n.intValue() : v != null ? Integer.valueOf(String.valueOf(v)) : null;
    }

    private static BigDecimal decimal(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v == null ? null : new BigDecimal(String.valueOf(v));
    }

    private Map<String, Object> mapa(String json) {
        if (json == null || json.isBlank()) return new LinkedHashMap<>();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private String json(Map<String, Object> m) {
        try {
            return objectMapper.writeValueAsString(m);
        } catch (Exception e) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "No se pudo guardar la regla");
        }
    }

    private ReglaCreditoEntity buscar(Long id, Integer empresaId) {
        return reglaRepo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Regla no encontrada"));
    }

    private static String nombre(TerceroCreditoEntity c) {
        var t = c.getTercero();
        if (t.getRazonSocial() != null && !t.getRazonSocial().isBlank()) return t.getRazonSocial();
        return ((t.getNombres() != null ? t.getNombres() : "") + " " + (t.getApellidos() != null ? t.getApellidos() : "")).trim();
    }
}
