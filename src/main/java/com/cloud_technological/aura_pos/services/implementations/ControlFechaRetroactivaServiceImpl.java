package com.cloud_technological.aura_pos.services.implementations;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.repositories.empresas.EmpresaJPARepository;
import com.cloud_technological.aura_pos.services.ControlFechaRetroactivaService;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

import lombok.RequiredArgsConstructor;

/**
 * Implementación del control de fecha retroactiva.
 *
 * <p>El orden de las comprobaciones importa: primero se descartan los casos sin
 * fricción (no sale de caja, o está dentro de la ventana), y solo después se
 * mira el rol y el motivo. Al revés, un cajero registrando la compra del día se
 * toparía con preguntas que no vienen al caso.
 */
@Service
@RequiredArgsConstructor
public class ControlFechaRetroactivaServiceImpl implements ControlFechaRetroactivaService {

    /** Un motivo de tres letras no explica nada; es saltarse el control. */
    private static final int MOTIVO_MINIMO = 10;

    private final EmpresaJPARepository empresaRepository;
    private final SecurityUtils securityUtils;

    @Override
    public Integer validar(Integer empresaId, LocalDate fechaDocumento, boolean saleDeCaja,
            String motivo, Long usuarioId, String documento) {

        // Las otras vías no mueven el arqueo: no hay nada que proteger.
        if (!saleDeCaja) {
            return null;
        }
        LocalDate fecha = fechaDocumento != null ? fechaDocumento : LocalDate.now();
        long diasAtras = ChronoUnit.DAYS.between(fecha, LocalDate.now());

        EmpresaEntity empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST,
                        "Empresa no encontrada"));

        int gracia = empresa.getDiasGraciaDocumentoRetroactivo() != null
                ? empresa.getDiasGraciaDocumentoRetroactivo() : 3;

        // Dentro de la ventana (y los documentos futuros o de hoy) pasan sin ruido.
        if (diasAtras <= gracia) {
            return null;
        }

        String rolActual = securityUtils.getRol();
        String rolAutoriza = empresa.getRolAutorizaRetroactivo() != null
                ? empresa.getRolAutorizaRetroactivo() : "ADMIN";
        boolean autoriza = rolAutoriza.equalsIgnoreCase(rolActual);

        // Con el bloqueo puesto, quien no autoriza no pasa: se le ofrecen las
        // salidas en vez de dejarlo con un error sin camino.
        if (Boolean.TRUE.equals(empresa.getBloquearCajaRetroactiva()) && !autoriza) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, mensajeBloqueo(documento, diasAtras));
        }
        if (!autoriza) {
            throw new GlobalException(HttpStatus.FORBIDDEN,
                    "El " + documento + " tiene " + diasAtras + " días de antigüedad y solo "
                            + rolAutoriza + " puede cargarlo a la caja. Pídele autorización o "
                            + "regístralo contra la caja menor, un banco o como cuenta por pagar");
        }
        if (motivo == null || motivo.trim().length() < MOTIVO_MINIMO) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Explica por qué este " + documento + " de hace " + diasAtras
                            + " días se carga a la caja de hoy. El motivo queda en el documento");
        }
        // Quien autoriza es quien está registrando: el rol ya se verificó.
        return usuarioId != null ? usuarioId.intValue() : null;
    }

    @Override
    public void exigirRolAutorizador(Integer empresaId, String accion) {
        EmpresaEntity empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST,
                        "Empresa no encontrada"));
        String rolAutoriza = empresa.getRolAutorizaRetroactivo() != null
                ? empresa.getRolAutorizaRetroactivo() : "ADMIN";

        if (!rolAutoriza.equalsIgnoreCase(securityUtils.getRol())) {
            throw new GlobalException(HttpStatus.FORBIDDEN,
                    "Solo " + rolAutoriza + " puede " + accion);
        }
    }

    private static String mensajeBloqueo(String documento, long diasAtras) {
        return "Este " + documento + " es de hace " + diasAtras + " días y no puede salir de la "
                + "caja de hoy: le dejaría el descuadre a un cajero que no hizo ese gasto. "
                + "Regístralo contra la caja menor, una cuenta bancaria, o déjalo como cuenta "
                + "por pagar si todavía no se ha pagado";
    }
}
