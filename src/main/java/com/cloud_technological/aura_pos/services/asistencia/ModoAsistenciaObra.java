package com.cloud_technological.aura_pos.services.asistencia;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.entity.NominaConfigEntity;
import com.cloud_technological.aura_pos.repositories.nomina.NominaConfigJPARepository;
import com.cloud_technological.aura_pos.utils.GlobalException;

import lombok.RequiredArgsConstructor;

/**
 * Cómo trabaja la empresa la asistencia por obra (Configuración de nómina, V243):
 * si divide los proyectos en frentes, si exige soporte para enviar un día a
 * revisión y si las horas se registran a mano, con la plantilla de Excel o de
 * las dos formas. Sin configuración guardada se usa lo de siempre (frentes,
 * soporte obligatorio y ambas formas).
 */
@Component
@RequiredArgsConstructor
public class ModoAsistenciaObra {

    public static final String MANUAL = "MANUAL";
    public static final String EXCEL = "EXCEL";
    public static final String AMBOS = "AMBOS";

    /** Lo que la empresa eligió. */
    public record Modo(boolean usaFrentes, boolean exigeSoporte, String registro, boolean requiereAprobacion) {

        public boolean permiteManual() {
            return !EXCEL.equals(registro);
        }

        public boolean permiteExcel() {
            return !MANUAL.equals(registro);
        }
    }

    private final NominaConfigJPARepository configRepo;

    /** Modo de la empresa; sin configuración, el de siempre. */
    public Modo de(Integer empresaId) {
        return configRepo.findByEmpresaId(empresaId)
                .map(ModoAsistenciaObra::desde)
                .orElse(new Modo(true, true, AMBOS, true));
    }

    /** 400 si la empresa registra las horas solo con la plantilla de Excel. */
    public void exigirManual(Integer empresaId) {
        if (!de(empresaId).permiteManual())
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "La empresa registra las horas con la plantilla de Excel del mes (Configuración de nómina → Asistencia por obra).");
    }

    /** 400 si la empresa registra las horas solo a mano. */
    public void exigirExcel(Integer empresaId) {
        if (!de(empresaId).permiteExcel())
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "La empresa registra las horas día por día; la importación de Excel está desactivada (Configuración de nómina → Asistencia por obra).");
    }

    /** 400 si la empresa trabaja por proyecto sin frentes. */
    public void exigirFrentes(Integer empresaId) {
        if (!de(empresaId).usaFrentes())
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "La empresa trabaja por proyecto sin frentes. Para crear frentes actívelo en Configuración de nómina → Asistencia por obra.");
    }

    /** ¿El valor es un modo de registro válido? */
    public static boolean registroValido(String registro) {
        return MANUAL.equals(registro) || EXCEL.equals(registro) || AMBOS.equals(registro);
    }

    private static Modo desde(NominaConfigEntity c) {
        return new Modo(!Boolean.FALSE.equals(c.getAsistenciaUsaFrentes()),
                !Boolean.FALSE.equals(c.getAsistenciaExigeSoporte()),
                c.getAsistenciaRegistro() != null ? c.getAsistenciaRegistro() : AMBOS,
                !Boolean.FALSE.equals(c.getAsistenciaRequiereAprobacion()));
    }
}
