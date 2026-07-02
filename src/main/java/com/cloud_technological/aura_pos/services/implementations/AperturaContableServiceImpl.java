package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.cloud_technological.aura_pos.dto.contabilidad.AsientoContableTableDto;
import com.cloud_technological.aura_pos.dto.contabilidad.CreateSaldosInicialesDto;
import com.cloud_technological.aura_pos.dto.contabilidad.SaldoInicialLineaDto;
import com.cloud_technological.aura_pos.entity.AsientoContableEntity;
import com.cloud_technological.aura_pos.entity.AsientoDetalleEntity;
import com.cloud_technological.aura_pos.entity.ConceptoContable;
import com.cloud_technological.aura_pos.entity.PeriodoContableEntity;
import com.cloud_technological.aura_pos.entity.CuentaBancariaEntity;
import com.cloud_technological.aura_pos.entity.PlanCuentaEntity;
import com.cloud_technological.aura_pos.repositories.contabilidad.AsientoContableJPARepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.AsientoContableQueryRepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.PlanCuentaJPARepository;
import com.cloud_technological.aura_pos.repositories.periodo_contable.PeriodoContableJPARepository;
import com.cloud_technological.aura_pos.repositories.tesoreria.CuentaBancariaJPARepository;
import com.cloud_technological.aura_pos.services.AperturaContableService;
import com.cloud_technological.aura_pos.services.ConfiguracionContableService;

@Service
public class AperturaContableServiceImpl implements AperturaContableService {

    private static final String TIPO_ORIGEN = "APERTURA";
    private static final String PREFIX = "AP";

    @Autowired private AsientoContableJPARepository asientoRepo;
    @Autowired private AsientoContableQueryRepository queryRepo;
    @Autowired private PeriodoContableJPARepository periodoRepo;
    @Autowired private PlanCuentaJPARepository planRepo;
    @Autowired private CuentaBancariaJPARepository cuentaBancariaRepo;
    @Autowired private ConfiguracionContableService config;

    @Override
    public AsientoContableTableDto obtener(Integer empresaId) {
        return asientoRepo.findFirstByEmpresaIdAndTipoOrigen(empresaId, TIPO_ORIGEN)
                .map(this::toDto)
                .orElse(null);
    }

    @Override
    public List<SaldoInicialLineaDto> sugerirDesdeBancos(Integer empresaId) {
        List<SaldoInicialLineaDto> lineas = new ArrayList<>();
        for (CuentaBancariaEntity cb : cuentaBancariaRepo.findByEmpresaIdOrderByNombreAsc(empresaId)) {
            if (!Boolean.TRUE.equals(cb.getActiva())) continue;
            if (cb.getCuentaContableId() == null) continue;      // sin cuenta contable: no se puede mapear
            // Se usa el SALDO ACTUAL (foto real de hoy), no el inicial: el saldo cambia
            // con pagos de ventas, préstamos, etc., y la apertura debe reflejar la realidad.
            BigDecimal saldo = nz(cb.getSaldoActual());
            if (saldo.signum() == 0) continue;                    // sin saldo: nada que aportar
            SaldoInicialLineaDto linea = new SaldoInicialLineaDto();
            linea.setCuentaId(cb.getCuentaContableId());
            // Los bancos son activo (naturaleza débito): saldo positivo → débito;
            // negativo (sobregiro) → crédito.
            linea.setDebito(saldo.max(BigDecimal.ZERO));
            linea.setCredito(saldo.signum() < 0 ? saldo.abs() : BigDecimal.ZERO);
            lineas.add(linea);
        }
        return lineas;
    }

    @Override
    @Transactional
    public AsientoContableTableDto guardarDesdeBancos(Integer empresaId, java.time.LocalDate fecha, Integer usuarioId) {
        List<SaldoInicialLineaDto> lineas = sugerirDesdeBancos(empresaId);
        if (lineas.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "No hay cuentas bancarias con cuenta contable y saldo para cargar la apertura.");
        }
        CreateSaldosInicialesDto dto = new CreateSaldosInicialesDto();
        dto.setFechaApertura(fecha != null ? fecha : java.time.LocalDate.now());
        dto.setLineas(lineas);
        // guardar() aplica el cuadre del descuadre contra patrimonio (resultados acumulados).
        return guardar(dto, empresaId, usuarioId);
    }

    @Override
    @Transactional
    public AsientoContableTableDto guardar(CreateSaldosInicialesDto dto, Integer empresaId, Integer usuarioId) {
        if (asientoRepo.findFirstByEmpresaIdAndTipoOrigen(empresaId, TIPO_ORIGEN).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un asiento de apertura. Elimínalo para volver a cargar los saldos iniciales.");
        }
        PeriodoContableEntity periodo = periodoRepo.findByEmpresaIdAndEstado(empresaId, "ABIERTO")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "No hay un período contable ABIERTO. Abra un período antes de cargar los saldos iniciales."));

        List<AsientoDetalleEntity> detalles = new ArrayList<>();
        BigDecimal totalDb = BigDecimal.ZERO;
        BigDecimal totalCr = BigDecimal.ZERO;

        for (SaldoInicialLineaDto l : dto.getLineas()) {
            BigDecimal debito = nz(l.getDebito());
            BigDecimal credito = nz(l.getCredito());
            if (debito.signum() == 0 && credito.signum() == 0) continue; // línea vacía
            PlanCuentaEntity cuenta = planRepo.findByIdAndEmpresaId(l.getCuentaId(), empresaId)
                    .filter(c -> Boolean.TRUE.equals(c.getActiva()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Cuenta contable no válida o inactiva en los saldos iniciales."));
            detalles.add(AsientoDetalleEntity.builder()
                    .cuentaId(cuenta.getId())
                    .descripcion("Saldo inicial")
                    .debito(debito)
                    .credito(credito)
                    .terceroId(l.getTerceroId())
                    .build());
            totalDb = totalDb.add(debito);
            totalCr = totalCr.add(credito);
        }

        if (detalles.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "No se ingresó ningún saldo inicial.");
        }

        // Cuadre: la diferencia se lleva a la cuenta de ajuste (patrimonio).
        BigDecimal diff = totalDb.subtract(totalCr);
        if (diff.signum() != 0) {
            PlanCuentaEntity ajuste = (dto.getCuentaAjusteId() != null)
                    ? planRepo.findByIdAndEmpresaId(dto.getCuentaAjusteId(), empresaId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "La cuenta de ajuste no existe."))
                    : config.resolverCuenta(empresaId, ConceptoContable.RESULTADOS_ACUMULADOS);
            if (diff.signum() > 0) {
                // Sobra débito → acreditar el ajuste (patrimonio).
                detalles.add(AsientoDetalleEntity.builder().cuentaId(ajuste.getId())
                        .descripcion("Ajuste de saldos iniciales")
                        .debito(BigDecimal.ZERO).credito(diff).build());
                totalCr = totalCr.add(diff);
            } else {
                detalles.add(AsientoDetalleEntity.builder().cuentaId(ajuste.getId())
                        .descripcion("Ajuste de saldos iniciales")
                        .debito(diff.negate()).credito(BigDecimal.ZERO).build());
                totalDb = totalDb.add(diff.negate());
            }
        }

        String comprobante = queryRepo.siguienteNumeroComprobante(empresaId, PREFIX);
        AsientoContableEntity asiento = AsientoContableEntity.builder()
                .empresaId(empresaId)
                .fecha(dto.getFechaApertura())
                .descripcion("Saldos iniciales de apertura")
                .tipoOrigen(TIPO_ORIGEN)
                .tipoComprobante("AP")
                .periodoContableId(periodo.getId())
                .numeroComprobante(comprobante)
                .totalDebito(totalDb)
                .totalCredito(totalCr)
                .estado("CONTABILIZADO")
                .usuarioId(usuarioId)
                .detalles(detalles)
                .build();
        detalles.forEach(d -> d.setAsiento(asiento));

        AsientoContableEntity saved = asientoRepo.save(asiento);
        return toDto(saved);
    }

    @Override
    @Transactional
    public void eliminar(Integer empresaId) {
        AsientoContableEntity apertura = asientoRepo.findFirstByEmpresaIdAndTipoOrigen(empresaId, TIPO_ORIGEN)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No hay asiento de apertura para eliminar."));
        // Solo permitido durante la configuración inicial (sin otros movimientos contables).
        if (asientoRepo.countByEmpresaIdAndTipoOrigenNot(empresaId, TIPO_ORIGEN) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se puede eliminar la apertura porque ya existen otros asientos. Anúlela con un contraasiento.");
        }
        asientoRepo.delete(apertura);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private AsientoContableTableDto toDto(AsientoContableEntity e) {
        AsientoContableTableDto dto = new AsientoContableTableDto();
        dto.setId(e.getId());
        dto.setNumeroComprobante(e.getNumeroComprobante());
        dto.setFecha(e.getFecha() != null ? e.getFecha().toString() : null);
        dto.setDescripcion(e.getDescripcion());
        dto.setTipoOrigen(e.getTipoOrigen());
        dto.setTipoComprobante(e.getTipoComprobante());
        dto.setTotalDebito(e.getTotalDebito());
        dto.setTotalCredito(e.getTotalCredito());
        dto.setEstado(e.getEstado());
        dto.setDetalles(queryRepo.obtenerDetalles(e.getId()));
        return dto;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
