package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.nomina.certificado.CertificadoIngresosDto;
import com.cloud_technological.aura_pos.dto.nomina.certificado.DesprendibleDto;
import com.cloud_technological.aura_pos.entity.CertificadoEmitidoEntity;
import com.cloud_technological.aura_pos.entity.CertificadoEmitidoEntity.Tipo;
import com.cloud_technological.aura_pos.entity.ConceptoNominaEntity;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.NominaDetalleEntity;
import com.cloud_technological.aura_pos.entity.NominaEntity;
import com.cloud_technological.aura_pos.entity.TerceroEntity;
import com.cloud_technological.aura_pos.repositories.empresas.EmpresaJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina.CertificadoEmitidoJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina.NominaDetalleJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina.NominaJPARepository;
import com.cloud_technological.aura_pos.repositories.terceros.TerceroJPARepository;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Certificados y desprendible (Fase 10).
 *
 * <p>Depende enteramente de la Fase 3: sin {@code nomina_detalle} con
 * {@code traza} no hay nada que imprimir. Los campos agregados de
 * {@code nomina} dan totales, no explicaciones.
 *
 * <h2>Los certificados se archivan, no se regeneran</h2>
 * Un certificado tiene valor probatorio. Si el empleado vuelve por el mismo
 * dentro de un año, recibe <b>exactamente el mismo documento</b> — aunque los
 * datos hayan cambiado. Por eso {@code contenido_json} es un snapshot.
 */
@Slf4j
@Service
public class CertificadoService {

    /** Conceptos que suman al renglón "pagos por salarios" del formato 220. */
    private static final List<String> CONCEPTOS_SALARIOS =
            List.of("SALARIO", "AUX_TRANSP", "PRIMA", "VACACIONES", "BONIF_RETIRO");
    private static final List<String> CONCEPTOS_CESANTIAS =
            List.of("CESANTIAS", "INT_CESANTIAS");
    private static final List<String> CONCEPTOS_APORTE_SALUD = List.of("DED_SALUD");
    private static final List<String> CONCEPTOS_APORTE_PENSION = List.of("DED_PENSION");
    private static final List<String> CONCEPTOS_FSP = List.of("DED_FONDO_SOLIDARIDAD");
    private static final List<String> CONCEPTOS_RETENCION =
            List.of("RETEFUENTE_P1", "RETEFUENTE_P2");

    private final CertificadoEmitidoJPARepository certRepo;
    private final NominaDetalleJPARepository detalleRepo;
    private final NominaJPARepository nominaRepo;
    private final TerceroJPARepository terceroRepo;
    private final EmpresaJPARepository empresaRepo;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CertificadoService(CertificadoEmitidoJPARepository certRepo,
                              NominaDetalleJPARepository detalleRepo,
                              NominaJPARepository nominaRepo,
                              TerceroJPARepository terceroRepo,
                              EmpresaJPARepository empresaRepo) {
        this.certRepo = certRepo;
        this.detalleRepo = detalleRepo;
        this.nominaRepo = nominaRepo;
        this.terceroRepo = terceroRepo;
        this.empresaRepo = empresaRepo;
    }

    // ── Desprendible ────────────────────────────────────────────────────────

    /**
     * Desprendible de una nómina.
     *
     * <p>A diferencia de los certificados anuales, este SÍ se puede regenerar:
     * refleja una nómina que ya está congelada. Si la nómina cambia, el
     * desprendible debe cambiar con ella.
     */
    @Transactional(readOnly = true)
    public DesprendibleDto desprendible(Long nominaId, Integer empresaId) {
        NominaEntity n = nominaRepo.findByIdAndEmpresaId(nominaId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Nómina no encontrada"));

        List<NominaDetalleEntity> detalle = detalleRepo.findParaDesprendible(nominaId);
        if (detalle.isEmpty()) {
            throw new GlobalException(HttpStatus.CONFLICT,
                    "La nómina " + nominaId + " no tiene detalle. ¿Se liquidó con el motor nuevo?");
        }

        DesprendibleDto d = new DesprendibleDto();
        d.setNominaId(nominaId);

        EmpresaEntity empresa = empresaRepo.findById(empresaId).orElse(null);
        if (empresa != null) {
            d.setEmpresaRazonSocial(empresa.getRazonSocial());
            d.setEmpresaNit(empresa.getNit());
        }

        if (n.getEmpleado() != null) {
            d.setEmpleadoNombre(n.getEmpleado().getNombreCompletoResuelto());
            d.setEmpleadoDocumento(n.getEmpleado().getNumeroDocumentoResuelto());
            d.setCargo(n.getEmpleado().getCargo());
            d.setBanco(n.getEmpleado().getBancoResuelto());
            d.setTipoCuenta(n.getEmpleado().getTipoCuentaResuelto());
            d.setNumeroCuenta(n.getEmpleado().getNumeroCuentaResuelto());
        }

        if (n.getPeriodo() != null) {
            d.setPeriodoInicio(n.getPeriodo().getFechaInicio());
            d.setPeriodoFin(n.getPeriodo().getFechaFin());
        }
        d.setDiasTrabajados(n.getDiasTrabajados());
        d.setSalarioBase(n.getSalarioBase());

        for (NominaDetalleEntity linea : detalle) {
            ConceptoNominaEntity c = linea.getConcepto();
            // Aportes del empleador y provisiones NO van al desprendible: son
            // costo de la empresa, no algo que el empleado devengue o se le
            // deduzca. Verlos ahí solo confunde.
            if (!ConceptoNominaEntity.Clase.DEVENGADO.equals(c.getClase())
                && !ConceptoNominaEntity.Clase.DEDUCCION.equals(c.getClase())) continue;

            DesprendibleDto.Linea l = new DesprendibleDto.Linea();
            l.setConcepto(c.getNombre());
            l.setCantidad(linea.getCantidad());
            l.setBase(linea.getBase());
            l.setPorcentaje(linea.getPorcentaje());
            l.setValor(linea.getValor());
            l.setTraza(linea.getTraza());   // el desglose paso a paso

            if (ConceptoNominaEntity.Clase.DEVENGADO.equals(c.getClase())) d.getDevengados().add(l);
            else d.getDeducciones().add(l);
        }

        d.setTotalDevengado(nz(n.getTotalDevengado()));
        d.setTotalDeducciones(nz(n.getTotalDeducciones()));
        d.setNetoPagar(nz(n.getNetoPagar()));
        return d;
    }

    // ── Certificado de ingresos y retenciones (formato 220) ─────────────────

    /**
     * Certificado anual.
     *
     * <p>Si ya se emitió, devuelve el mismo: es un documento con valor
     * probatorio y no puede cambiar entre una consulta y otra.
     */
    @Transactional
    public CertificadoIngresosDto certificadoIngresos(Long terceroId, Integer agno, Integer empresaId) {
        var previos = certRepo.findAnual(terceroId, Tipo.INGRESOS_RETENCIONES, agno);
        if (!previos.isEmpty()) {
            log.debug("Certificado de ingresos {} del tercero {} ya emitido: se devuelve el archivado.",
                    agno, terceroId);
            return deserializar(previos.get(0).getContenidoJson());
        }

        TerceroEntity t = terceroRepo.findByIdAndEmpresaId(terceroId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Tercero no encontrado"));

        CertificadoIngresosDto c = new CertificadoIngresosDto();
        c.setAgno(agno);

        EmpresaEntity empresa = empresaRepo.findById(empresaId).orElse(null);
        if (empresa != null) {
            c.setEmpresaRazonSocial(empresa.getRazonSocial());
            c.setEmpresaNit(empresa.getNit());
        }

        c.setTipoDocumento(t.getTipoDocumento());
        c.setNumeroDocumento(t.getNumeroDocumento());
        c.setApellido1(t.getApellido1());
        c.setApellido2(t.getApellido2());
        c.setNombre1(t.getNombre1());
        c.setNombre2(t.getNombre2());

        // Los renglones salen de agregar nomina_detalle del año por concepto.
        c.setPagosSalarios(sumar(terceroId, CONCEPTOS_SALARIOS, agno));
        c.setCesantiasEIntereses(sumar(terceroId, CONCEPTOS_CESANTIAS, agno));
        c.setTotalIngresos(c.getPagosSalarios()
                .add(c.getCesantiasEIntereses())
                .add(nz(c.getGastosRepresentacion()))
                .add(nz(c.getOtrosPagos())));

        c.setAportesSalud(sumar(terceroId, CONCEPTOS_APORTE_SALUD, agno));
        c.setAportesPension(sumar(terceroId, CONCEPTOS_APORTE_PENSION, agno));
        c.setAportesFondoSolidaridad(sumar(terceroId, CONCEPTOS_FSP, agno));
        c.setRetencionPracticada(sumar(terceroId, CONCEPTOS_RETENCION, agno));

        // Archivar el snapshot: a partir de aquí, este es EL certificado del año.
        archivar(t, empresaId, Tipo.INGRESOS_RETENCIONES, agno, null, c);
        return c;
    }

    private BigDecimal sumar(Long terceroId, List<String> codigos, Integer agno) {
        BigDecimal v = certRepo.sumarAnualPorConceptos(terceroId, codigos, agno);
        return v != null ? v : BigDecimal.ZERO;
    }

    private void archivar(TerceroEntity t, Integer empresaId, String tipo,
                          Integer agno, Long nominaId, Object contenido) {
        CertificadoEmitidoEntity e = new CertificadoEmitidoEntity();
        e.setEmpresaId(empresaId);
        e.setTercero(t);
        e.setTipo(tipo);
        e.setAgno(agno);
        e.setNominaId(nominaId);
        try {
            e.setContenidoJson(objectMapper.writeValueAsString(contenido));
        } catch (Exception ex) {
            // No romper la emisión, pero avisar: sin snapshot, el certificado
            // se regeneraría distinto la próxima vez.
            log.error("No se pudo archivar el contenido del certificado: {}", ex.getMessage());
        }
        certRepo.save(e);
    }

    private CertificadoIngresosDto deserializar(String json) {
        try {
            return objectMapper.readValue(json, CertificadoIngresosDto.class);
        } catch (Exception e) {
            throw new GlobalException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "El certificado archivado no se pudo leer: " + e.getMessage());
        }
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
