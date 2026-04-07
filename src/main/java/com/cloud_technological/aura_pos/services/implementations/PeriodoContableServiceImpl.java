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

    @Override
    public List<PeriodoContableTableDto> listar(Integer empresaId) {
        return queryRepo.listar(empresaId);
    }

    @Override
    @Transactional
    public PeriodoContableTableDto abrirPeriodo(AbrirPeriodoDto dto, Integer empresaId, Long usuarioId) {
        Short anio = dto.getAnio().shortValue();
        Short mes  = dto.getMes().shortValue();

        // No puede haber dos períodos ABIERTOS simultáneamente
        if (jpaRepo.existsByEmpresaIdAndEstado(empresaId, "ABIERTO")) {
            throw new GlobalException(HttpStatus.CONFLICT,
                "Ya existe un período contable ABIERTO. Ciérrelo antes de abrir uno nuevo.");
        }

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

        jpaRepo.save(periodo);
        return queryRepo.listar(empresaId).stream()
                .filter(p -> p.getId().equals(periodo.getId()))
                .findFirst()
                .orElseThrow();
    }

    @Override
    @Transactional
    public PeriodoContableTableDto cerrarPeriodo(Long id, CerrarPeriodoDto dto, Integer empresaId, Long usuarioId) {
        PeriodoContableEntity periodo = jpaRepo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Período contable no encontrado"));

        if ("CERRADO".equals(periodo.getEstado())) {
            throw new GlobalException(HttpStatus.CONFLICT, "El período ya está cerrado");
        }

        // Validar que todos los asientos cuadren antes de cerrar
        List<String> sinCuadre = queryRepo.comprobantesSinCuadre(id);
        if (!sinCuadre.isEmpty()) {
            throw new GlobalException(HttpStatus.UNPROCESSABLE_ENTITY,
                "No se puede cerrar el período. Los siguientes comprobantes no cuadran (débito ≠ crédito): "
                + String.join(", ", sinCuadre));
        }

        periodo.setEstado("CERRADO");
        periodo.setFechaCierre(LocalDate.now());
        periodo.setUsuarioCierreId(usuarioId);
        if (dto.getObservaciones() != null && !dto.getObservaciones().isBlank()) {
            periodo.setObservaciones(dto.getObservaciones());
        }

        jpaRepo.save(periodo);
        return queryRepo.listar(empresaId).stream()
                .filter(p -> p.getId().equals(id))
                .findFirst()
                .orElseThrow();
    }

    @Override
    public Optional<PeriodoContableEntity> getPeriodoAbierto(Integer empresaId) {
        return jpaRepo.findByEmpresaIdAndEstado(empresaId, "ABIERTO");
    }
}
