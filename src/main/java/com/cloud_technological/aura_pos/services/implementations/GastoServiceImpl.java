package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.compras.CreateGastoDto;
import com.cloud_technological.aura_pos.dto.compras.GastoDto;
import com.cloud_technological.aura_pos.dto.compras.GastoTableDto;
import com.cloud_technological.aura_pos.dto.cuentas_pagar.CreateCuentaPagarDto;
import com.cloud_technological.aura_pos.entity.GastoEntity;
import com.cloud_technological.aura_pos.entity.MovimientoCajaEntity;
import com.cloud_technological.aura_pos.entity.SucursalEntity;
import com.cloud_technological.aura_pos.entity.UsuarioEntity;
import com.cloud_technological.aura_pos.repositories.empresas.EmpresaJPARepository;
import com.cloud_technological.aura_pos.repositories.gastos.GastoJPARepository;
import com.cloud_technological.aura_pos.repositories.gastos.GastoQueryRepository;
import com.cloud_technological.aura_pos.repositories.sucursales.SucursalJPARepository;
import com.cloud_technological.aura_pos.repositories.users.UsuarioJPARepository;
import com.cloud_technological.aura_pos.services.OrigenFondosService;
import com.cloud_technological.aura_pos.services.TesoreriaService;
import com.cloud_technological.aura_pos.utils.Terceros;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.MediosPago;
import com.cloud_technological.aura_pos.utils.PageableDto;
import com.cloud_technological.aura_pos.services.GastoService;

@Service
public class GastoServiceImpl implements GastoService {

    @Autowired private org.springframework.context.ApplicationEventPublisher eventPublisher;
    @Autowired private com.cloud_technological.aura_pos.repositories.terceros.TerceroJPARepository terceroJPARepository;
    @Autowired private com.cloud_technological.aura_pos.repositories.contabilidad.PlanCuentaJPARepository planCuentaJPARepository;
    @Autowired private GastoJPARepository gastoJPARepository;
    @Autowired private GastoQueryRepository gastoQueryRepository;
    @Autowired private EmpresaJPARepository empresaJPARepository;
    @Autowired private SucursalJPARepository sucursalJPARepository;
    @Autowired private UsuarioJPARepository usuarioJPARepository;
    @Autowired private com.cloud_technological.aura_pos.services.OrigenFondosService origenFondosService;
    @Autowired private com.cloud_technological.aura_pos.repositories.movimiento_caja.MovimientoCajaJPARepository movimientoCajaJPARepository;
    @Autowired private com.cloud_technological.aura_pos.services.TesoreriaService tesoreriaService;
    @Autowired private com.cloud_technological.aura_pos.services.CuentaPagarService cuentaPagarService;
    @Autowired private com.cloud_technological.aura_pos.services.ComprobanteCajaService comprobanteCajaService;

    @Override
    public GastoDto obtener(Long id, Integer empresaId) {
        GastoEntity gasto = gastoJPARepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Gasto no encontrado"));
        return toDto(gasto);
    }

    @Override
    @Transactional
    public GastoDto crear(CreateGastoDto dto, Integer empresaId, Long usuarioId) {
        var empresa = empresaJPARepository.findById(empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Empresa no encontrada"));

        SucursalEntity sucursal = sucursalJPARepository.findByIdAndEmpresaId(dto.getSucursalId(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Sucursal no encontrada"));

        UsuarioEntity usuario = usuarioJPARepository.findById(usuarioId.intValue())
                .orElse(null);

        GastoEntity gasto = new GastoEntity();
        gasto.setEmpresa(empresa);
        gasto.setSucursal(sucursal);
        gasto.setUsuario(usuario);
        gasto.setCategoria(dto.getCategoria());
        gasto.setDescripcion(dto.getDescripcion());
        gasto.setMonto(dto.getMonto());
        gasto.setFecha(dto.getFecha() != null ? dto.getFecha() : LocalDate.now());
        gasto.setDeducible(dto.getDeducible());
        gasto.setEstado("ACTIVO");
        gasto.setCreatedAt(LocalDateTime.now());
        mapCamposTributarios(dto, gasto);

        // De dónde sale la plata. Antes no se preguntaba: el asiento acreditaba
        // CAJA a ciegas, así que un gasto pagado por transferencia dejaba la
        // caja contable en negativo con el banco intacto, y como nunca se
        // registraba movimiento_caja tampoco aparecía en el cierre del cajero.
        boolean esCredito = MediosPago.CREDITO.equalsIgnoreCase(dto.getFormaPago());
        OrigenFondosService.OrigenFondos origen = null;
        if (!esCredito) {
            origen = origenFondosService.resolver(empresaId,
                    new OrigenFondosService.Solicitud(
                            dto.getMetodoPago(),
                            null,
                            dto.getCuentaBancariaId(),
                            dto.getCuentaPagoId(),
                            dto.getSucursalId(),
                            "gasto"));
            gasto.setCuentaPagoId(origen.cuentaContableId());
        }

        gasto = gastoJPARepository.save(gasto);

        if (esCredito) {
            // El gasto a crédito no saca plata hoy: deja una obligación con el
            // tercero, igual que una compra a crédito. Sin egreso no hay
            // comprobante: se emitirá cuando se abone la cuenta por pagar.
            registrarCuentaPorPagar(dto, gasto, empresaId, usuarioId);
        } else {
            registrarEgresoDeCaja(origen, gasto, empresaId);
            descontarSaldoBancario(dto, gasto, empresaId, usuarioId);
            emitirComprobanteEgreso(dto, gasto, empresaId, usuarioId);
        }

        // Asiento contable del gasto tras el commit.
        eventPublisher.publishEvent(
                new com.cloud_technological.aura_pos.event.OperacionContabilizableEvent(
                        "GASTO", gasto.getId(), empresaId, usuarioId != null ? usuarioId.intValue() : null));

        return toDto(gasto);
    }

    /** El efectivo del gasto sale de una caja y tiene que verse en su cierre. */
    private void registrarEgresoDeCaja(OrigenFondosService.OrigenFondos origen,
            GastoEntity gasto, Integer empresaId) {
        if (origen == null || !origen.generaMovimientoCaja()) {
            return;
        }
        MovimientoCajaEntity egreso = MovimientoCajaEntity.builder()
                .turnoCaja(origen.turno())
                .usuario(gasto.getUsuario())
                .tipo("EGRESO")
                .concepto("Gasto #" + gasto.getId()
                        + (gasto.getCategoria() != null ? " — " + gasto.getCategoria() : ""))
                .monto(gasto.getMonto())
                .metodoPago(gasto.getMetodoPago())
                .build();
        movimientoCajaJPARepository.save(egreso);
    }

    private void descontarSaldoBancario(CreateGastoDto dto, GastoEntity gasto,
            Integer empresaId, Long usuarioId) {
        String beneficiario = dto.getTerceroId() != null
                ? terceroJPARepository.findByIdAndEmpresaId(dto.getTerceroId(), empresaId)
                        .map(Terceros::nombreVisible).orElse(null)
                : null;
        String referencia = dto.getNumeroDocSoporte() != null && !dto.getNumeroDocSoporte().isBlank()
                ? dto.getNumeroDocSoporte().trim()
                : "GASTO-" + gasto.getId();

        tesoreriaService.registrarMovimientoDeDocumento(empresaId,
                usuarioId != null ? usuarioId.intValue() : null,
                new TesoreriaService.MovimientoDocumento(
                        dto.getCuentaBancariaId(), true, gasto.getMonto(),
                        "Gasto #" + gasto.getId()
                                + (gasto.getDescripcion() != null ? " — " + gasto.getDescripcion() : ""),
                        beneficiario, referencia, gasto.getCategoria()));
    }

    /**
     * Todo dinero que sale necesita su soporte, salga de la caja, del banco o de
     * una cuenta contable. Se sincroniza (no se genera a secas) para que editar
     * el gasto corrija el comprobante en vez de emitir uno nuevo.
     */
    private void emitirComprobanteEgreso(CreateGastoDto dto, GastoEntity gasto,
            Integer empresaId, Long usuarioId) {
        String beneficiario = dto.getTerceroId() != null
                ? terceroJPARepository.findByIdAndEmpresaId(dto.getTerceroId(), empresaId)
                        .map(Terceros::nombreVisible).orElse(null)
                : null;

        comprobanteCajaService.sincronizarDeDocumento(empresaId,
                usuarioId != null ? usuarioId.intValue() : null,
                "EGRESO",
                "Gasto #" + gasto.getId()
                        + (gasto.getCategoria() != null ? " — " + gasto.getCategoria() : ""),
                gasto.getMonto(),
                gasto.getMetodoPago(),
                beneficiario,
                "GASTO", gasto.getId(), null);
    }

    private void registrarCuentaPorPagar(CreateGastoDto dto, GastoEntity gasto,
            Integer empresaId, Long usuarioId) {
        if (dto.getTerceroId() == null) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Un gasto a crédito necesita el tercero al que se le queda debiendo");
        }
        CreateCuentaPagarDto cxp = new CreateCuentaPagarDto();
        cxp.setProveedorId(dto.getTerceroId());
        cxp.setTotalDeuda(gasto.getMonto());
        cxp.setFechaEmision(gasto.getFecha().atStartOfDay());
        cxp.setFechaVencimiento(dto.getFechaVencimiento() != null
                ? dto.getFechaVencimiento()
                : gasto.getFecha().plusDays(30).atStartOfDay());
        cxp.setObservaciones("Gasto #" + gasto.getId() + " - Gasto a crédito");
        cuentaPagarService.crear(cxp, empresaId, usuarioId);
    }

    @Override
    @Transactional
    public GastoDto actualizar(Long id, CreateGastoDto dto, Integer empresaId) {
        GastoEntity gasto = gastoJPARepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Gasto no encontrado"));

        SucursalEntity sucursal = sucursalJPARepository.findByIdAndEmpresaId(dto.getSucursalId(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Sucursal no encontrada"));

        gasto.setSucursal(sucursal);
        gasto.setCategoria(dto.getCategoria());
        gasto.setDescripcion(dto.getDescripcion());
        gasto.setMonto(dto.getMonto());
        if (dto.getFecha() != null) gasto.setFecha(dto.getFecha());
        gasto.setDeducible(dto.getDeducible());
        mapCamposTributarios(dto, gasto);

        gasto = gastoJPARepository.save(gasto);
        return toDto(gasto);
    }

    @Override
    @Transactional
    public void eliminar(Long id, Integer empresaId) {
        GastoEntity gasto = gastoJPARepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Gasto no encontrado"));
        gasto.setEstado("ELIMINADO");
        gastoJPARepository.save(gasto);

        // El soporte no puede seguir vigente si el gasto ya no existe.
        comprobanteCajaService.anularDeDocumento(empresaId, "GASTO", gasto.getId(),
                "Gasto eliminado");

        // Reversar el asiento del gasto tras el commit.
        eventPublisher.publishEvent(
                new com.cloud_technological.aura_pos.event.ContabilidadReversaEvent(
                        "GASTO", gasto.getId(), empresaId, null));
    }

    @Override
    public PageImpl<GastoTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        return gastoQueryRepository.listar(pageable, empresaId);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void mapCamposTributarios(CreateGastoDto dto, GastoEntity gasto) {
        // Origen de fondos (V142). El método se normaliza porque el cierre de
        // caja compara metodo_pago = 'EFECTIVO' exacto.
        gasto.setFormaPago(dto.getFormaPago() != null
                ? dto.getFormaPago().trim().toUpperCase() : "CONTADO");
        gasto.setMetodoPago(dto.getMetodoPago() != null
                ? MediosPago.normalizar(dto.getMetodoPago()) : MediosPago.EFECTIVO);
        gasto.setCuentaBancariaId(dto.getCuentaBancariaId());
        gasto.setTerceroId(dto.getTerceroId());
        gasto.setCuentaContableId(dto.getCuentaContableId());
        gasto.setCentroCostoId(dto.getCentroCostoId());
        gasto.setEsDiferido(Boolean.TRUE.equals(dto.getEsDiferido()));
        gasto.setMesesDiferido(dto.getMesesDiferido());
        gasto.setProyectoId(dto.getProyectoId());
        gasto.setFrenteId(dto.getFrenteId());
        gasto.setPeriodoContableId(dto.getPeriodoContableId());
        gasto.setBaseIva(nvl(dto.getBaseIva()));
        gasto.setTarifaIva(nvl(dto.getTarifaIva()));
        gasto.setValorIva(nvl(dto.getValorIva()));
        gasto.setBaseRetefuente(nvl(dto.getBaseRetefuente()));
        gasto.setTarifaRetefuente(nvl(dto.getTarifaRetefuente()));
        gasto.setValorRetefuente(nvl(dto.getValorRetefuente()));
        gasto.setBaseReteica(nvl(dto.getBaseReteica()));
        gasto.setTarifaReteica(nvl(dto.getTarifaReteica()));
        gasto.setValorReteica(nvl(dto.getValorReteica()));
        gasto.setTipoDocSoporte(dto.getTipoDocSoporte());
        gasto.setNumeroDocSoporte(dto.getNumeroDocSoporte());
    }

    private BigDecimal nvl(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private GastoDto toDto(GastoEntity g) {
        GastoDto dto = new GastoDto();
        dto.setId(g.getId());
        dto.setEmpresaId(g.getEmpresa() != null ? g.getEmpresa().getId() : null);
        dto.setSucursalId(g.getSucursal() != null ? g.getSucursal().getId().longValue() : null);
        dto.setSucursalNombre(g.getSucursal() != null ? g.getSucursal().getNombre() : null);
        dto.setUsuarioId(g.getUsuario() != null ? g.getUsuario().getId() : null);
        dto.setUsuarioNombre(g.getUsuario() != null ? g.getUsuario().getUsername() : null);
        dto.setCategoria(g.getCategoria());
        dto.setDescripcion(g.getDescripcion());
        dto.setMonto(g.getMonto());
        dto.setFecha(g.getFecha());
        dto.setDeducible(g.getDeducible());
        dto.setEstado(g.getEstado());
        dto.setCreatedAt(g.getCreatedAt());
        // Origen de fondos
        dto.setFormaPago(g.getFormaPago());
        dto.setMetodoPago(g.getMetodoPago());
        dto.setCuentaBancariaId(g.getCuentaBancariaId());
        dto.setCuentaPagoId(g.getCuentaPagoId());
        // Campos tributarios
        dto.setTerceroId(g.getTerceroId());
        dto.setCuentaContableId(g.getCuentaContableId());
        Integer empId = g.getEmpresa() != null ? g.getEmpresa().getId() : null;
        if (g.getTerceroId() != null && empId != null) {
            terceroJPARepository.findByIdAndEmpresaId(g.getTerceroId(), empId)
                    .ifPresent(t -> dto.setTerceroNombre(
                            t.getRazonSocial() != null && !t.getRazonSocial().isBlank()
                                    ? t.getRazonSocial()
                                    : ((t.getNombres() != null ? t.getNombres() : "") + " "
                                            + (t.getApellidos() != null ? t.getApellidos() : "")).trim()));
        }
        if (g.getCuentaContableId() != null && empId != null) {
            planCuentaJPARepository.findByIdAndEmpresaId(g.getCuentaContableId(), empId)
                    .ifPresent(c -> dto.setCuentaContableNombre(c.getCodigo() + " - " + c.getNombre()));
        }
        dto.setCentroCostoId(g.getCentroCostoId());
        dto.setPeriodoContableId(g.getPeriodoContableId());
        dto.setBaseIva(g.getBaseIva());
        dto.setTarifaIva(g.getTarifaIva());
        dto.setValorIva(g.getValorIva());
        dto.setBaseRetefuente(g.getBaseRetefuente());
        dto.setTarifaRetefuente(g.getTarifaRetefuente());
        dto.setValorRetefuente(g.getValorRetefuente());
        dto.setBaseReteica(g.getBaseReteica());
        dto.setTarifaReteica(g.getTarifaReteica());
        dto.setValorReteica(g.getValorReteica());
        dto.setTipoDocSoporte(g.getTipoDocSoporte());
        dto.setNumeroDocSoporte(g.getNumeroDocSoporte());
        return dto;
    }
}
