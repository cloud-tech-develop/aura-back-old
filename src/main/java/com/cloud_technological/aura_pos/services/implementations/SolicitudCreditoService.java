package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.cartera.credito.CreateSolicitudCreditoDto;
import com.cloud_technological.aura_pos.dto.cartera.credito.SolicitudCreditoDto;
import com.cloud_technological.aura_pos.entity.SolicitudAutorizacionCreditoEntity;
import com.cloud_technological.aura_pos.entity.TerceroCreditoEntity;
import com.cloud_technological.aura_pos.repositories.cartera.CarteraQueryRepository;
import com.cloud_technological.aura_pos.repositories.cartera.SolicitudAutorizacionJPARepository;
import com.cloud_technological.aura_pos.repositories.cartera.TerceroCreditoJPARepository;
import com.cloud_technological.aura_pos.repositories.empresas.EmpresaJPARepository;
import com.cloud_technological.aura_pos.repositories.terceros.TerceroJPARepository;
import com.cloud_technological.aura_pos.repositories.users.UsuarioJPARepository;
import com.cloud_technological.aura_pos.utils.GlobalException;

/**
 * Autorizaciones para vender por encima del cupo.
 *
 * <p>El cajero la pide desde el POS, un administrador la aprueba con vigencia
 * (por defecto 4 horas) y la venta a crédito la consume: una aprobación sirve
 * para una sola venta del cliente y por hasta el monto aprobado.
 */
@Service
public class SolicitudCreditoService {

    private static final int VIGENCIA_HORAS_DEFECTO = 4;

    @Autowired private SolicitudAutorizacionJPARepository repo;
    @Autowired private TerceroCreditoJPARepository creditoRepo;
    @Autowired private TerceroJPARepository terceroRepo;
    @Autowired private EmpresaJPARepository empresaRepo;
    @Autowired private UsuarioJPARepository usuarioRepo;
    @Autowired private CarteraQueryRepository carteraQuery;
    @Autowired private NamedParameterJdbcTemplate jdbc;
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;

    @Transactional
    public SolicitudCreditoDto crear(CreateSolicitudCreditoDto dto, Integer empresaId, Long usuarioId) {
        if (dto.getTerceroId() == null || dto.getMonto() == null || dto.getMonto().signum() <= 0)
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Indica el cliente y el monto de la venta a crédito");
        TerceroCreditoEntity credito = creditoRepo.findByTerceroIdAndEmpresaId(dto.getTerceroId(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "El cliente no tiene cupo de crédito configurado"));
        if ("BLOQUEADO".equals(credito.getEstadoCredito()) || "SUSPENDIDO".equals(credito.getEstadoCredito()))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "El crédito del cliente está " + credito.getEstadoCredito().toLowerCase()
                            + ": la autorización solo aplica cuando la venta pasa el cupo");
        int diasMora = carteraQuery.diasMoraMaxima(dto.getTerceroId(), empresaId);
        if (diasMora > credito.getDiasMoraTolerancia())
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "El cliente tiene " + diasMora + " días de mora: primero debe ponerse al día");

        BigDecimal saldo = carteraQuery.saldoCarteraTercero(dto.getTerceroId(), empresaId);
        BigDecimal disponible = credito.getCupoCreditoActual().subtract(saldo);
        BigDecimal excedente = dto.getMonto().subtract(disponible);
        if (excedente.signum() <= 0)
            throw new GlobalException(HttpStatus.BAD_REQUEST, "No hace falta autorización: la venta cabe en el cupo");

        // Una sola pendiente por cliente: si el cajero vuelve a pedir, se actualiza el monto.
        SolicitudAutorizacionCreditoEntity s = repo
                .findFirstByEmpresaIdAndTerceroIdAndEstadoOrderByCreatedAtDesc(empresaId, dto.getTerceroId(), "PENDIENTE")
                .orElseGet(() -> SolicitudAutorizacionCreditoEntity.builder()
                        .empresa(empresaRepo.getReferenceById(empresaId))
                        .tercero(terceroRepo.findByIdAndEmpresaId(dto.getTerceroId(), empresaId)
                                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Cliente no encontrado")))
                        .estado("PENDIENTE")
                        .build());
        s.setMontoSolicitado(dto.getMonto());
        s.setCupoDisponible(disponible);
        s.setExcedente(excedente);
        s.setSolicitadoPorId(usuarioId != null ? usuarioId.intValue() : null);
        s.setObservacion(dto.getObservacion() != null && !dto.getObservacion().isBlank()
                ? dto.getObservacion().trim().substring(0, Math.min(300, dto.getObservacion().trim().length())) : null);
        s = repo.saveAndFlush(s);
        return obtener(s.getId(), empresaId);
    }

    public List<SolicitudCreditoDto> listar(Integer empresaId, String estado) {
        vencer(empresaId);
        StringBuilder sql = new StringBuilder(SELECT).append(" WHERE s.empresa_id = :empresaId");
        MapSqlParameterSource p = new MapSqlParameterSource("empresaId", empresaId);
        if (estado != null && !estado.isBlank()) {
            sql.append(" AND s.estado = :estado");
            p.addValue("estado", estado.toUpperCase());
        }
        sql.append(" ORDER BY CASE WHEN s.estado = 'PENDIENTE' THEN 0 ELSE 1 END, s.created_at DESC LIMIT 200");
        return jdbc.query(sql.toString(), p, new BeanPropertyRowMapper<>(SolicitudCreditoDto.class));
    }

    public SolicitudCreditoDto obtener(Long id, Integer empresaId) {
        vencer(empresaId);
        List<SolicitudCreditoDto> r = jdbc.query(SELECT + " WHERE s.id = :id AND s.empresa_id = :empresaId",
                new MapSqlParameterSource("id", id).addValue("empresaId", empresaId),
                new BeanPropertyRowMapper<>(SolicitudCreditoDto.class));
        if (r.isEmpty()) throw new GlobalException(HttpStatus.NOT_FOUND, "Solicitud no encontrada");
        return r.get(0);
    }

    @Transactional
    public SolicitudCreditoDto aprobar(Long id, Integer vigenciaHoras, Integer empresaId, Long usuarioId) {
        SolicitudAutorizacionCreditoEntity s = pendiente(id, empresaId);
        int horas = vigenciaHoras != null ? Math.max(1, Math.min(vigenciaHoras, 72)) : VIGENCIA_HORAS_DEFECTO;
        s.setEstado("APROBADA");
        s.setAprobadoPor(usuarioRepo.findById(usuarioId.intValue()).orElse(null));
        s.setRespondidoAt(LocalDateTime.now());
        s.setVigenteHasta(LocalDateTime.now().plusHours(horas));
        repo.saveAndFlush(s);
        return obtener(id, empresaId);
    }

    @Transactional
    public SolicitudCreditoDto rechazar(Long id, String motivo, Integer empresaId, Long usuarioId) {
        if (motivo == null || motivo.isBlank())
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Indica por qué se rechaza: el cajero lo verá");
        SolicitudAutorizacionCreditoEntity s = pendiente(id, empresaId);
        s.setEstado("RECHAZADA");
        s.setMotivoRechazo(motivo.trim());
        s.setAprobadoPor(usuarioRepo.findById(usuarioId.intValue()).orElse(null));
        s.setRespondidoAt(LocalDateTime.now());
        repo.saveAndFlush(s);
        return obtener(id, empresaId);
    }

    /** Aprobación vigente, sin usar, que cubra el monto de la venta. */
    public Optional<SolicitudAutorizacionCreditoEntity> aprobadaVigente(Long terceroId, BigDecimal monto, Integer empresaId) {
        return repo.findFirstByEmpresaIdAndTerceroIdAndEstadoOrderByCreatedAtDesc(empresaId, terceroId, "APROBADA")
                .filter(s -> s.getVigenteHasta() != null && s.getVigenteHasta().isAfter(LocalDateTime.now()))
                .filter(s -> s.getMontoSolicitado().compareTo(monto) >= 0);
    }

    public Optional<SolicitudAutorizacionCreditoEntity> pendienteDe(Long terceroId, Integer empresaId) {
        return repo.findFirstByEmpresaIdAndTerceroIdAndEstadoOrderByCreatedAtDesc(empresaId, terceroId, "PENDIENTE");
    }

    @Transactional
    public void consumir(Long solicitudId, Long ventaId) {
        repo.findById(solicitudId).ifPresent(s -> {
            s.setEstado("USADA");
            s.setUsadaAt(LocalDateTime.now());
            s.setVenta(entityManager.getReference(com.cloud_technological.aura_pos.entity.VentaEntity.class, ventaId));
            repo.save(s);
        });
    }

    public int pendientes(Integer empresaId) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM solicitud_autorizacion_credito WHERE empresa_id = :empresaId AND estado = 'PENDIENTE'",
                new MapSqlParameterSource("empresaId", empresaId), Integer.class);
        return n != null ? n : 0;
    }

    private void vencer(Integer empresaId) {
        jdbc.update("""
            UPDATE solicitud_autorizacion_credito SET estado = 'VENCIDA', updated_at = NOW()
            WHERE empresa_id = :empresaId AND estado = 'APROBADA' AND vigente_hasta < NOW()
            """, new MapSqlParameterSource("empresaId", empresaId));
    }

    private SolicitudAutorizacionCreditoEntity pendiente(Long id, Integer empresaId) {
        SolicitudAutorizacionCreditoEntity s = repo.findById(id)
                .filter(x -> x.getEmpresa() != null && empresaId.equals(x.getEmpresa().getId()))
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Solicitud no encontrada"));
        if (!"PENDIENTE".equals(s.getEstado()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La solicitud ya fue respondida");
        return s;
    }

    private static final String SELECT = """
        SELECT s.id, s.tercero_id,
               COALESCE(NULLIF(t.razon_social, ''), TRIM(CONCAT(t.nombres, ' ', t.apellidos))) AS tercero_nombre, t.numero_documento AS tercero_documento,
               s.monto_solicitado, s.cupo_disponible, s.excedente,
               tc.cupo_credito_actual AS cupo_actual,
               (SELECT COALESCE(SUM(c.saldo_pendiente), 0) FROM cuentas_cobrar c
                 WHERE c.empresa_id = s.empresa_id AND c.tercero_id = s.tercero_id
                   AND c.deleted_at IS NULL AND c.estado NOT IN ('pagada','anulada')) AS saldo_actual,
               (SELECT COALESCE(MAX(CURRENT_DATE - c.fecha_vencimiento::date), 0) FROM cuentas_cobrar c
                 WHERE c.empresa_id = s.empresa_id AND c.tercero_id = s.tercero_id AND c.deleted_at IS NULL
                   AND c.estado NOT IN ('pagada','anulada') AND c.fecha_vencimiento::date < CURRENT_DATE)::INT AS dias_mora,
               tc.score_crediticio,
               s.estado, s.observacion, us.username AS solicitado_por, ua.username AS aprobado_por,
               s.motivo_rechazo, s.created_at, s.respondido_at, s.vigente_hasta, s.usada_at,
               s.venta_id,
               CASE WHEN v.id IS NULL THEN NULL ELSE CONCAT(COALESCE(v.prefijo, ''), v.consecutivo) END AS numero_venta
        FROM solicitud_autorizacion_credito s
        JOIN tercero t ON t.id = s.tercero_id
        LEFT JOIN tercero_credito tc ON tc.tercero_id = s.tercero_id AND tc.empresa_id = s.empresa_id
        LEFT JOIN usuario us ON us.id = s.solicitado_por_id
        LEFT JOIN usuario ua ON ua.id = s.aprobado_por_id
        LEFT JOIN venta v ON v.id = s.venta_id
        """;
}
