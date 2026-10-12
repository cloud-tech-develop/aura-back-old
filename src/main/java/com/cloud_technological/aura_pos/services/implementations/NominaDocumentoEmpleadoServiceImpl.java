package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.nomina.comprobante.TipoComprobanteTableDto;
import com.cloud_technological.aura_pos.dto.nomina.documento.AgregarEmpleadosDto;
import com.cloud_technological.aura_pos.dto.nomina.documento.CandidatoNominaDto;
import com.cloud_technological.aura_pos.dto.nomina.documento.EmpleadoDocumentoDto;
import com.cloud_technological.aura_pos.dto.nomina.documento.ResultadoAgregarEmpleadosDto;
import com.cloud_technological.aura_pos.entity.ContratoLaboralEntity;
import com.cloud_technological.aura_pos.entity.EmpleadoEntity;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.NominaDocumentoEntity;
import com.cloud_technological.aura_pos.entity.NominaEntity;
import com.cloud_technological.aura_pos.entity.PeriodoNominaEntity;
import com.cloud_technological.aura_pos.repositories.nomina.NominaJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina_documentos.NominaDocumentoEmpleadoQueryRepository;
import com.cloud_technological.aura_pos.repositories.nomina_documentos.NominaDocumentoJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina_documentos.NominaDocumentoLiquidacionQueryRepository;
import com.cloud_technological.aura_pos.services.NominaDocumentoEmpleadoService;
import com.cloud_technological.aura_pos.services.NominaTipoComprobanteService;
import com.cloud_technological.aura_pos.services.nomina.comprobante.ClaseComprobanteNomina;
import com.cloud_technological.aura_pos.services.nomina.documento.TipoListaNomina;
import com.cloud_technological.aura_pos.utils.GlobalException;

/** Implementación de {@link NominaDocumentoEmpleadoService}. */
@Service
public class NominaDocumentoEmpleadoServiceImpl implements NominaDocumentoEmpleadoService {

    /** Estado de una fila aún sin liquidar (el motor la deja igual hasta confirmar). */
    private static final String SIN_LIQUIDAR = "BORRADOR";

    private final NominaDocumentoJPARepository documentoRepository;
    private final NominaDocumentoEmpleadoQueryRepository queryRepository;
    private final NominaJPARepository nominaRepository;
    private final NominaTipoComprobanteService tiposComprobante;
    private final NominaDocumentoLiquidacionQueryRepository liquidacionQuery;

    public NominaDocumentoEmpleadoServiceImpl(NominaDocumentoJPARepository documentoRepository,
            NominaDocumentoEmpleadoQueryRepository queryRepository,
            NominaJPARepository nominaRepository,
            NominaTipoComprobanteService tiposComprobante,
            NominaDocumentoLiquidacionQueryRepository liquidacionQuery) {
        this.documentoRepository = documentoRepository;
        this.queryRepository = queryRepository;
        this.nominaRepository = nominaRepository;
        this.tiposComprobante = tiposComprobante;
        this.liquidacionQuery = liquidacionQuery;
    }

    @Override
    public List<EmpleadoDocumentoDto> listar(Long documentoId, Integer empresaId) {
        buscar(documentoId, empresaId);
        return queryRepository.empleados(documentoId);
    }

    @Override
    public List<CandidatoNominaDto> candidatos(Long documentoId, Integer empresaId) {
        NominaDocumentoEntity d = buscar(documentoId, empresaId);
        return evaluar(d, empresaId, TipoListaNomina.TODOS, List.of());
    }

    @Override
    @Transactional
    public ResultadoAgregarEmpleadosDto agregar(Long documentoId, AgregarEmpleadosDto dto, Integer empresaId) {
        NominaDocumentoEntity d = buscarPreliminar(documentoId, empresaId);
        TipoComprobanteTableDto tipo = tiposComprobante.obtener(d.getTipoComprobanteId(), empresaId);
        if (ClaseComprobanteNomina.LIQUIDACION_DEFINITIVA.name().equals(tipo.getClase()))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "La liquidación definitiva se genera desde Retiros, con el empleado retirado.");
        TipoListaNomina tipoLista = TipoListaNomina.valueOf(dto.getTipoLista());
        List<Long> ids = dto.getIds() == null ? List.of() : dto.getIds();
        if (tipoLista.exigeIds() && ids.isEmpty())
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Elija qué agregar.");

        List<CandidatoNominaDto> candidatos = evaluar(d, empresaId, tipoLista, ids);
        if (candidatos.isEmpty())
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "No hay vinculaciones activas en el período " + d.getFechaDesde() + " a " + d.getFechaHasta()
                    + " para lo que eligió.");

        ResultadoAgregarEmpleadosDto r = new ResultadoAgregarEmpleadosDto();
        for (CandidatoNominaDto c : candidatos) {
            if (c.getMotivo() != null) {
                r.getOmitidos().add(c);
                continue;
            }
            nominaRepository.save(nuevaFila(d, c, empresaId));
            r.setAgregados(r.getAgregados() + 1);
        }
        return r;
    }

    @Override
    @Transactional
    public void agregarRetirado(Long documentoId, Long contratoId, Long empleadoId, BigDecimal salarioBase,
            Integer empresaId) {
        NominaDocumentoEntity d = buscarPreliminar(documentoId, empresaId);
        CandidatoNominaDto c = new CandidatoNominaDto();
        c.setContratoId(contratoId);
        c.setEmpleadoId(empleadoId);
        c.setSalarioBase(salarioBase);
        nominaRepository.save(nuevaFila(d, c, empresaId));
    }

    @Override
    @Transactional
    public void quitar(Long documentoId, List<Long> nominaIds, Integer empresaId) {
        buscarPreliminar(documentoId, empresaId);
        queryRepository.quitar(documentoId, nominaIds);
        // Lo liquidado de quienes salen deja de sumar en el encabezado.
        liquidacionQuery.recalcularTotales(documentoId);
    }

    // ── Apoyo ────────────────────────────────────────────────────────────────

    /** Candidatos del tipo de lista evaluados contra el documento y su comprobante. */
    private List<CandidatoNominaDto> evaluar(NominaDocumentoEntity d, Integer empresaId, TipoListaNomina tipoLista,
            List<Long> ids) {
        TipoComprobanteTableDto tipo = tiposComprobante.obtener(d.getTipoComprobanteId(), empresaId);
        boolean periodico = ClaseComprobanteNomina.valueOf(tipo.getClase()).esPeriodica();
        return queryRepository.candidatos(empresaId, d.getId(), d.getTipoComprobanteId(), periodico,
                d.getFechaDesde(), d.getFechaHasta(), tipoLista, ids);
    }

    /**
     * Fila de prenómina del empleado, sin liquidar. El salario se copia del contrato
     * para que la lista lo muestre; el motor lo recalcula al liquidar.
     */
    private static NominaEntity nuevaFila(NominaDocumentoEntity d, CandidatoNominaDto c, Integer empresaId) {
        EmpresaEntity empresa = new EmpresaEntity();
        empresa.setId(empresaId);
        PeriodoNominaEntity periodo = new PeriodoNominaEntity();
        periodo.setId(d.getPeriodoId());
        EmpleadoEntity empleado = new EmpleadoEntity();
        empleado.setId(c.getEmpleadoId());
        ContratoLaboralEntity contrato = new ContratoLaboralEntity();
        contrato.setId(c.getContratoId());

        NominaEntity n = new NominaEntity();
        n.setEmpresa(empresa);
        n.setPeriodo(periodo);
        n.setEmpleado(empleado);
        n.setContrato(contrato);
        n.setDocumentoId(d.getId());
        n.setEsPrenomina(Boolean.TRUE);
        n.setSalarioBase(c.getSalarioBase());
        n.setEstado(SIN_LIQUIDAR);
        n.setCreatedAt(LocalDateTime.now());
        return n;
    }

    /** La prenómina de la empresa; 404 si no existe. */
    private NominaDocumentoEntity buscar(Long id, Integer empresaId) {
        return documentoRepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Documento de nómina no encontrado"));
    }

    /** La prenómina en estado preliminar; 409 si no. */
    private NominaDocumentoEntity buscarPreliminar(Long id, Integer empresaId) {
        NominaDocumentoEntity d = buscar(id, empresaId);
        if (!"PRENOMINA".equals(d.getClaseDocumento()))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "La nómina no se modifica: los cambios se hacen en su prenómina.");
        if (!NominaDocumentoServiceImpl.PRELIMINAR.equals(d.getEstado()))
            throw new GlobalException(HttpStatus.CONFLICT,
                    "El documento está " + d.getEstado().toLowerCase() + ": sus empleados no se modifican.");
        return d;
    }
}
