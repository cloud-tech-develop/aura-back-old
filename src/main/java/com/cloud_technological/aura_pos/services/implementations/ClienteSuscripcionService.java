package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.super_admin.ClienteDto;
import com.cloud_technological.aura_pos.dto.super_admin.ClientesResumenDto;
import com.cloud_technological.aura_pos.dto.super_admin.GuardarSuscripcionDto;
import com.cloud_technological.aura_pos.dto.super_admin.RegistrarPagoDto;
import com.cloud_technological.aura_pos.dto.super_admin.SuscripcionPagoDto;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.EmpresaSuscripcionEntity;
import com.cloud_technological.aura_pos.entity.SuscripcionPagoEntity;
import com.cloud_technological.aura_pos.repositories.empresas.EmpresaJPARepository;
import com.cloud_technological.aura_pos.repositories.super_admin.EmpresaSuscripcionJPARepository;
import com.cloud_technological.aura_pos.repositories.super_admin.SuscripcionPagoJPARepository;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

/** Gestión de clientes/membresías desde el platform (super admin). */
@Service
public class ClienteSuscripcionService {

    @Autowired private EmpresaJPARepository empresaRepo;
    @Autowired private EmpresaSuscripcionJPARepository suscripcionRepo;
    @Autowired private SuscripcionPagoJPARepository pagoRepo;
    @Autowired private SecurityUtils securityUtils;

    // ── Listado de clientes ───────────────────────────────────────────────────
    public List<ClienteDto> listar() {
        Map<Integer, EmpresaSuscripcionEntity> porEmpresa = suscripcionRepo.findByDeletedAtIsNull().stream()
                .collect(Collectors.toMap(EmpresaSuscripcionEntity::getEmpresaId, s -> s, (a, b) -> a));

        Map<Integer, List<SuscripcionPagoEntity>> pagosPorEmpresa = pagoRepo.findByDeletedAtIsNull().stream()
                .collect(Collectors.groupingBy(SuscripcionPagoEntity::getEmpresaId));

        return empresaRepo.findAll().stream()
                .sorted(Comparator.comparing(EmpresaEntity::getRazonSocial, Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(e -> toClienteDto(e, porEmpresa.get(e.getId()), pagosPorEmpresa.get(e.getId())))
                .collect(Collectors.toList());
    }

    private ClienteDto toClienteDto(EmpresaEntity e, EmpresaSuscripcionEntity s, List<SuscripcionPagoEntity> pagos) {
        ClienteDto d = new ClienteDto();
        d.setEmpresaId(e.getId());
        d.setRazonSocial(e.getRazonSocial());
        d.setNombreComercial(e.getNombreComercial());
        d.setNit(e.getNit());
        d.setActiva(e.getActiva());

        if (s == null) {
            d.setTieneSuscripcion(false);
            d.setEstadoEfectivo("SIN_MEMBRESIA");
            return d;
        }

        d.setTieneSuscripcion(true);
        d.setSuscripcionId(s.getId());
        d.setTipoPlan(s.getTipoPlan());
        d.setEstado(s.getEstado());
        d.setValor(s.getValor());
        d.setMoneda(s.getMoneda());
        d.setFechaInicio(s.getFechaInicio());
        d.setFechaProximoPago(s.getFechaProximoPago());
        d.setContactoNombre(s.getContactoNombre());
        d.setContactoEmail(s.getContactoEmail());
        d.setContactoTelefono(s.getContactoTelefono());

        boolean vencida = esVencida(s);
        d.setVencida(vencida);
        d.setEstadoEfectivo(vencida ? "VENCIDA" : s.getEstado());
        if (s.getFechaProximoPago() != null) {
            d.setDiasParaVencer((int) ChronoUnit.DAYS.between(LocalDate.now(), s.getFechaProximoPago()));
        }

        if (pagos != null && !pagos.isEmpty()) {
            d.setTotalPagado(pagos.stream().map(SuscripcionPagoEntity::getMonto)
                    .filter(m -> m != null).reduce(BigDecimal.ZERO, BigDecimal::add));
            d.setUltimoPago(pagos.stream().map(SuscripcionPagoEntity::getFechaPago)
                    .filter(f -> f != null).max(Comparator.naturalOrder()).orElse(null));
        } else {
            d.setTotalPagado(BigDecimal.ZERO);
        }
        return d;
    }

    /** Vencida = mensual, activa, con próximo pago en el pasado. */
    private boolean esVencida(EmpresaSuscripcionEntity s) {
        return "MENSUAL".equals(s.getTipoPlan())
                && "ACTIVA".equals(s.getEstado())
                && s.getFechaProximoPago() != null
                && s.getFechaProximoPago().isBefore(LocalDate.now());
    }

    // ── KPIs ──────────────────────────────────────────────────────────────────
    public ClientesResumenDto resumen() {
        List<EmpresaSuscripcionEntity> subs = suscripcionRepo.findByDeletedAtIsNull();
        long totalEmpresas = empresaRepo.count();

        ClientesResumenDto r = new ClientesResumenDto();
        r.setTotalClientes(subs.size());
        r.setSinMembresia(Math.max(0, totalEmpresas - subs.size()));
        r.setActivos(subs.stream().filter(s -> "ACTIVA".equals(s.getEstado())).count());
        r.setEnPrueba(subs.stream().filter(s -> "PRUEBA".equals(s.getEstado())).count());
        r.setSuspendidos(subs.stream().filter(s -> "SUSPENDIDA".equals(s.getEstado())).count());
        r.setCancelados(subs.stream().filter(s -> "CANCELADA".equals(s.getEstado())).count());
        r.setMensuales(subs.stream().filter(s -> "MENSUAL".equals(s.getTipoPlan())).count());
        r.setUnicos(subs.stream().filter(s -> "UNICO".equals(s.getTipoPlan())).count());
        r.setVencidos(subs.stream().filter(this::esVencida).count());

        r.setMrr(subs.stream()
                .filter(s -> "MENSUAL".equals(s.getTipoPlan()) && "ACTIVA".equals(s.getEstado()))
                .map(s -> s.getValor() != null ? s.getValor() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        LocalDate hoy = LocalDate.now();
        LocalDate desde = hoy.withDayOfMonth(1);
        r.setRecaudadoMesActual(pagoRepo.findByDeletedAtIsNull().stream()
                .filter(p -> p.getFechaPago() != null && !p.getFechaPago().isBefore(desde) && !p.getFechaPago().isAfter(hoy))
                .map(p -> p.getMonto() != null ? p.getMonto() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        return r;
    }

    // ── Upsert de la membresía ────────────────────────────────────────────────
    @Transactional
    public ClienteDto guardarSuscripcion(Integer empresaId, GuardarSuscripcionDto dto) {
        EmpresaEntity empresa = empresaRepo.findById(empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Empresa no encontrada"));
        Long usuarioId = securityUtils.getUsuarioId();

        EmpresaSuscripcionEntity s = suscripcionRepo.findByEmpresaIdAndDeletedAtIsNull(empresaId)
                .orElseGet(() -> {
                    EmpresaSuscripcionEntity nueva = new EmpresaSuscripcionEntity();
                    nueva.setEmpresaId(empresaId);
                    nueva.setCreatedBy(usuarioId);
                    return nueva;
                });

        s.setTipoPlan(dto.getTipoPlan());
        s.setEstado(dto.getEstado() != null ? dto.getEstado() : "ACTIVA");
        s.setValor(dto.getValor() != null ? dto.getValor() : BigDecimal.ZERO);
        s.setMoneda(dto.getMoneda() != null ? dto.getMoneda() : "COP");
        s.setFechaInicio(dto.getFechaInicio());
        // Pago único: no hay cobro recurrente.
        s.setFechaProximoPago("UNICO".equals(dto.getTipoPlan()) ? null : dto.getFechaProximoPago());
        s.setDiaCobro(dto.getDiaCobro());
        s.setContactoNombre(dto.getContactoNombre());
        s.setContactoEmail(dto.getContactoEmail());
        s.setContactoTelefono(dto.getContactoTelefono());
        s.setNotas(dto.getNotas());
        if (s.getId() != null) s.setUpdatedBy(usuarioId);

        suscripcionRepo.save(s);
        return toClienteDto(empresa, s, pagoRepo.findByEmpresaIdAndDeletedAtIsNullOrderByFechaPagoDesc(empresaId));
    }

    // ── Registrar pago ────────────────────────────────────────────────────────
    @Transactional
    public ClienteDto registrarPago(Integer empresaId, RegistrarPagoDto dto) {
        EmpresaEntity empresa = empresaRepo.findById(empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Empresa no encontrada"));
        EmpresaSuscripcionEntity s = suscripcionRepo.findByEmpresaIdAndDeletedAtIsNull(empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST,
                        "La empresa no tiene una membresía; créala antes de registrar pagos"));
        Long usuarioId = securityUtils.getUsuarioId();

        SuscripcionPagoEntity p = new SuscripcionPagoEntity();
        p.setEmpresaId(empresaId);
        p.setSuscripcionId(s.getId());
        p.setFechaPago(dto.getFechaPago());
        p.setMonto(dto.getMonto());
        p.setMetodo(dto.getMetodo());
        p.setPeriodoDesde(dto.getPeriodoDesde());
        p.setPeriodoHasta(dto.getPeriodoHasta());
        p.setReferencia(dto.getReferencia());
        p.setObservacion(dto.getObservacion());
        p.setCreatedBy(usuarioId);
        pagoRepo.save(p);

        // Avanzar próximo pago (solo mensual). Preferir periodo_hasta+1, si no +1 mes.
        if ("MENSUAL".equals(s.getTipoPlan()) && Boolean.TRUE.equals(dto.getAvanzarProximoPago())) {
            LocalDate base = dto.getPeriodoHasta() != null
                    ? dto.getPeriodoHasta().plusDays(1)
                    : (s.getFechaProximoPago() != null ? s.getFechaProximoPago() : dto.getFechaPago()).plusMonths(1);
            s.setFechaProximoPago(base);
            if (!"CANCELADA".equals(s.getEstado())) s.setEstado("ACTIVA");
            s.setUpdatedBy(usuarioId);
            suscripcionRepo.save(s);
        }

        return toClienteDto(empresa, s, pagoRepo.findByEmpresaIdAndDeletedAtIsNullOrderByFechaPagoDesc(empresaId));
    }

    public List<SuscripcionPagoDto> pagos(Integer empresaId) {
        return pagoRepo.findByEmpresaIdAndDeletedAtIsNullOrderByFechaPagoDesc(empresaId).stream()
                .map(p -> {
                    SuscripcionPagoDto d = new SuscripcionPagoDto();
                    d.setId(p.getId());
                    d.setFechaPago(p.getFechaPago());
                    d.setMonto(p.getMonto());
                    d.setMetodo(p.getMetodo());
                    d.setPeriodoDesde(p.getPeriodoDesde());
                    d.setPeriodoHasta(p.getPeriodoHasta());
                    d.setReferencia(p.getReferencia());
                    d.setObservacion(p.getObservacion());
                    return d;
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public void eliminarPago(Long pagoId) {
        SuscripcionPagoEntity p = pagoRepo.findById(pagoId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Pago no encontrado"));
        p.setDeletedAt(LocalDateTime.now());
        p.setDeletedBy(securityUtils.getUsuarioId());
        pagoRepo.save(p);
    }
}
