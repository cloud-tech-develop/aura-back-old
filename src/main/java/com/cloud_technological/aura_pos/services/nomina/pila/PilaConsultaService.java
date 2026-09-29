package com.cloud_technological.aura_pos.services.nomina.pila;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.nomina.pila.PilaDtos.CotizanteDto;
import com.cloud_technological.aura_pos.dto.nomina.pila.PilaDtos.EncabezadoDto;
import com.cloud_technological.aura_pos.entity.PilaCotizanteEntity;
import com.cloud_technological.aura_pos.entity.PilaEncabezadoEntity;
import com.cloud_technological.aura_pos.entity.PilaPlanillaEntity;
import com.cloud_technological.aura_pos.repositories.nomina.PilaJPARepositories.PilaCotizanteRepo;
import com.cloud_technological.aura_pos.repositories.nomina.PilaJPARepositories.PilaEncabezadoRepo;
import com.cloud_technological.aura_pos.repositories.nomina.PilaJPARepositories.PilaPlanillaRepo;
import com.cloud_technological.aura_pos.utils.GlobalException;

/** Consultas de PILA (Fase 6): listar planillas, ver una y sus cotizantes. */
@Service
public class PilaConsultaService {

    private final PilaEncabezadoRepo encabezadoRepo;
    private final PilaPlanillaRepo planillaRepo;
    private final PilaCotizanteRepo cotizanteRepo;

    public PilaConsultaService(PilaEncabezadoRepo encabezadoRepo,
                               PilaPlanillaRepo planillaRepo,
                               PilaCotizanteRepo cotizanteRepo) {
        this.encabezadoRepo = encabezadoRepo;
        this.planillaRepo = planillaRepo;
        this.cotizanteRepo = cotizanteRepo;
    }

    @Transactional(readOnly = true)
    public List<EncabezadoDto> listar(Integer empresaId) {
        return encabezadoRepo.findByEmpresaIdOrderByPeriodoDesc(empresaId).stream()
                .map(this::aDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public EncabezadoDto obtener(Integer empresaId, String periodo) {
        PilaEncabezadoEntity enc = encabezadoRepo.findByEmpresaIdAndPeriodo(empresaId, periodo)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND,
                        "No hay planilla de PILA para " + periodo));
        return aDto(enc);
    }

    @Transactional(readOnly = true)
    public List<CotizanteDto> cotizantes(Integer empresaId, Long encabezadoId) {
        PilaEncabezadoEntity enc = encabezadoRepo.findById(encabezadoId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Planilla no encontrada"));
        // No filtrar por empresa desde un JOIN: se valida sobre el encabezado.
        if (!enc.getEmpresaId().equals(empresaId)) {
            throw new GlobalException(HttpStatus.FORBIDDEN, "La planilla no pertenece a la empresa");
        }

        PilaPlanillaEntity planilla = primeraPlanilla(enc);
        if (planilla == null) return List.of();

        return cotizanteRepo.findByPlanillaIdOrderBySecuencia(planilla.getId()).stream()
                .map(this::aDto)
                .toList();
    }

    private PilaPlanillaEntity primeraPlanilla(PilaEncabezadoEntity enc) {
        List<PilaPlanillaEntity> planillas = planillaRepo.findByEncabezadoId(enc.getId());
        return planillas.isEmpty() ? null : planillas.get(0);
    }

    private EncabezadoDto aDto(PilaEncabezadoEntity e) {
        EncabezadoDto d = new EncabezadoDto();
        d.setId(e.getId());
        d.setPeriodo(e.getPeriodo());
        d.setRazonSocial(e.getRazonSocial());
        d.setNumeroDocumento(e.getNumeroDocumento());
        d.setEstado(e.getEstado());
        PilaPlanillaEntity p = primeraPlanilla(e);
        if (p != null) {
            d.setTotalEmpleados(p.getTotalEmpleados());
            d.setTotalNomina(p.getTotalNomina());
        }
        return d;
    }

    private CotizanteDto aDto(PilaCotizanteEntity c) {
        CotizanteDto d = new CotizanteDto();
        d.setSecuencia(c.getSecuencia());
        d.setNombreCompleto(nombreCompleto(c));
        d.setTipoDocumento(c.getTipoDocumento());
        d.setNumeroIdentificacion(c.getNumeroIdentificacion());
        d.setTipoCotizante(c.getTipoCotizante());
        d.setDiasCotizadosSalud(c.getDiasCotizadosSalud());
        d.setDiasCotizadosPension(c.getDiasCotizadosPension());
        d.setIbcSalud(c.getIbcSalud());
        d.setIbcPension(c.getIbcPension());
        d.setAporteSalud(c.getAporteSalud());
        d.setAportePension(c.getAportePension());
        d.setAporteRiesgos(c.getAporteRiesgos());
        d.setAporteCcf(c.getAporteCcf());
        d.setCodEps(c.getCodEps());
        d.setCodAfp(c.getCodAfp());
        d.setCodArl(c.getCodArl());
        d.setCodCcf(c.getCodCcf());
        d.setIngreso(Boolean.TRUE.equals(c.getIng()));
        d.setRetiro(Boolean.TRUE.equals(c.getRet()));
        return d;
    }

    private String nombreCompleto(PilaCotizanteEntity c) {
        return String.join(" ", java.util.stream.Stream.of(
                        c.getApellido1(), c.getApellido2(), c.getNombre1(), c.getNombre2())
                .filter(s -> s != null && !s.isBlank())
                .toList());
    }
}
