package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.nomina.retefuente.RetefuenteDtos.CreateDeduccionDto;
import com.cloud_technological.aura_pos.dto.nomina.retefuente.RetefuenteDtos.DeduccionDto;
import com.cloud_technological.aura_pos.entity.ContratoLaboralEntity;
import com.cloud_technological.aura_pos.entity.EmpleadoDeduccionRentaEntity;
import com.cloud_technological.aura_pos.entity.EmpleadoDeduccionRentaEntity.Tipo;
import com.cloud_technological.aura_pos.repositories.nomina.RetefuenteJPARepositories.EmpleadoDeduccionRentaRepo;
import com.cloud_technological.aura_pos.utils.GlobalException;

/**
 * Deducciones y rentas exentas de retefuente (Fase 4.5).
 *
 * <p>Depuran la base de retención. Los topes legales NO viven aquí: los aplica
 * {@code CalculadoraRetefuente} en UVT. Esta pantalla solo captura qué declaró
 * el empleado.
 */
@Service
public class DeduccionRentaService {

    private static final Set<String> TIPOS_VALIDOS = Set.of(
            Tipo.DEPENDIENTES, Tipo.INTERESES_VIVIENDA, Tipo.MEDICINA_PREPAGADA,
            Tipo.AFC, Tipo.AFP_VOLUNTARIO);

    private final EmpleadoDeduccionRentaRepo repo;
    private final ContratoLaboralService contratoService;

    public DeduccionRentaService(EmpleadoDeduccionRentaRepo repo,
                                 ContratoLaboralService contratoService) {
        this.repo = repo;
        this.contratoService = contratoService;
    }

    @Transactional(readOnly = true)
    public List<DeduccionDto> listar(Long contratoId, Integer empresaId) {
        contratoService.obtener(contratoId, empresaId);   // valida pertenencia
        return repo.findByContratoIdOrderByVigenteDesdeDesc(contratoId).stream()
                .map(this::aDto)
                .toList();
    }

    @Transactional
    public DeduccionDto crear(CreateDeduccionDto dto, Integer empresaId) {
        if (dto.getTipo() == null || !TIPOS_VALIDOS.contains(dto.getTipo())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Tipo de deducción inválido");
        }
        if (dto.getVigenteDesde() == null) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "vigenteDesde es obligatorio");
        }
        ContratoLaboralEntity contrato = contratoService.obtener(dto.getContratoId(), empresaId);

        EmpleadoDeduccionRentaEntity e = new EmpleadoDeduccionRentaEntity();
        e.setEmpresaId(empresaId);
        e.setContrato(contrato);
        e.setTipo(dto.getTipo());
        // DEPENDIENTES no lleva valor: es 10% del ingreso topado a 32 UVT, lo
        // calcula el motor. Cualquier valor que llegue se ignora.
        e.setValor(Tipo.DEPENDIENTES.equals(dto.getTipo())
                ? BigDecimal.ZERO
                : (dto.getValor() != null ? dto.getValor() : BigDecimal.ZERO));
        e.setVigenteDesde(dto.getVigenteDesde());
        e.setVigenteHasta(dto.getVigenteHasta());
        e.setSoporte(dto.getSoporte());
        return aDto(repo.save(e));
    }

    @Transactional
    public void eliminar(Long id, Integer empresaId) {
        EmpleadoDeduccionRentaEntity e = repo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Deducción no encontrada"));
        repo.delete(e);
    }

    private DeduccionDto aDto(EmpleadoDeduccionRentaEntity e) {
        DeduccionDto d = new DeduccionDto();
        d.setId(e.getId());
        d.setTipo(e.getTipo());
        d.setValor(e.getValor());
        d.setVigenteDesde(e.getVigenteDesde());
        d.setVigenteHasta(e.getVigenteHasta());
        d.setSoporte(e.getSoporte());
        d.setValorLoCalculaElMotor(Tipo.DEPENDIENTES.equals(e.getTipo()));
        return d;
    }
}
