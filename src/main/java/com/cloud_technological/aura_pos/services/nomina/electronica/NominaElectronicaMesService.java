package com.cloud_technological.aura_pos.services.nomina.electronica;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.nomina.electronica.NominaElectronicaMesDto;
import com.cloud_technological.aura_pos.entity.ContratoLaboralEntity;
import com.cloud_technological.aura_pos.entity.NominaElectronicaEntity;
import com.cloud_technological.aura_pos.entity.NominaEntity;
import com.cloud_technological.aura_pos.repositories.nomina.NominaElectronicaJPARepositories.NominaElectronicaRepo;
import com.cloud_technological.aura_pos.repositories.nomina.NominaElectronicaMesQueryRepository;
import com.cloud_technological.aura_pos.repositories.nomina.NominaElectronicaMesQueryRepository.ContratoMes;
import com.cloud_technological.aura_pos.repositories.nomina.NominaElectronicaMesQueryRepository.NominaMes;
import com.cloud_technological.aura_pos.repositories.nomina.NominaJPARepository;
import com.cloud_technological.aura_pos.utils.GlobalException;

import lombok.RequiredArgsConstructor;

/**
 * Nómina electrónica MENSUAL: la DIAN recibe un solo documento por empleado y mes,
 * con todo lo pagado. Este servicio arma el mes de un empleado (sus nóminas
 * confirmadas), dice si ya está completo para enviarlo y da la referencia y el
 * documento electrónico del mes.
 */
@Service
@RequiredArgsConstructor
public class NominaElectronicaMesService {

    private static final DateTimeFormatter AAAAMM = DateTimeFormatter.ofPattern("yyyyMM");
    private static final String TERMINADO = "TERMINADO";
    private static final String DEFINITIVA = "LIQUIDACION_DEFINITIVA";
    /** Clases que cubren días del mes; prima, cesantías u otros pagos no. */
    private static final List<String> PERIODICAS = List.of("PERIODICO", DEFINITIVA);

    private final NominaElectronicaMesQueryRepository queryRepository;
    private final NominaJPARepository nominaRepo;
    private final NominaElectronicaRepo neRepo;

    /**
     * El mes de la nómina dada (el de su período): todas las nóminas confirmadas del
     * contrato en ese mes y si lo cubren completo. Si la nómina aún no está confirmada
     * igual se usa su mes, pero ella no entra hasta que se confirme.
     */
    @Transactional(readOnly = true)
    public NominaElectronicaMes mesDe(NominaEntity nomina, Integer empresaId) {
        if (nomina.getPeriodo() == null || nomina.getPeriodo().getFechaFin() == null)
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La nómina no tiene período.");
        ContratoLaboralEntity contrato = nomina.getContrato();
        YearMonth mes = YearMonth.from(nomina.getPeriodo().getFechaFin());
        List<NominaMes> resumen = queryRepository.nominasDelMes(empresaId, contrato.getId(), mes.atDay(1),
                mes.atEndOfMonth());

        Map<Long, NominaEntity> porId = new HashMap<>();
        nominaRepo.findAllById(resumen.stream().map(NominaMes::id).toList()).forEach(n -> porId.put(n.getId(), n));
        List<NominaEntity> nominas = new ArrayList<>();
        for (NominaMes r : resumen)
            if (porId.containsKey(r.id())) nominas.add(porId.get(r.id()));

        List<NominaMes> periodicas = resumen.stream().filter(r -> PERIODICAS.contains(r.clase())).toList();
        boolean definitiva = resumen.stream().anyMatch(r -> DEFINITIVA.equals(r.clase()));
        CoberturaMes cobertura = CoberturaMes.evaluar(mes,
                periodicas.stream().map(NominaMes::periodoInicio).min(LocalDate::compareTo).orElse(null),
                periodicas.stream().map(NominaMes::periodoFin).max(LocalDate::compareTo).orElse(null),
                contrato.getFechaInicio(), contrato.getFechaFin(),
                definitiva || TERMINADO.equals(contrato.getEstado()));
        return new NominaElectronicaMes(mes, contrato, nominas, resumen, cobertura);
    }

    /** 409 con el motivo si el mes del empleado aún no está completo. */
    public void exigirCompleto(NominaElectronicaMes m) {
        if (m.nominas().isEmpty())
            throw new GlobalException(HttpStatus.CONFLICT,
                    "Mes incompleto: la nómina debe estar confirmada para enviar la nómina electrónica.");
        if (!m.cobertura().completo())
            throw new GlobalException(HttpStatus.CONFLICT, "Mes incompleto: " + m.cobertura().motivo()
                    + " La nómina electrónica es una sola por mes y se envía cuando el mes está completo.");
    }

    /** Documento electrónico original más reciente del contrato en el mes. */
    @Transactional(readOnly = true)
    public Optional<NominaElectronicaEntity> documentoDelMes(Integer empresaId, Long contratoId, YearMonth mes) {
        return queryRepository.documentoDelMes(empresaId, contratoId, mes.getYear(), mes.getMonthValue())
                .flatMap(neRepo::findById);
    }

    /**
     * reference_code del documento del mes: NE-{contrato}-{aaaamm}. Si el anterior se
     * eliminó con nota de ajuste, NE-{contrato}-{aaaamm}-2, -3… (Factus no deja repetir
     * la referencia: si la repite, devuelve el documento viejo).
     */
    public String referencia(Integer empresaId, Long contratoId, YearMonth mes) {
        long anulados = queryRepository.anuladosDelMes(empresaId, contratoId, mes.getYear(), mes.getMonthValue());
        String base = "NE-" + contratoId + "-" + mes.format(AAAAMM);
        return anulados == 0 ? base : base + "-" + (anulados + 1);
    }

    /** El mes como DTO para la modal. */
    public NominaElectronicaMesDto aDto(NominaElectronicaMes m, Integer empresaId) {
        NominaElectronicaMesDto d = new NominaElectronicaMesDto();
        d.setContratoId(m.contrato().getId());
        d.setAgno(m.mes().getYear());
        d.setMes(m.mes().getMonthValue());
        d.setCompleto(!m.nominas().isEmpty() && m.cobertura().completo());
        d.setMotivo(m.nominas().isEmpty() ? "La nómina debe estar confirmada." : m.cobertura().motivo());
        if (!m.nominas().isEmpty()) {
            NominaEntity ultima = m.ultima();
            d.setUltimaNominaId(ultima.getId());
            if (ultima.getEmpleado() != null) {
                d.setEmpleadoNombre(ultima.getEmpleado().getNombreCompletoResuelto());
                d.setEmpleadoDocumento(ultima.getEmpleado().getNumeroDocumentoResuelto());
            }
        }
        d.setTotalDevengado(m.totalDevengado());
        d.setNominas(m.resumen().stream().map(NominaElectronicaMesService::aNominaDelMes).toList());
        d.setCantidadNominas(m.resumen().size());
        documentoDelMes(empresaId, m.contrato().getId(), m.mes()).ifPresent(ne -> {
            d.setDocumentoId(ne.getId());
            d.setDocumentoEstado(ne.getEstado());
            if (ne.getConsecutivo() != null)
                d.setDocumentoNumero((ne.getPrefijo() != null ? ne.getPrefijo() : "") + ne.getConsecutivo());
            d.setCune(ne.getCune());
        });
        return d;
    }

    /** Vista por mes: cada empleado con nóminas confirmadas en el mes y su estado. */
    @Transactional(readOnly = true)
    public List<NominaElectronicaMesDto> listar(Integer empresaId, YearMonth mes) {
        List<ContratoMes> filas = queryRepository.contratosDelMes(empresaId, mes.getYear(), mes.getMonthValue(),
                mes.atDay(1), mes.atEndOfMonth());
        List<NominaElectronicaMesDto> out = new ArrayList<>();
        for (ContratoMes c : filas) {
            CoberturaMes cob = CoberturaMes.evaluar(mes, c.coberturaInicio(), c.coberturaFin(), c.contratoInicio(),
                    c.contratoFin(), c.definitiva() || TERMINADO.equals(c.contratoEstado()));
            NominaElectronicaMesDto d = new NominaElectronicaMesDto();
            d.setContratoId(c.contratoId());
            d.setEmpleadoNombre(c.empleadoNombre());
            d.setEmpleadoDocumento(c.empleadoDocumento());
            d.setAgno(mes.getYear());
            d.setMes(mes.getMonthValue());
            d.setCompleto(cob.completo());
            d.setMotivo(cob.motivo());
            d.setUltimaNominaId(c.ultimaNominaId());
            d.setTotalDevengado(c.totalDevengado());
            d.setDocumentoId(c.neId());
            d.setDocumentoEstado(c.neEstado());
            d.setDocumentoNumero(c.neNumero());
            d.setCune(c.neCune());
            d.setNominas(List.of());
            d.setCantidadNominas(c.nominas());
            out.add(d);
        }
        return out;
    }

    private static NominaElectronicaMesDto.NominaDelMes aNominaDelMes(NominaMes r) {
        NominaElectronicaMesDto.NominaDelMes n = new NominaElectronicaMesDto.NominaDelMes();
        n.setNominaId(r.id());
        n.setNumero(r.numero());
        n.setPeriodoInicio(r.periodoInicio());
        n.setPeriodoFin(r.periodoFin());
        n.setClase(r.clase());
        n.setTotalDevengado(r.totalDevengado());
        return n;
    }
}
