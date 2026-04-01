package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.cloud_technological.aura_pos.dto.contabilidad.AsientoContableTableDto;
import com.cloud_technological.aura_pos.dto.contabilidad.AsientoDetalleDto;
import com.cloud_technological.aura_pos.dto.contabilidad.BalanceGeneralDto;
import com.cloud_technological.aura_pos.dto.contabilidad.CreateAsientoDto;
import com.cloud_technological.aura_pos.dto.contabilidad.EstadoResultadosDto;
import com.cloud_technological.aura_pos.dto.contabilidad.EstadoResultadosLineaDto;
import com.cloud_technological.aura_pos.dto.contabilidad.FlujoCajaDto;
import com.cloud_technological.aura_pos.dto.contabilidad.FlujoCajaLineaDto;
import com.cloud_technological.aura_pos.dto.contabilidad.FlujoCajaProyeccionDto;
import com.cloud_technological.aura_pos.dto.contabilidad.LibroMayorLineaDto;
import com.cloud_technological.aura_pos.entity.AsientoContableEntity;
import com.cloud_technological.aura_pos.entity.AsientoDetalleEntity;
import com.cloud_technological.aura_pos.repositories.contabilidad.AsientoContableJPARepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.AsientoContableQueryRepository;
import com.cloud_technological.aura_pos.services.AsientoContableService;

@Service
public class AsientoContableServiceImpl implements AsientoContableService {

    @Autowired
    private AsientoContableJPARepository repo;

    @Autowired
    private AsientoContableQueryRepository queryRepo;

    @Override
    public List<AsientoContableTableDto> listar(Integer empresaId, String desde, String hasta,
            String tipoOrigen, int page, int rows) {
        return queryRepo.paginar(empresaId, desde, hasta, tipoOrigen, page, rows);
    }

    @Override
    public AsientoContableTableDto obtenerConDetalles(Long id, Integer empresaId) {
        AsientoContableEntity entity = repo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Asiento no encontrado"));
        AsientoContableTableDto dto = toTableDto(entity);
        dto.setDetalles(queryRepo.obtenerDetalles(id));
        return dto;
    }

    @Override
    @Transactional
    public AsientoContableTableDto crear(Integer empresaId, Integer usuarioId, CreateAsientoDto dto) {
        BigDecimal totalDebito = dto.getDetalles().stream()
                .map(d -> d.getDebito() != null ? d.getDebito() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredito = dto.getDetalles().stream()
                .map(d -> d.getCredito() != null ? d.getCredito() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalDebito.compareTo(totalCredito) != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El asiento no está cuadrado: débito=" + totalDebito + " crédito=" + totalCredito);
        }

        List<AsientoDetalleEntity> detalles = dto.getDetalles().stream()
                .map(d -> AsientoDetalleEntity.builder()
                        .cuentaId(d.getCuentaId())
                        .descripcion(d.getDescripcion())
                        .debito(d.getDebito() != null ? d.getDebito() : BigDecimal.ZERO)
                        .credito(d.getCredito() != null ? d.getCredito() : BigDecimal.ZERO)
                        .build())
                .collect(Collectors.toList());

        String comprobante = queryRepo.siguienteNumeroComprobante(empresaId, "CD");

        AsientoContableEntity asiento = AsientoContableEntity.builder()
                .empresaId(empresaId)
                .fecha(dto.getFecha())
                .descripcion(dto.getDescripcion().trim())
                .tipoOrigen("MANUAL")
                .numeroComprobante(comprobante)
                .totalDebito(totalDebito)
                .totalCredito(totalCredito)
                .estado("CONTABILIZADO")
                .usuarioId(usuarioId)
                .detalles(detalles)
                .build();

        // Relación bidireccional: el hijo debe apuntar al padre antes del save
        detalles.forEach(d -> d.setAsiento(asiento));

        AsientoContableEntity saved = repo.save(asiento);

        AsientoContableTableDto result = toTableDto(saved);
        result.setDetalles(queryRepo.obtenerDetalles(saved.getId()));
        return result;
    }

    @Override
    @Transactional
    public void anular(Long id, Integer empresaId) {
        AsientoContableEntity asiento = repo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Asiento no encontrado"));
        if (!"MANUAL".equals(asiento.getTipoOrigen())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Solo se pueden anular asientos manuales");
        }
        asiento.setEstado("ANULADO");
        repo.save(asiento);
    }

    @Override
    public BalanceGeneralDto balanceGeneral(Integer empresaId, String hasta) {
        Map<String, Object> saldos = queryRepo.balanceGeneral(empresaId, hasta);

        BigDecimal activo     = getOrZero(saldos, "ACTIVO");
        BigDecimal pasivo     = getOrZero(saldos, "PASIVO");
        BigDecimal patrimonio = getOrZero(saldos, "PATRIMONIO");
        BigDecimal ingreso    = getOrZero(saldos, "INGRESO");
        BigDecimal gasto      = getOrZero(saldos, "GASTO");
        BigDecimal costo      = getOrZero(saldos, "COSTO");

        BigDecimal utilidad = ingreso.subtract(gasto).subtract(costo);
        BigDecimal ecuacion = activo.subtract(pasivo.add(patrimonio).add(utilidad));

        BalanceGeneralDto dto = new BalanceGeneralDto();
        dto.setHasta(hasta);
        dto.setTotalActivo(activo);
        dto.setTotalPasivo(pasivo);
        dto.setTotalPatrimonio(patrimonio);
        dto.setTotalIngreso(ingreso);
        dto.setTotalGasto(gasto);
        dto.setTotalCosto(costo);
        dto.setUtilidadNeta(utilidad);
        dto.setEcuacionContable(ecuacion);
        return dto;
    }

    @Override
    public EstadoResultadosDto estadoResultados(Integer empresaId, String desde, String hasta) {
        List<EstadoResultadosLineaDto> lineas = queryRepo.estadoResultados(empresaId, desde, hasta);

        List<EstadoResultadosLineaDto> ingresos = lineas.stream()
                .filter(l -> "INGRESO".equals(l.getTipo())).collect(Collectors.toList());
        List<EstadoResultadosLineaDto> costos = lineas.stream()
                .filter(l -> "COSTO".equals(l.getTipo())).collect(Collectors.toList());
        List<EstadoResultadosLineaDto> gastos = lineas.stream()
                .filter(l -> "GASTO".equals(l.getTipo())).collect(Collectors.toList());

        BigDecimal totalIngresos = sum(ingresos);
        BigDecimal totalCostos   = sum(costos);
        BigDecimal totalGastos   = sum(gastos);
        BigDecimal utilidadBruta = totalIngresos.subtract(totalCostos);
        BigDecimal utilidadNeta  = utilidadBruta.subtract(totalGastos);

        BigDecimal margenBruto = totalIngresos.compareTo(BigDecimal.ZERO) != 0
                ? utilidadBruta.multiply(new BigDecimal("100")).divide(totalIngresos, 2, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal margenNeto = totalIngresos.compareTo(BigDecimal.ZERO) != 0
                ? utilidadNeta.multiply(new BigDecimal("100")).divide(totalIngresos, 2, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        EstadoResultadosDto dto = new EstadoResultadosDto();
        dto.setDesde(desde);
        dto.setHasta(hasta);
        dto.setIngresos(ingresos);
        dto.setCostos(costos);
        dto.setGastos(gastos);
        dto.setTotalIngresos(totalIngresos);
        dto.setTotalCostos(totalCostos);
        dto.setTotalGastos(totalGastos);
        dto.setUtilidadBruta(utilidadBruta);
        dto.setUtilidadNeta(utilidadNeta);
        dto.setMargenBruto(margenBruto);
        dto.setMargenNeto(margenNeto);
        return dto;
    }

    @Override
    public List<LibroMayorLineaDto> libroMayor(Integer empresaId, Long cuentaId,
            String desde, String hasta) {
        return queryRepo.libroMayor(empresaId, cuentaId, desde, hasta);
    }

    @Override
    public FlujoCajaDto flujoCaja(Integer empresaId, String desde, String hasta) {
        BigDecimal saldoInicial = queryRepo.saldoInicialTesoreria(empresaId, desde);
        List<FlujoCajaLineaDto> movimientos = queryRepo.movimientosTesoreria(empresaId, desde, hasta);

        BigDecimal totalIngresos = movimientos.stream()
                .filter(m -> "INGRESO".equals(m.getTipo()))
                .map(FlujoCajaLineaDto::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalEgresos = movimientos.stream()
                .filter(m -> "EGRESO".equals(m.getTipo()))
                .map(FlujoCajaLineaDto::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal saldoFinal = saldoInicial.add(totalIngresos).subtract(totalEgresos);

        List<FlujoCajaProyeccionDto> cxc = queryRepo.proyeccionCxC(empresaId);
        List<FlujoCajaProyeccionDto> cxp = queryRepo.proyeccionCxP(empresaId);

        BigDecimal totalPorCobrar = cxc.stream()
                .map(FlujoCajaProyeccionDto::getSaldo)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPorPagar = cxp.stream()
                .map(FlujoCajaProyeccionDto::getSaldo)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return FlujoCajaDto.builder()
                .desde(desde)
                .hasta(hasta)
                .saldoInicial(saldoInicial)
                .movimientos(movimientos)
                .totalIngresos(totalIngresos)
                .totalEgresos(totalEgresos)
                .saldoFinal(saldoFinal)
                .proyeccionCxC(cxc)
                .proyeccionCxP(cxp)
                .totalPorCobrar(totalPorCobrar)
                .totalPorPagar(totalPorPagar)
                .build();
    }

    private BigDecimal sum(List<EstadoResultadosLineaDto> lineas) {
        return lineas.stream()
                .map(l -> l.getSaldo() != null ? l.getSaldo() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal getOrZero(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val == null) return BigDecimal.ZERO;
        if (val instanceof BigDecimal) return (BigDecimal) val;
        return new BigDecimal(val.toString());
    }

    private AsientoContableTableDto toTableDto(AsientoContableEntity e) {
        AsientoContableTableDto dto = new AsientoContableTableDto();
        dto.setId(e.getId());
        dto.setNumeroComprobante(e.getNumeroComprobante());
        dto.setFecha(e.getFecha() != null ? e.getFecha().toString() : null);
        dto.setDescripcion(e.getDescripcion());
        dto.setTipoOrigen(e.getTipoOrigen());
        dto.setOrigenId(e.getOrigenId());
        dto.setTotalDebito(e.getTotalDebito());
        dto.setTotalCredito(e.getTotalCredito());
        dto.setEstado(e.getEstado());
        dto.setCreatedAt(e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
        return dto;
    }
}
