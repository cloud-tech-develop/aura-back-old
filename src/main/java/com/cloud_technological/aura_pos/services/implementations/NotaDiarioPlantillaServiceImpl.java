package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.contabilidad.notas.NotaDiarioDto;
import com.cloud_technological.aura_pos.dto.contabilidad.notas.NotaDiarioPlantillaDto;
import com.cloud_technological.aura_pos.dto.contabilidad.notas.SaveNotaDiarioDto;
import com.cloud_technological.aura_pos.dto.contabilidad.notas.SaveNotaDiarioLineaDto;
import com.cloud_technological.aura_pos.dto.contabilidad.notas.SaveNotaDiarioPlantillaDto;
import com.cloud_technological.aura_pos.entity.AsientoDetalleEntity;
import com.cloud_technological.aura_pos.entity.NotaDiarioPlantillaEntity;
import com.cloud_technological.aura_pos.entity.NotaDiarioPlantillaLineaEntity;
import com.cloud_technological.aura_pos.repositories.contabilidad.NotaDiarioPlantillaJPARepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.NotaDiarioQueryRepository;
import com.cloud_technological.aura_pos.services.NotaDiarioPlantillaService;
import com.cloud_technological.aura_pos.services.NotaDiarioService;
import com.cloud_technological.aura_pos.utils.GlobalException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotaDiarioPlantillaServiceImpl implements NotaDiarioPlantillaService {

    private final NotaDiarioPlantillaJPARepository repo;
    private final NotaDiarioQueryRepository queryRepo;
    private final NotaDiarioLineasBuilder lineasBuilder;
    private final NotaDiarioService notaService;

    @Override
    @Transactional(readOnly = true)
    public List<NotaDiarioPlantillaDto> listar(Integer empresaId) {
        return repo.findByEmpresaIdAndDeletedAtIsNullOrderByNombreAsc(empresaId).stream()
                .map(p -> toDto(p, false)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public NotaDiarioPlantillaDto obtener(Long id, Integer empresaId) {
        return toDto(cargar(id, empresaId), true);
    }

    @Override
    @Transactional
    public NotaDiarioPlantillaDto crear(Integer empresaId, Integer usuarioId, SaveNotaDiarioPlantillaDto dto) {
        if (!dto.traeLineas() || dto.getLineas().isEmpty()) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La plantilla necesita al menos una línea.");
        }
        if (repo.existsByEmpresaIdAndNombreIgnoreCaseAndDeletedAtIsNull(empresaId, dto.getNombre().trim())) {
            throw new GlobalException(HttpStatus.CONFLICT, "Ya existe una plantilla con el nombre " + dto.getNombre().trim());
        }
        NotaDiarioPlantillaEntity p = new NotaDiarioPlantillaEntity();
        p.setEmpresaId(empresaId);
        p.setUsuarioId(usuarioId);
        aplicar(p, empresaId, dto);
        return toDto(repo.saveAndFlush(p), true);
    }

    @Override
    @Transactional
    public NotaDiarioPlantillaDto actualizar(Long id, Integer empresaId, SaveNotaDiarioPlantillaDto dto) {
        NotaDiarioPlantillaEntity p = cargar(id, empresaId);
        if (repo.existsByEmpresaIdAndNombreIgnoreCaseAndDeletedAtIsNullAndIdNot(empresaId, dto.getNombre().trim(), id)) {
            throw new GlobalException(HttpStatus.CONFLICT, "Ya existe una plantilla con el nombre " + dto.getNombre().trim());
        }
        if (dto.traeLineas() && dto.getLineas().isEmpty()) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La plantilla necesita al menos una línea.");
        }
        aplicar(p, empresaId, dto);
        p.setUpdatedAt(LocalDateTime.now());
        return toDto(repo.saveAndFlush(p), true);
    }

    @Override
    @Transactional
    public void eliminar(Long id, Integer empresaId) {
        NotaDiarioPlantillaEntity p = cargar(id, empresaId);
        // Borrado lógico: las notas que salieron de ella conservan la referencia.
        p.setDeletedAt(LocalDateTime.now());
        p.setActiva(false);
        repo.save(p);
    }

    @Override
    @Transactional
    public NotaDiarioDto generar(Long id, Integer empresaId, Integer usuarioId, LocalDate fecha) {
        NotaDiarioPlantillaEntity p = cargar(id, empresaId);
        return notaService.crear(empresaId, usuarioId, aNota(p, fecha));
    }

    @Override
    @Transactional
    public boolean generarProgramada(Long id, LocalDate hoy) {
        NotaDiarioPlantillaEntity p = repo.findById(id).orElse(null);
        if (p == null || p.getDeletedAt() != null || !Boolean.TRUE.equals(p.getActiva())
                || !Boolean.TRUE.equals(p.getRecurrente()) || p.getDiaMes() == null) {
            return false;
        }
        YearMonth mes = YearMonth.from(hoy);
        String periodo = mes.toString(); // YYYY-MM
        if (p.getUltimoPeriodo() != null && p.getUltimoPeriodo().compareTo(periodo) >= 0) return false;
        if (hoy.getDayOfMonth() < p.getDiaMes()) return false;

        // Si la creación falla (mes cerrado, cuenta desactivada) la transacción
        // se revierte entera, ultimo_periodo no avanza y mañana se reintenta.
        notaService.crear(p.getEmpresaId(), p.getUsuarioId(), aNota(p, mes.atDay(p.getDiaMes())));
        p.setUltimoPeriodo(periodo);
        repo.save(p);
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> recurrentesActivas() {
        return repo.findByRecurrenteTrueAndActivaTrueAndDeletedAtIsNull().stream()
                .map(NotaDiarioPlantillaEntity::getId).toList();
    }

    // ── Internos ─────────────────────────────────────────────────────────

    private NotaDiarioPlantillaEntity cargar(Long id, Integer empresaId) {
        return repo.findByIdAndEmpresaIdAndDeletedAtIsNull(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Plantilla no encontrada"));
    }

    private void aplicar(NotaDiarioPlantillaEntity p, Integer empresaId, SaveNotaDiarioPlantillaDto dto) {
        boolean recurrente = Boolean.TRUE.equals(dto.getRecurrente());
        if (recurrente && dto.getDiaMes() == null) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Indique el día del mes en que se genera la nota recurrente (1 a 28).");
        }
        p.setNombre(dto.getNombre().trim());
        p.setDescripcion(dto.getDescripcion().trim());
        p.setClasificacion(lineasBuilder.normalizarClasificacion(dto.getClasificacion()));
        p.setRecurrente(recurrente);
        p.setDiaMes(recurrente ? dto.getDiaMes() : null);
        p.setActiva(dto.getActiva() == null || dto.getActiva());

        if (dto.traeLineas()) {
            // Mismas reglas que la nota: si no, la plantilla generaría borradores
            // que la nota después rechaza.
            List<AsientoDetalleEntity> validadas = lineasBuilder.construir(empresaId, dto.getLineas());
            p.getLineas().clear();
            for (int i = 0; i < validadas.size(); i++) {
                AsientoDetalleEntity v = validadas.get(i);
                NotaDiarioPlantillaLineaEntity l = new NotaDiarioPlantillaLineaEntity();
                l.setPlantilla(p);
                l.setOrden(i);
                l.setCuentaId(v.getCuentaId());
                l.setDescripcion(v.getDescripcion());
                l.setDebito(v.getDebito());
                l.setCredito(v.getCredito());
                l.setTerceroId(v.getTerceroId());
                l.setCentroCostoId(v.getCentroCostoId());
                l.setProyectoId(v.getProyectoId());
                l.setFrenteId(v.getFrenteId());
                p.getLineas().add(l);
            }
        }
    }

    private static SaveNotaDiarioDto aNota(NotaDiarioPlantillaEntity p, LocalDate fecha) {
        SaveNotaDiarioDto dto = new SaveNotaDiarioDto();
        dto.setFecha(fecha);
        dto.setDescripcion(p.getDescripcion());
        dto.setClasificacion(p.getClasificacion());
        dto.setPlantillaId(p.getId());
        dto.setContabilizar(false);
        dto.setLineas(p.getLineas().stream().map(l -> {
            SaveNotaDiarioLineaDto d = new SaveNotaDiarioLineaDto();
            d.setCuentaId(l.getCuentaId());
            d.setDescripcion(l.getDescripcion());
            d.setDebito(l.getDebito());
            d.setCredito(l.getCredito());
            d.setTerceroId(l.getTerceroId());
            d.setCentroCostoId(l.getCentroCostoId());
            d.setProyectoId(l.getProyectoId());
            d.setFrenteId(l.getFrenteId());
            return d;
        }).toList());
        return dto;
    }

    private NotaDiarioPlantillaDto toDto(NotaDiarioPlantillaEntity p, boolean conLineas) {
        NotaDiarioPlantillaDto dto = new NotaDiarioPlantillaDto();
        dto.setId(p.getId());
        dto.setNombre(p.getNombre());
        dto.setDescripcion(p.getDescripcion());
        dto.setClasificacion(p.getClasificacion());
        dto.setRecurrente(p.getRecurrente());
        dto.setDiaMes(p.getDiaMes());
        dto.setUltimoPeriodo(p.getUltimoPeriodo());
        dto.setActiva(p.getActiva());
        dto.setCantidadLineas(p.getLineas().size());
        dto.setTotalDebito(p.getLineas().stream().map(NotaDiarioPlantillaLineaEntity::getDebito)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        dto.setTotalCredito(p.getLineas().stream().map(NotaDiarioPlantillaLineaEntity::getCredito)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        if (conLineas && p.getId() != null) {
            dto.setLineas(queryRepo.lineasPlantilla(p.getId()));
        }
        return dto;
    }
}
