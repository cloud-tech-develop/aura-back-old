package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.dto.cartera.CarteraDashboardDto;
import com.cloud_technological.aura_pos.dto.cartera.ClienteCarteraDto;
import com.cloud_technological.aura_pos.dto.cartera.CreateGestionCobroDto;
import com.cloud_technological.aura_pos.dto.cartera.CreateTerceroCreditoDto;
import com.cloud_technological.aura_pos.dto.cartera.CuentaVencidaAlertaDto;
import com.cloud_technological.aura_pos.dto.cartera.EdadCarteraDto;
import com.cloud_technological.aura_pos.dto.cartera.ValidacionCreditoDto;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.GestionCobroEntity;
import com.cloud_technological.aura_pos.entity.HistorialCreditoEntity;
import com.cloud_technological.aura_pos.entity.ReglaCreditoEntity;
import com.cloud_technological.aura_pos.entity.TerceroCreditoEntity;
import com.cloud_technological.aura_pos.entity.TerceroEntity;
import com.cloud_technological.aura_pos.entity.UsuarioEntity;
import com.cloud_technological.aura_pos.repositories.cartera.CarteraQueryRepository;
import com.cloud_technological.aura_pos.repositories.cartera.GestionCobroJPARepository;
import com.cloud_technological.aura_pos.repositories.cartera.HistorialCreditoJPARepository;
import com.cloud_technological.aura_pos.repositories.cartera.ReglaCreditoJPARepository;
import com.cloud_technological.aura_pos.repositories.cartera.SolicitudAutorizacionJPARepository;
import com.cloud_technological.aura_pos.repositories.cartera.TerceroCreditoJPARepository;
import com.cloud_technological.aura_pos.repositories.cuentas_cobrar.CuentaCobrarJPARepository;
import com.cloud_technological.aura_pos.repositories.empresas.EmpresaJPARepository;
import com.cloud_technological.aura_pos.repositories.terceros.TerceroJPARepository;
import com.cloud_technological.aura_pos.repositories.users.UsuarioJPARepository;
import com.cloud_technological.aura_pos.services.CarteraService;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.transaction.Transactional;

@Service
public class CarteraServiceImpl implements CarteraService {

    @Autowired private CarteraQueryRepository queryRepo;
    @Autowired private TerceroCreditoJPARepository creditoRepo;
    @Autowired private HistorialCreditoJPARepository historialRepo;
    @Autowired private ReglaCreditoJPARepository reglaRepo;
    @Autowired private GestionCobroJPARepository gestionRepo;
    @Autowired private SolicitudAutorizacionJPARepository solicitudRepo;
    @Autowired private TerceroJPARepository terceroRepo;
    @Autowired private EmpresaJPARepository empresaRepo;
    @Autowired private UsuarioJPARepository usuarioRepo;
    @Autowired private CuentaCobrarJPARepository cuentaCobrarRepo;
    @Autowired private ObjectMapper objectMapper;

    // ─── Dashboard ────────────────────────────────────────────────────────────

    @Override
    public CarteraDashboardDto dashboard(Integer empresaId) {
        return queryRepo.dashboard(empresaId);
    }

    @Override
    public List<EdadCarteraDto> edadesCartera(Integer empresaId) {
        return queryRepo.edadesCartera(empresaId);
    }

    @Override
    public List<CuentaVencidaAlertaDto> alertasVencidas(Integer empresaId, int limit) {
        return queryRepo.alertasVencidas(empresaId, limit);
    }

    @Override
    public PageImpl<ClienteCarteraDto> listarClientes(Integer empresaId, int page, int rows, String search) {
        return queryRepo.listarClientes(empresaId, page, rows, search);
    }

    // ─── Cupo de crédito ──────────────────────────────────────────────────────

    @Override
    @Transactional
    public TerceroCreditoEntity abrirCredito(CreateTerceroCreditoDto dto, Integer empresaId, Long usuarioId) {
        if (creditoRepo.existsByTerceroIdAndEmpresaId(dto.getTerceroId(), empresaId))
            throw new GlobalException(HttpStatus.CONFLICT, "El cliente ya tiene un cupo de crédito configurado");

        EmpresaEntity empresa = empresaRepo.findById(empresaId)
            .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Empresa no encontrada"));
        TerceroEntity tercero = terceroRepo.findByIdAndEmpresaId(dto.getTerceroId(), empresaId)
            .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Cliente no encontrado"));

        TerceroCreditoEntity credito = TerceroCreditoEntity.builder()
            .empresa(empresa)
            .tercero(tercero)
            .cupoCreditoInicial(dto.getCupoCreditoInicial())
            .cupoCreditoActual(dto.getCupoCreditoInicial())
            .plazoDias(dto.getPlazoDias() != null ? dto.getPlazoDias() : 30)
            .estadoCredito(dto.getEstadoCredito() != null ? dto.getEstadoCredito() : "ACTIVO")
            .nivelRiesgo("BAJO")
            .scoreCrediticio(500)
            .requiereAutorizacion(dto.getRequiereAutorizacion() != null ? dto.getRequiereAutorizacion() : false)
            .diasMoraTolerancia(dto.getDiasMoraTolerancia() != null ? dto.getDiasMoraTolerancia() : 30)
            .fechaUltimoEstudio(LocalDateTime.now())
            .build();

        creditoRepo.save(credito);

        // Registrar en historial
        UsuarioEntity usuario = usuarioRepo.findById(usuarioId.intValue()).orElse(null);
        guardarHistorial(empresa, tercero, "APERTURA", null, dto.getCupoCreditoInicial(),
            null, 500, "Apertura de cupo: $" + dto.getCupoCreditoInicial(), usuario);

        return credito;
    }

    @Override
    @Transactional
    public TerceroCreditoEntity actualizarCredito(Long id, CreateTerceroCreditoDto dto, Integer empresaId, Long usuarioId) {
        TerceroCreditoEntity credito = creditoRepo.findById(id)
            .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Cupo de crédito no encontrado"));

        BigDecimal cupoAnterior = credito.getCupoCreditoActual();
        String estadoAnterior = credito.getEstadoCredito();

        if (dto.getCupoCreditoInicial() != null) {
            credito.setCupoCreditoInicial(dto.getCupoCreditoInicial());
            credito.setCupoCreditoActual(dto.getCupoCreditoInicial());
        }
        if (dto.getPlazoDias() != null) credito.setPlazoDias(dto.getPlazoDias());
        if (dto.getEstadoCredito() != null) credito.setEstadoCredito(dto.getEstadoCredito());
        if (dto.getRequiereAutorizacion() != null) credito.setRequiereAutorizacion(dto.getRequiereAutorizacion());
        if (dto.getDiasMoraTolerancia() != null) credito.setDiasMoraTolerancia(dto.getDiasMoraTolerancia());
        credito.setFechaUltimoEstudio(LocalDateTime.now());

        creditoRepo.save(credito);

        // Determinar tipo de evento
        String tipoEvento = "ESTUDIO";
        if (!cupoAnterior.equals(credito.getCupoCreditoActual())) {
            tipoEvento = credito.getCupoCreditoActual().compareTo(cupoAnterior) > 0 ? "AUMENTO_CUPO" : "REDUCCION_CUPO";
        } else if (!estadoAnterior.equals(credito.getEstadoCredito())) {
            tipoEvento = "BLOQUEADO".equals(credito.getEstadoCredito()) ? "BLOQUEO" : "DESBLOQUEO";
        }

        UsuarioEntity usuario = usuarioRepo.findById(usuarioId.intValue()).orElse(null);
        guardarHistorial(credito.getEmpresa(), credito.getTercero(), tipoEvento,
            cupoAnterior, credito.getCupoCreditoActual(), null, null,
            "Actualización manual de crédito", usuario);

        return credito;
    }

    @Override
    public TerceroCreditoEntity obtenerCreditoTercero(Long terceroId, Integer empresaId) {
        return creditoRepo.findByTerceroIdAndEmpresaId(terceroId, empresaId)
            .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "El cliente no tiene cupo de crédito configurado"));
    }

    // ─── Validación POS ───────────────────────────────────────────────────────

    @Override
    public ValidacionCreditoDto validarVenta(Long terceroId, BigDecimal monto, Integer empresaId) {
        ValidacionCreditoDto resultado = new ValidacionCreditoDto();
        resultado.setMontoSolicitado(monto);

        // Si no tiene cupo configurado, venta permitida sin restricciones de crédito
        TerceroCreditoEntity credito = creditoRepo.findByTerceroIdAndEmpresaId(terceroId, empresaId)
            .orElse(null);

        if (credito == null) {
            resultado.setPermitido(true);
            resultado.setRequiereAutorizacion(false);
            return resultado;
        }

        resultado.setCupoActual(credito.getCupoCreditoActual());
        resultado.setEstadoCredito(credito.getEstadoCredito());
        resultado.setDiasMoraTolerancia(credito.getDiasMoraTolerancia());

        // 1. Estado bloqueado o suspendido
        if ("BLOQUEADO".equals(credito.getEstadoCredito()) || "SUSPENDIDO".equals(credito.getEstadoCredito())) {
            resultado.setPermitido(false);
            resultado.setRequiereAutorizacion(false);
            resultado.setMotivoBloqueo("Crédito " + credito.getEstadoCredito().toLowerCase() + " para este cliente");
            return resultado;
        }

        BigDecimal saldoCartera = queryRepo.saldoCarteraTercero(terceroId, empresaId);
        int diasMora = queryRepo.diasMoraMaxima(terceroId, empresaId);
        resultado.setSaldoCartera(saldoCartera);
        resultado.setDiasMoraMaximo(diasMora);

        // 2. Mora mayor a la tolerancia
        if (diasMora > credito.getDiasMoraTolerancia()) {
            resultado.setPermitido(false);
            resultado.setRequiereAutorizacion(false);
            resultado.setMotivoBloqueo("Cliente con " + diasMora + " días en mora (límite: " + credito.getDiasMoraTolerancia() + " días)");
            return resultado;
        }

        // 3. Verificar cupo
        BigDecimal saldoDisponible = credito.getCupoCreditoActual().subtract(saldoCartera);
        resultado.setSaldoDisponible(saldoDisponible);

        if (monto.compareTo(saldoDisponible) > 0) {
            BigDecimal excedente = monto.subtract(saldoDisponible);
            resultado.setExcedente(excedente);

            if (credito.getRequiereAutorizacion()) {
                resultado.setPermitido(false);
                resultado.setRequiereAutorizacion(true);
                resultado.setMotivoBloqueo("Venta supera el cupo disponible en $" + excedente.setScale(0, RoundingMode.HALF_UP) + ". Requiere autorización.");
            } else {
                resultado.setPermitido(false);
                resultado.setRequiereAutorizacion(false);
                resultado.setMotivoBloqueo("Venta supera el cupo disponible en $" + excedente.setScale(0, RoundingMode.HALF_UP));
            }
            return resultado;
        }

        resultado.setPermitido(true);
        resultado.setRequiereAutorizacion(false);
        resultado.setExcedente(BigDecimal.ZERO);
        return resultado;
    }

    // ─── Motor de score ───────────────────────────────────────────────────────

    @Override
    @Transactional
    public void recalcularScore(Long terceroId, Integer empresaId) {
        TerceroCreditoEntity credito = creditoRepo.findByTerceroIdAndEmpresaId(terceroId, empresaId)
            .orElse(null);
        if (credito == null) return;

        BigDecimal saldoCartera = queryRepo.saldoCarteraTercero(terceroId, empresaId);
        int diasMora = queryRepo.diasMoraMaxima(terceroId, empresaId);
        int pagosATiempo = queryRepo.pagosConsecutivosATiempo(terceroId, empresaId);

        // ── Historial de pago (400 pts) ─────────────────────────────────────
        int ptsHistorial = Math.min(400, pagosATiempo * 50);

        // ── Utilización del cupo (250 pts) ──────────────────────────────────
        int ptsUtilizacion = 250;
        if (credito.getCupoCreditoActual().compareTo(BigDecimal.ZERO) > 0) {
            double utilizacion = saldoCartera.doubleValue() / credito.getCupoCreditoActual().doubleValue();
            if (utilizacion > 0.8)       ptsUtilizacion = 50;
            else if (utilizacion > 0.6)  ptsUtilizacion = 150;
            else if (utilizacion > 0.3)  ptsUtilizacion = 250; // óptimo
            else if (utilizacion > 0.1)  ptsUtilizacion = 200;
            else                         ptsUtilizacion = 100; // sin uso
        }

        // ── Mora actual (200 pts, penalización) ──────────────────────────────
        int ptsMora = Math.max(0, 200 - (diasMora * 5));

        // ── Antigüedad (150 pts) ─────────────────────────────────────────────
        int ptsAntiguedad = 75; // default 75 si no hay historial suficiente
        long eventosHistorial = historialRepo.countByTerceroIdAndEmpresaIdAndTipoEvento(
            terceroId, empresaId, "APERTURA");
        if (eventosHistorial > 0) ptsAntiguedad = Math.min(150, pagosATiempo * 10 + 50);

        int scoreAnterior = credito.getScoreCrediticio();
        int scoreNuevo = ptsHistorial + ptsUtilizacion + ptsMora + ptsAntiguedad;
        scoreNuevo = Math.max(0, Math.min(1000, scoreNuevo));

        credito.setScoreCrediticio(scoreNuevo);

        // Actualizar nivel de riesgo
        if (scoreNuevo >= 800)      credito.setNivelRiesgo("BAJO");
        else if (scoreNuevo >= 600) credito.setNivelRiesgo("MEDIO");
        else if (scoreNuevo >= 400) credito.setNivelRiesgo("ALTO");
        else                        credito.setNivelRiesgo("CRITICO");

        // Actualizar estado si hay mora severa
        if (diasMora > credito.getDiasMoraTolerancia() && !"BLOQUEADO".equals(credito.getEstadoCredito())) {
            credito.setEstadoCredito("SUSPENDIDO");
            guardarHistorial(credito.getEmpresa(), credito.getTercero(), "MORA_DETECTADA",
                null, null, scoreAnterior, scoreNuevo,
                "Mora de " + diasMora + " días detectada automáticamente. Crédito suspendido.", null);
        } else if (diasMora == 0 && "SUSPENDIDO".equals(credito.getEstadoCredito())) {
            credito.setEstadoCredito("ACTIVO");
            guardarHistorial(credito.getEmpresa(), credito.getTercero(), "NORMALIZACION",
                null, null, scoreAnterior, scoreNuevo,
                "Sin mora activa. Crédito normalizado automáticamente.", null);
        }

        creditoRepo.save(credito);
    }

    // ─── Motor de reglas automáticas ─────────────────────────────────────────

    @Override
    @Transactional
    public void evaluarReglasAutomaticas(Long terceroId, Integer empresaId, String evento) {
        TerceroCreditoEntity credito = creditoRepo.findByTerceroIdAndEmpresaId(terceroId, empresaId)
            .orElse(null);
        if (credito == null) return;

        List<ReglaCreditoEntity> reglas = reglaRepo.findByEmpresaIdAndActivoTrueAndEventoOrderByOrdenAsc(empresaId, evento);
        if (reglas.isEmpty()) return;

        int pagosATiempo = queryRepo.pagosConsecutivosATiempo(terceroId, empresaId);
        int diasMora = queryRepo.diasMoraMaxima(terceroId, empresaId);

        for (ReglaCreditoEntity regla : reglas) {
            try {
                Map<String, Object> condicion = objectMapper.readValue(
                    regla.getCondicionJson(), new TypeReference<Map<String, Object>>() {});
                Map<String, Object> accion = objectMapper.readValue(
                    regla.getAccionJson(), new TypeReference<Map<String, Object>>() {});

                if (!cumpleCondicion(condicion, credito, pagosATiempo, diasMora)) continue;

                aplicarAccion(regla, accion, credito, empresaId);

            } catch (Exception ignored) {
                // Si la regla tiene JSON inválido, la omitimos
            }
        }
    }

    private boolean cumpleCondicion(Map<String, Object> cond, TerceroCreditoEntity credito,
                                     int pagosATiempo, int diasMora) {
        if (cond.containsKey("pagos_consecutivos_a_tiempo")) {
            int requeridos = ((Number) cond.get("pagos_consecutivos_a_tiempo")).intValue();
            if (pagosATiempo < requeridos) return false;
        }
        if (cond.containsKey("score_minimo")) {
            int scoreMin = ((Number) cond.get("score_minimo")).intValue();
            if (credito.getScoreCrediticio() < scoreMin) return false;
        }
        if (cond.containsKey("sin_mora_dias")) {
            int sinMoraDias = ((Number) cond.get("sin_mora_dias")).intValue();
            if (diasMora > sinMoraDias) return false;
        }
        if (cond.containsKey("estado_credito")) {
            String estadoReq = (String) cond.get("estado_credito");
            if (!estadoReq.equals(credito.getEstadoCredito())) return false;
        }
        return true;
    }

    @Transactional
    private void aplicarAccion(ReglaCreditoEntity regla, Map<String, Object> accion,
                                TerceroCreditoEntity credito, Integer empresaId) {
        BigDecimal cupoAnterior = credito.getCupoCreditoActual();

        switch (regla.getTipo()) {
            case "AUMENTO_CUPO" -> {
                if (accion.containsKey("aumentar_pct")) {
                    double pct = ((Number) accion.get("aumentar_pct")).doubleValue();
                    BigDecimal incremento = cupoAnterior.multiply(BigDecimal.valueOf(pct / 100));
                    BigDecimal cupoNuevo = cupoAnterior.add(incremento);

                    if (accion.containsKey("cupo_maximo")) {
                        BigDecimal maximo = BigDecimal.valueOf(((Number) accion.get("cupo_maximo")).doubleValue());
                        cupoNuevo = cupoNuevo.min(maximo);
                    }

                    credito.setCupoCreditoActual(cupoNuevo);
                    creditoRepo.save(credito);
                    guardarHistorial(credito.getEmpresa(), credito.getTercero(), "AUMENTO_CUPO",
                        cupoAnterior, cupoNuevo, null, null,
                        "Motor automático — Regla: " + regla.getNombre(), null);
                }
            }
            case "REDUCCION_CUPO" -> {
                if (accion.containsKey("reducir_pct")) {
                    double pct = ((Number) accion.get("reducir_pct")).doubleValue();
                    BigDecimal reduccion = cupoAnterior.multiply(BigDecimal.valueOf(pct / 100));
                    BigDecimal cupoNuevo = cupoAnterior.subtract(reduccion).max(BigDecimal.ZERO);
                    credito.setCupoCreditoActual(cupoNuevo);
                    creditoRepo.save(credito);
                    guardarHistorial(credito.getEmpresa(), credito.getTercero(), "REDUCCION_CUPO",
                        cupoAnterior, cupoNuevo, null, null,
                        "Motor automático — Regla: " + regla.getNombre(), null);
                }
            }
            case "BLOQUEO" -> {
                credito.setEstadoCredito("BLOQUEADO");
                creditoRepo.save(credito);
                guardarHistorial(credito.getEmpresa(), credito.getTercero(), "BLOQUEO",
                    null, null, null, null,
                    "Motor automático — Regla: " + regla.getNombre(), null);
            }
        }
    }

    // ─── Gestión de cobros ────────────────────────────────────────────────────

    @Override
    @Transactional
    public void registrarGestion(CreateGestionCobroDto dto, Integer empresaId, Long usuarioId) {
        EmpresaEntity empresa = empresaRepo.findById(empresaId)
            .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Empresa no encontrada"));
        TerceroEntity tercero = terceroRepo.findByIdAndEmpresaId(dto.getTerceroId(), empresaId)
            .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Cliente no encontrado"));
        UsuarioEntity usuario = usuarioRepo.findById(usuarioId.intValue()).orElse(null);

        GestionCobroEntity gestion = GestionCobroEntity.builder()
            .empresa(empresa)
            .tercero(tercero)
            .cuentaCobrar(dto.getCuentaCobrarId() != null
                ? cuentaCobrarRepo.findById(dto.getCuentaCobrarId()).orElse(null)
                : null)
            .tipoGestion(dto.getTipoGestion())
            .resultado(dto.getResultado())
            .nota(dto.getNota())
            .fechaPromesaPago(dto.getFechaPromesaPago())
            .montoPrometido(dto.getMontoPrometido())
            .usuario(usuario)
            .build();

        gestionRepo.save(gestion);
    }

    // ─── Solicitudes de autorización ─────────────────────────────────────────

    @Override
    @Transactional
    public void aprobarSolicitud(Long solicitudId, Integer empresaId, Long usuarioId) {
        var solicitud = solicitudRepo.findById(solicitudId)
            .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Solicitud no encontrada"));
        if (!"PENDIENTE".equals(solicitud.getEstado()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La solicitud ya fue procesada");

        UsuarioEntity usuario = usuarioRepo.findById(usuarioId.intValue()).orElse(null);
        solicitud.setEstado("APROBADA");
        solicitud.setAprobadoPor(usuario);
        solicitudRepo.save(solicitud);
    }

    @Override
    @Transactional
    public void rechazarSolicitud(Long solicitudId, String motivo, Integer empresaId, Long usuarioId) {
        var solicitud = solicitudRepo.findById(solicitudId)
            .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Solicitud no encontrada"));
        if (!"PENDIENTE".equals(solicitud.getEstado()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La solicitud ya fue procesada");

        solicitud.setEstado("RECHAZADA");
        solicitud.setMotivoRechazo(motivo);
        solicitudRepo.save(solicitud);
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private void guardarHistorial(EmpresaEntity empresa, TerceroEntity tercero,
                                   String tipo, BigDecimal cupoAnt, BigDecimal cupoNuevo,
                                   Integer scoreAnt, Integer scoreNuevo,
                                   String motivo, UsuarioEntity usuario) {
        historialRepo.save(HistorialCreditoEntity.builder()
            .empresa(empresa)
            .tercero(tercero)
            .tipoEvento(tipo)
            .cupoAnterior(cupoAnt)
            .cupoNuevo(cupoNuevo)
            .scoreAnterior(scoreAnt)
            .scoreNuevo(scoreNuevo)
            .motivo(motivo)
            .usuario(usuario)
            .build());
    }
}
