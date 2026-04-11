package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.activos_fijos.ActivoFijoDto;
import com.cloud_technological.aura_pos.dto.activos_fijos.ActivoFijoTableDto;
import com.cloud_technological.aura_pos.dto.activos_fijos.CreateActivoFijoDto;
import com.cloud_technological.aura_pos.dto.activos_fijos.DepreciacionPeriodoDto;
import com.cloud_technological.aura_pos.entity.ActivoFijoEntity;
import com.cloud_technological.aura_pos.entity.AsientoContableEntity;
import com.cloud_technological.aura_pos.entity.AsientoDetalleEntity;
import com.cloud_technological.aura_pos.entity.DepreciacionPeriodoEntity;
import com.cloud_technological.aura_pos.repositories.activos_fijos.ActivoFijoJPARepository;
import com.cloud_technological.aura_pos.repositories.activos_fijos.ActivoFijoQueryRepository;
import com.cloud_technological.aura_pos.repositories.activos_fijos.DepreciacionPeriodoJPARepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.AsientoContableJPARepository;
import com.cloud_technological.aura_pos.repositories.periodo_contable.PeriodoContableJPARepository;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;

@Service
public class ActivoFijoServiceImpl {

    @Autowired private ActivoFijoJPARepository activoJPARepository;
    @Autowired private ActivoFijoQueryRepository activoQueryRepository;
    @Autowired private DepreciacionPeriodoJPARepository depreciacionJPARepository;
    @Autowired private AsientoContableJPARepository asientoJPARepository;
    @Autowired private PeriodoContableJPARepository periodoJPARepository;

    // ── CRUD ────────────────────────────────────────────────────────────────────

    @Transactional
    public ActivoFijoDto crear(CreateActivoFijoDto dto, Integer empresaId) {
        if (activoJPARepository.existsByCodigoAndEmpresaIdAndDeletedAtIsNull(dto.getCodigo(), empresaId))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Ya existe un activo con el código " + dto.getCodigo());

        ActivoFijoEntity entity = new ActivoFijoEntity();
        entity.setEmpresaId(empresaId);
        mapFromDto(dto, entity);
        entity.setDepreciacionAcumulada(BigDecimal.ZERO);
        entity.setEstado("ACTIVO");
        return toDto(activoJPARepository.save(entity));
    }

    @Transactional
    public ActivoFijoDto actualizar(Long id, CreateActivoFijoDto dto, Integer empresaId) {
        ActivoFijoEntity entity = activoJPARepository.findByIdAndEmpresaIdAndDeletedAtIsNull(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Activo fijo no encontrado"));

        if (activoJPARepository.existsByCodigoAndEmpresaIdAndIdNotAndDeletedAtIsNull(dto.getCodigo(), empresaId, id))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El código ya está en uso por otro activo");

        mapFromDto(dto, entity);
        return toDto(activoJPARepository.save(entity));
    }

    public ActivoFijoDto getById(Long id, Integer empresaId) {
        ActivoFijoEntity entity = activoJPARepository.findByIdAndEmpresaIdAndDeletedAtIsNull(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Activo fijo no encontrado"));
        return toDto(entity);
    }

    public PageImpl<ActivoFijoTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        return activoQueryRepository.listar(pageable, empresaId);
    }

    @Transactional
    public ActivoFijoDto darDeBaja(Long id, String observaciones, Integer empresaId) {
        ActivoFijoEntity entity = activoJPARepository.findByIdAndEmpresaIdAndDeletedAtIsNull(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Activo fijo no encontrado"));

        entity.setEstado("DADO_DE_BAJA");
        if (observaciones != null && !observaciones.isBlank()) {
            String obs = entity.getObservaciones() != null ? entity.getObservaciones() + " | " : "";
            entity.setObservaciones(obs + "Baja: " + observaciones);
        }
        return toDto(activoJPARepository.save(entity));
    }

    @Transactional
    public void eliminar(Long id, Integer empresaId) {
        ActivoFijoEntity entity = activoJPARepository.findByIdAndEmpresaIdAndDeletedAtIsNull(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Activo fijo no encontrado"));
        entity.setDeletedAt(LocalDateTime.now());
        activoJPARepository.save(entity);
    }

    // ── Depreciación ────────────────────────────────────────────────────────────

    @Transactional
    public List<DepreciacionPeriodoDto> calcularDepreciacionPeriodo(Long periodoId, Integer empresaId,
            Integer usuarioId) {

        // Validar que el período existe
        periodoJPARepository.findByIdAndEmpresaId(periodoId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Período contable no encontrado"));

        List<ActivoFijoEntity> activos = activoJPARepository
                .findByEmpresaIdAndEstadoAndDeletedAtIsNull(empresaId, "ACTIVO");

        return activos.stream()
                .filter(a -> !depreciacionJPARepository.existsByActivoIdAndPeriodoId(a.getId(), periodoId))
                .map(activo -> calcularYGuardar(activo, periodoId, empresaId, usuarioId))
                .toList();
    }

    public List<DepreciacionPeriodoDto> historialDepreciacion(Long activoId, Integer empresaId) {
        activoJPARepository.findByIdAndEmpresaIdAndDeletedAtIsNull(activoId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Activo fijo no encontrado"));

        return depreciacionJPARepository.findByActivoIdOrderByCalculadoEnDesc(activoId)
                .stream().map(this::toDepDto).toList();
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private DepreciacionPeriodoDto calcularYGuardar(ActivoFijoEntity activo, Long periodoId,
            Integer empresaId, Integer usuarioId) {

        BigDecimal baseDepreciable = activo.getValorCompra()
                .subtract(activo.getValorResidual())
                .subtract(activo.getDepreciacionAcumulada());

        if (baseDepreciable.compareTo(BigDecimal.ZERO) <= 0) {
            activo.setEstado("DEPRECIADO");
            activoJPARepository.save(activo);
            DepreciacionPeriodoDto dto = new DepreciacionPeriodoDto();
            dto.setActivoId(activo.getId());
            dto.setValor(BigDecimal.ZERO);
            return dto;
        }

        BigDecimal cuotaMensual = baseDepreciable
                .divide(BigDecimal.valueOf(activo.getVidaUtilMeses()), 2, RoundingMode.HALF_UP);

        // Crear asiento si tiene cuentas configuradas
        Long asientoId = null;
        if (activo.getCuentaGastoDepId() != null && activo.getCuentaDepreciacionId() != null) {
            asientoId = crearAsientoDepreciacion(activo, cuotaMensual, periodoId, empresaId, usuarioId);
        }

        // Guardar registro de depreciación
        DepreciacionPeriodoEntity dep = new DepreciacionPeriodoEntity();
        dep.setActivoId(activo.getId());
        dep.setEmpresaId(empresaId);
        dep.setPeriodoId(periodoId);
        dep.setValor(cuotaMensual);
        dep.setAsientoId(asientoId);
        dep.setCalculadoEn(LocalDateTime.now());
        dep = depreciacionJPARepository.save(dep);

        // Actualizar depreciación acumulada del activo
        BigDecimal nuevaAcumulada = activo.getDepreciacionAcumulada().add(cuotaMensual);
        activo.setDepreciacionAcumulada(nuevaAcumulada);
        BigDecimal valorEnLibros = activo.getValorCompra().subtract(nuevaAcumulada);
        if (valorEnLibros.compareTo(activo.getValorResidual()) <= 0) {
            activo.setEstado("DEPRECIADO");
        }
        activoJPARepository.save(activo);

        return toDepDto(dep);
    }

    private Long crearAsientoDepreciacion(ActivoFijoEntity activo, BigDecimal valor,
            Long periodoId, Integer empresaId, Integer usuarioId) {

        AsientoContableEntity asiento = AsientoContableEntity.builder()
                .empresaId(empresaId)
                .fecha(LocalDate.now())
                .descripcion("Depreciación activo: " + activo.getCodigo() + " - " + activo.getDescripcion())
                .tipoOrigen("DEPRECIACION")
                .origenId(activo.getId())
                .totalDebito(valor)
                .totalCredito(valor)
                .estado("CONTABILIZADO")
                .usuarioId(usuarioId)
                .periodoContableId(periodoId)
                .build();

        // Débito: gasto depreciación
        AsientoDetalleEntity debito = new AsientoDetalleEntity();
        debito.setCuentaId(activo.getCuentaGastoDepId());
        debito.setDebito(valor);
        debito.setCredito(BigDecimal.ZERO);
        debito.setDescripcion("Gasto depreciación " + activo.getCodigo());
        if (activo.getCentroCostoId() != null) debito.setCentroCostoId(activo.getCentroCostoId());
        asiento.getDetalles().add(debito);
        debito.setAsiento(asiento);

        // Crédito: depreciación acumulada
        AsientoDetalleEntity credito = new AsientoDetalleEntity();
        credito.setCuentaId(activo.getCuentaDepreciacionId());
        credito.setDebito(BigDecimal.ZERO);
        credito.setCredito(valor);
        credito.setDescripcion("Depreciación acumulada " + activo.getCodigo());
        asiento.getDetalles().add(credito);
        credito.setAsiento(asiento);

        return asientoJPARepository.save(asiento).getId();
    }

    private void mapFromDto(CreateActivoFijoDto dto, ActivoFijoEntity entity) {
        entity.setCodigo(dto.getCodigo().trim().toUpperCase());
        entity.setDescripcion(dto.getDescripcion().trim());
        entity.setCategoria(dto.getCategoria());
        entity.setFechaAdquisicion(dto.getFechaAdquisicion());
        entity.setValorCompra(dto.getValorCompra());
        entity.setVidaUtilMeses(dto.getVidaUtilMeses());
        entity.setMetodoDepreciacion(dto.getMetodoDepreciacion() != null ? dto.getMetodoDepreciacion() : "LINEA_RECTA");
        entity.setValorResidual(dto.getValorResidual() != null ? dto.getValorResidual() : BigDecimal.ZERO);
        entity.setUbicacion(dto.getUbicacion());
        entity.setResponsable(dto.getResponsable());
        entity.setCuentaActivoId(dto.getCuentaActivoId());
        entity.setCuentaDepreciacionId(dto.getCuentaDepreciacionId());
        entity.setCuentaGastoDepId(dto.getCuentaGastoDepId());
        entity.setCentroCostoId(dto.getCentroCostoId());
        entity.setPeriodoContableId(dto.getPeriodoContableId());
        entity.setTerceroId(dto.getTerceroId());
        entity.setObservaciones(dto.getObservaciones());
    }

    private ActivoFijoDto toDto(ActivoFijoEntity e) {
        ActivoFijoDto dto = new ActivoFijoDto();
        dto.setId(e.getId());
        dto.setEmpresaId(e.getEmpresaId());
        dto.setCodigo(e.getCodigo());
        dto.setDescripcion(e.getDescripcion());
        dto.setCategoria(e.getCategoria());
        dto.setFechaAdquisicion(e.getFechaAdquisicion());
        dto.setValorCompra(e.getValorCompra());
        dto.setVidaUtilMeses(e.getVidaUtilMeses());
        dto.setMetodoDepreciacion(e.getMetodoDepreciacion());
        dto.setDepreciacionAcumulada(e.getDepreciacionAcumulada());
        dto.setValorResidual(e.getValorResidual());
        dto.setValorEnLibros(e.getValorCompra().subtract(e.getDepreciacionAcumulada()));
        dto.setUbicacion(e.getUbicacion());
        dto.setResponsable(e.getResponsable());
        dto.setEstado(e.getEstado());
        dto.setCuentaActivoId(e.getCuentaActivoId());
        dto.setCuentaDepreciacionId(e.getCuentaDepreciacionId());
        dto.setCuentaGastoDepId(e.getCuentaGastoDepId());
        dto.setCentroCostoId(e.getCentroCostoId());
        dto.setPeriodoContableId(e.getPeriodoContableId());
        dto.setTerceroId(e.getTerceroId());
        dto.setObservaciones(e.getObservaciones());
        dto.setCreatedAt(e.getCreatedAt());
        return dto;
    }

    private DepreciacionPeriodoDto toDepDto(DepreciacionPeriodoEntity e) {
        DepreciacionPeriodoDto dto = new DepreciacionPeriodoDto();
        dto.setId(e.getId());
        dto.setActivoId(e.getActivoId());
        dto.setPeriodoId(e.getPeriodoId());
        dto.setValor(e.getValor());
        dto.setAsientoId(e.getAsientoId());
        dto.setCalculadoEn(e.getCalculadoEn());
        return dto;
    }
}
