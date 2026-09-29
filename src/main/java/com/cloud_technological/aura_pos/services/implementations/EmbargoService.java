package com.cloud_technological.aura_pos.services.implementations;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.nomina.embargo.EmbargoDtos.CreateEmbargoDto;
import com.cloud_technological.aura_pos.dto.nomina.embargo.EmbargoDtos.EmbargoDto;
import com.cloud_technological.aura_pos.entity.ContratoLaboralEntity;
import com.cloud_technological.aura_pos.entity.EmbargoEntity;
import com.cloud_technological.aura_pos.entity.EmbargoEntity.Estado;
import com.cloud_technological.aura_pos.entity.EmbargoEntity.Tipo;
import com.cloud_technological.aura_pos.repositories.nomina.EmbargoJPARepository;
import com.cloud_technological.aura_pos.utils.GlobalException;

/**
 * Embargos sobre el salario (V113).
 *
 * <p>La prelación y los límites los aplica {@code CalculadoraEmbargos} al
 * liquidar; aquí solo se capturan y se cierran. Alimentos tiene prelación
 * absoluta, por eso entra siempre en prioridad 1.
 */
@Service
public class EmbargoService {

    private static final Set<String> TIPOS_VALIDOS = Set.of(
            Tipo.ALIMENTOS, Tipo.COOPERATIVA, Tipo.JUDICIAL_ORDINARIO, Tipo.FISCAL);

    private final EmbargoJPARepository repo;
    private final ContratoLaboralService contratoService;

    public EmbargoService(EmbargoJPARepository repo, ContratoLaboralService contratoService) {
        this.repo = repo;
        this.contratoService = contratoService;
    }

    @Transactional(readOnly = true)
    public List<EmbargoDto> listar(Long contratoId, Integer empresaId) {
        contratoService.obtener(contratoId, empresaId);
        return repo.findByContratoIdOrderByPrioridadAsc(contratoId).stream()
                .map(this::aDto)
                .toList();
    }

    @Transactional
    public EmbargoDto crear(CreateEmbargoDto dto, Integer empresaId) {
        if (dto.getTipo() == null || !TIPOS_VALIDOS.contains(dto.getTipo())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Tipo de embargo inválido");
        }
        if (dto.getExpediente() == null || dto.getExpediente().isBlank()) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El expediente es obligatorio");
        }
        if (dto.getFechaInicio() == null) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La fecha de inicio es obligatoria");
        }
        // O valor total O porcentaje, nunca ambos ni ninguno (hay CHECK en BD;
        // se valida antes para dar un mensaje claro en vez de un error de SQL).
        boolean tieneTotal = dto.getValorTotal() != null && dto.getValorTotal().signum() > 0;
        boolean tienePct = dto.getPorcentaje() != null && dto.getPorcentaje().signum() > 0;
        if (tieneTotal == tienePct) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Indica un valor total O un porcentaje, no ambos ni ninguno.");
        }

        ContratoLaboralEntity contrato = contratoService.obtener(dto.getContratoId(), empresaId);

        EmbargoEntity e = new EmbargoEntity();
        e.setEmpresaId(empresaId);
        e.setContrato(contrato);
        e.setExpediente(dto.getExpediente().trim());
        e.setTipo(dto.getTipo());
        // Alimentos tiene prelación absoluta: siempre prioridad 1.
        e.setPrioridad(Tipo.ALIMENTOS.equals(dto.getTipo())
                ? 1
                : (dto.getPrioridad() != null ? dto.getPrioridad() : 1));
        e.setJuzgadoId(dto.getJuzgadoId());
        e.setDemandanteId(dto.getDemandanteId());
        e.setValorTotal(tieneTotal ? dto.getValorTotal() : null);
        e.setPorcentaje(tienePct ? dto.getPorcentaje() : null);
        e.setFechaInicio(dto.getFechaInicio());
        e.setEstado(Estado.ACTIVO);
        e.setObservacion(dto.getObservacion());
        // saldo se inicializa desde valorTotal en @PrePersist.
        return aDto(repo.save(e));
    }

    @Transactional
    public EmbargoDto terminar(Long id, Integer empresaId, LocalDate fechaFin) {
        EmbargoEntity e = repo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Embargo no encontrado"));
        e.setEstado(Estado.TERMINADO);
        e.setFechaFin(fechaFin != null ? fechaFin : LocalDate.now());
        return aDto(repo.save(e));
    }

    private EmbargoDto aDto(EmbargoEntity e) {
        EmbargoDto d = new EmbargoDto();
        d.setId(e.getId());
        d.setExpediente(e.getExpediente());
        d.setTipo(e.getTipo());
        d.setPrioridad(e.getPrioridad());
        d.setValorTotal(e.getValorTotal());
        d.setPorcentaje(e.getPorcentaje());
        d.setSaldo(e.getSaldo());
        d.setFechaInicio(e.getFechaInicio());
        d.setFechaFin(e.getFechaFin());
        d.setEstado(e.getEstado());
        d.setObservacion(e.getObservacion());
        d.setCupoAmpliado(e.tieneCupoAmpliado());
        return d;
    }
}
