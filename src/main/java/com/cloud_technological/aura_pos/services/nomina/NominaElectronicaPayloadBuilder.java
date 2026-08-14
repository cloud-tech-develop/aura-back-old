package com.cloud_technological.aura_pos.services.nomina;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.dto.nomina.electronica.NominaElectronicaPayload;
import com.cloud_technological.aura_pos.dto.nomina.electronica.NominaElectronicaPayload.Concepto;
import com.cloud_technological.aura_pos.entity.ConceptoNominaEntity;
import com.cloud_technological.aura_pos.entity.ContratoLaboralEntity;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.NominaDetalleEntity;
import com.cloud_technological.aura_pos.entity.NominaEntity;
import com.cloud_technological.aura_pos.entity.TerceroEntity;
import com.cloud_technological.aura_pos.repositories.nomina.NominaDetalleJPARepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Arma el payload de nómina electrónica desde {@code nomina_detalle}.
 *
 * <h2>Por qué la Fase 3 era prerrequisito</h2>
 * La DIAN exige cada devengado y deducción <b>en su etiqueta específica</b>
 * (Basico, Transporte, HEDs, Vacaciones, Prima, Cesantias, Salud, FondoPension,
 * RetencionFuente...), no un total. Sin {@code nomina_detalle} con conceptos
 * tipificados, esto sería un {@code if} gigante mapeando campos agregados a
 * etiquetas — inmantenible y frágil ante cada concepto nuevo.
 *
 * <p>Con el catálogo, el mapeo es una lectura de {@code concepto.codigoDian}.
 */
@Slf4j
@Component
public class NominaElectronicaPayloadBuilder {

    private final NominaDetalleJPARepository detalleRepo;

    public NominaElectronicaPayloadBuilder(NominaDetalleJPARepository detalleRepo) {
        this.detalleRepo = detalleRepo;
    }

    public NominaElectronicaPayload construir(NominaEntity nomina,
                                              ContratoLaboralEntity contrato,
                                              EmpresaEntity empresa,
                                              Long consecutivo,
                                              String prefijo) {
        NominaElectronicaPayload p = new NominaElectronicaPayload();
        p.setPrefijo(prefijo);
        p.setConsecutivo(consecutivo);
        p.setFechaGeneracion(LocalDate.now());

        LocalDate fin = nomina.getPeriodo().getFechaFin();
        p.setAgno(fin.getYear());
        p.setMes(fin.getMonthValue());

        armarPeriodo(p, nomina, contrato);
        armarEmpleador(p, empresa);
        armarTrabajador(p, nomina, contrato);
        armarPago(p, nomina, contrato);
        armarConceptos(p, nomina);

        return p;
    }

    private void armarPeriodo(NominaElectronicaPayload p, NominaEntity nomina, ContratoLaboralEntity contrato) {
        var periodo = p.getPeriodo();
        periodo.setFechaIngreso(contrato.getFechaInicio());
        periodo.setFechaRetiro(contrato.getFechaFin());
        periodo.setFechaLiquidacionInicio(nomina.getPeriodo().getFechaInicio());
        periodo.setFechaLiquidacionFin(nomina.getPeriodo().getFechaFin());
        periodo.setTiempoLaborado(nomina.getDiasTrabajados());
    }

    private void armarEmpleador(NominaElectronicaPayload p, EmpresaEntity empresa) {
        var e = p.getEmpleador();
        e.setRazonSocial(empresa.getRazonSocial());
        e.setNit(empresa.getNit());
        e.setDv(empresa.getDv());
        e.setMunicipio(empresa.getMunicipio());
    }

    /**
     * Datos del trabajador desde {@code tercero} (Fase 1).
     *
     * <p>Los cuatro componentes del nombre van por separado — no se parten aquí
     * con heurística. Si no están poblados, se avisa: es un documento que la
     * DIAN va a rechazar, y es mejor saberlo antes de enviarlo.
     */
    private void armarTrabajador(NominaElectronicaPayload p, NominaEntity nomina, ContratoLaboralEntity contrato) {
        var t = p.getTrabajador();
        TerceroEntity tercero = nomina.getEmpleado() != null ? nomina.getEmpleado().getTercero() : null;

        if (tercero == null) {
            log.warn("Nómina {} sin tercero: el documento saldrá incompleto. "
                    + "Completar la reconciliación de V99.", nomina.getId());
            return;
        }

        t.setTipoDocumento(tercero.getTipoDocumento());
        t.setNumeroDocumento(tercero.getNumeroDocumento());
        t.setPrimerApellido(tercero.getApellido1());
        t.setSegundoApellido(tercero.getApellido2());
        t.setPrimerNombre(tercero.getNombre1());
        t.setOtrosNombres(tercero.getNombre2());

        if (tercero.getNombre1() == null || tercero.getApellido1() == null) {
            log.warn("Tercero {} sin identificación desagregada (nombre1/apellido1). "
                    + "La DIAN exige los cuatro componentes por separado; este documento "
                    + "va a ser rechazado.", tercero.getId());
        }

        t.setSalarioIntegral(Boolean.TRUE.equals(contrato.getEsSalarioIntegral()));
        t.setTipoContrato(contrato.getTipoContrato());
        t.setSueldo(contrato.getSalarioBase());
        t.setLugarTrabajoMunicipio(tercero.getMunicipio());
    }

    private void armarPago(NominaElectronicaPayload p, NominaEntity nomina, ContratoLaboralEntity contrato) {
        var pago = p.getPago();
        if (nomina.getEmpleado() != null) {
            pago.setBanco(nomina.getEmpleado().getBancoResuelto());
            pago.setTipoCuenta(nomina.getEmpleado().getTipoCuentaResuelto());
            pago.setNumeroCuenta(nomina.getEmpleado().getNumeroCuentaResuelto());
        }
    }

    /**
     * Devengados y deducciones desde el detalle.
     *
     * <p>Los aportes del empleador y las provisiones <b>no</b> van al documento:
     * la DIAN reporta lo que el trabajador devenga y lo que se le deduce, no el
     * costo de la empresa.
     */
    private void armarConceptos(NominaElectronicaPayload p, NominaEntity nomina) {
        List<NominaDetalleEntity> detalle = detalleRepo.findParaDesprendible(nomina.getId());

        if (detalle.isEmpty()) {
            log.warn("Nómina {} sin detalle: no se puede armar el documento. "
                    + "¿Se liquidó con el motor nuevo?", nomina.getId());
            return;
        }

        for (NominaDetalleEntity d : detalle) {
            ConceptoNominaEntity c = d.getConcepto();
            if (c.getCodigoDian() == null) continue;   // no reportable a la DIAN

            Concepto item = new Concepto(c.getCodigoDian(), c.getNombre(), d.getValor());
            item.setCantidad(d.getCantidad());
            item.setPorcentaje(d.getPorcentaje());

            switch (c.getClase()) {
                case ConceptoNominaEntity.Clase.DEVENGADO -> p.getDevengados().add(item);
                case ConceptoNominaEntity.Clase.DEDUCCION -> p.getDeducciones().add(item);
                default -> { /* APORTE_EMPLEADOR y PROVISION no se reportan */ }
            }
        }

        p.setTotalDevengado(nomina.getTotalDevengado());
        p.setTotalDeducciones(nomina.getTotalDeducciones());
        p.setComprobanteTotal(nomina.getNetoPagar());
    }
}
