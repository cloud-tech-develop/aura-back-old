package com.cloud_technological.aura_pos.contabilidad.infrastructure.devengo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.cloud_technological.aura_pos.contabilidad.infrastructure.event.DocumentoContabilizableEvent;
import com.cloud_technological.aura_pos.entity.CausacionEjecucionEntity;
import com.cloud_technological.aura_pos.entity.CausacionProgramadaEntity;
import com.cloud_technological.aura_pos.repositories.contabilidad.CausacionEjecucionJPARepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.CausacionProgramadaJPARepository;

import lombok.RequiredArgsConstructor;

/**
 * Causaciones programadas (E6): CRUD de plantillas y generación mensual del
 * asiento en BORRADOR. Idempotente por (causación, período).
 */
@Service
@RequiredArgsConstructor
public class CausacionService {

    private static final Logger log = LoggerFactory.getLogger(CausacionService.class);
    private static final DateTimeFormatter PERIODO = DateTimeFormatter.ofPattern("yyyy-MM");

    private final CausacionProgramadaJPARepository causacionRepo;
    private final CausacionEjecucionJPARepository ejecucionRepo;
    private final ApplicationEventPublisher eventPublisher;

    public List<CausacionProgramadaEntity> listar(Integer empresaId) {
        return causacionRepo.findByEmpresaIdOrderByNombreAsc(empresaId);
    }

    @Transactional
    public CausacionProgramadaEntity crear(Integer empresaId, CausacionProgramadaEntity dto) {
        validarPlantilla(dto);
        dto.setId(null);
        dto.setEmpresaId(empresaId);
        dto.getLineas().forEach(l -> l.setCausacion(dto));
        return causacionRepo.save(dto);
    }

    /** Diario a las 02:30: genera las causaciones cuyo día ya llegó este mes. */
    @Scheduled(cron = "0 30 2 * * *")
    public void generarDelDia() {
        int generadas = generar(LocalDate.now());
        if (generadas > 0) {
            log.info("Causaciones programadas: {} asiento(s) en borrador", generadas);
        }
    }

    /** Genera las causaciones pendientes del mes de la fecha (día ya cumplido). */
    @Transactional
    public int generar(LocalDate fecha) {
        String periodo = fecha.format(PERIODO);
        int generadas = 0;
        for (CausacionProgramadaEntity causacion : causacionRepo.findByActivaTrue()) {
            int dia = causacion.getDia() != null ? causacion.getDia() : 1;
            if (fecha.getDayOfMonth() < dia
                    || ejecucionRepo.existsByCausacionIdAndPeriodo(causacion.getId(), periodo)) {
                continue;
            }
            CausacionEjecucionEntity ejecucion = ejecucionRepo.save(
                    CausacionEjecucionEntity.builder()
                            .empresaId(causacion.getEmpresaId())
                            .causacionId(causacion.getId())
                            .periodo(periodo)
                            .build());
            eventPublisher.publishEvent(new DocumentoContabilizableEvent(
                    "CAUSACION", ejecucion.getId(), causacion.getEmpresaId(), null));
            generadas++;
        }
        return generadas;
    }

    /** La plantilla debe cuadrar desde su diseño: Σ débitos = Σ créditos. */
    private void validarPlantilla(CausacionProgramadaEntity dto) {
        if (dto.getNombre() == null || dto.getNombre().isBlank()
                || dto.getLineas() == null || dto.getLineas().size() < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La causación requiere nombre y al menos dos líneas.");
        }
        BigDecimal db = dto.getLineas().stream()
                .map(l -> l.getDebito() != null ? l.getDebito() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cr = dto.getLineas().stream()
                .map(l -> l.getCredito() != null ? l.getCredito() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (db.compareTo(cr) != 0 || db.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La plantilla no cuadra: débitos=" + db + " créditos=" + cr);
        }
    }
}
