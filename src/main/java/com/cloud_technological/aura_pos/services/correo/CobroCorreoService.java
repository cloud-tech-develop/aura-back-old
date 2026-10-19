package com.cloud_technological.aura_pos.services.correo;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.dto.correo.CorreoDtos;
import com.cloud_technological.aura_pos.dto.terceros.EstadoCuentaClienteDto;
import com.cloud_technological.aura_pos.repositories.correo.CorreoQueryRepository;
import com.cloud_technological.aura_pos.repositories.correo.CorreoQueryRepository.ClienteCobro;
import com.cloud_technological.aura_pos.services.ITerceroService;
import com.cloud_technological.aura_pos.services.implementations.EstadoCuentaPdfService;
import com.cloud_technological.aura_pos.utils.GlobalException;

import lombok.RequiredArgsConstructor;

/**
 * Cobro de cartera por correo (docs/PLAN_CUENTA_COBRO_CORREO.md §6): al cliente le
 * llega un recordatorio con su estado de cuenta en PDF, que arma el servidor (el mismo
 * de la ficha del cliente). Uno a uno desde la ficha, o a todos los clientes con
 * facturas vencidas desde la agenda (va a la cola).
 */
@Service
@RequiredArgsConstructor
public class CobroCorreoService {

    private static final Locale CO = Locale.forLanguageTag("es-CO");

    private final CorreoService correos;
    private final CorreoQueryRepository query;
    private final ITerceroService terceros;
    private final EstadoCuentaPdfService estadoCuentaPdf;

    /** Correo del cliente, asunto y mensaje con su saldo y lo vencido. */
    public CorreoDtos.Sugerencia sugerencia(Integer empresaId, Long terceroId) {
        ClienteCobro c = cliente(empresaId, terceroId);
        String empresa = correos.nombreEmpresa(empresaId);
        boolean disponible = true;
        try {
            correos.exigirConfigurado();
        } catch (GlobalException e) {
            disponible = false;
        }
        return new CorreoDtos.Sugerencia(c.correo() != null ? List.of(c.correo()) : List.of(),
                asunto(empresa, c), mensaje(empresa, c), disponible);
    }

    /** Envía ya el recordatorio con el estado de cuenta adjunto. */
    public CorreoDtos.Fila enviar(Integer empresaId, Long terceroId, CorreoDtos.Enviar dto, Integer usuarioId) {
        ClienteCobro c = cliente(empresaId, terceroId);
        return correos.enviarConPdf(empresaId, CorreoService.COBRO, terceroId, dto, archivo(c),
                pdf(empresaId, terceroId), usuarioId);
    }

    public List<CorreoDtos.Fila> historial(Integer empresaId, Long terceroId) {
        cliente(empresaId, terceroId);
        return correos.historial(empresaId, CorreoService.COBRO, terceroId);
    }

    /** Asunto y mensaje del recordatorio masivo (el saldo de cada cliente va en su PDF). */
    public CorreoDtos.Sugerencia sugerenciaMasiva(Integer empresaId) {
        String empresa = correos.nombreEmpresa(empresaId);
        boolean disponible = true;
        try {
            correos.exigirConfigurado();
        } catch (GlobalException e) {
            disponible = false;
        }
        String mensaje = "Hola,\n\nLe recordamos que tiene facturas vencidas con " + empresa
                + ". Adjuntamos su estado de cuenta con el detalle.\n\nSi ya realizó el pago, por favor ignore este "
                + "mensaje.\n\nCordialmente,\n" + empresa;
        return new CorreoDtos.Sugerencia(List.of(), "Recordatorio de pago - " + empresa, mensaje, disponible);
    }

    /**
     * Encola el recordatorio a cada cliente con facturas vencidas que tenga correo; los
     * que no tienen correo vuelven en {@code sinCorreo}. Cuántos clientes hay sale de
     * la misma consulta que la agenda (fuera de acuerdos de pago vivos).
     */
    public CorreoDtos.LoteEncolado encolarVencidos(Integer empresaId, CorreoDtos.CobroMasivo dto, Integer usuarioId) {
        correos.exigirConfigurado();
        String loteId = UUID.randomUUID().toString();
        List<String> sinCorreo = new ArrayList<>();
        int encolados = 0;
        for (ClienteCobro c : query.clientesVencidos(empresaId)) {
            if (c.correo() == null || c.correo().isBlank()) {
                sinCorreo.add(c.nombre());
                continue;
            }
            correos.encolarConPdf(empresaId, loteId, CorreoService.COBRO, c.id(), c.correo(), dto.getAsunto().trim(),
                    dto.getMensaje(), archivo(c), pdf(empresaId, c.id()), usuarioId);
            encolados++;
        }
        if (encolados == 0 && sinCorreo.isEmpty())
            throw new GlobalException(HttpStatus.BAD_REQUEST, "No hay clientes con facturas vencidas");
        return new CorreoDtos.LoteEncolado(loteId, encolados, sinCorreo);
    }

    // ── Apoyo ───────────────────────────────────────────────────────────────

    private byte[] pdf(Integer empresaId, Long terceroId) {
        EstadoCuentaClienteDto estado = terceros.obtenerEstadoCuenta(terceroId, empresaId, null, null);
        return estadoCuentaPdf.generar(estado, empresaId, null, null);
    }

    private static String archivo(ClienteCobro c) {
        String n = c.nombre() == null ? "cliente" : c.nombre().replaceAll("[^\\p{L}0-9 ]", "").trim().replace(' ', '_');
        return "Estado_de_cuenta_" + (n.isEmpty() ? "cliente" : n) + ".pdf";
    }

    private static String asunto(String empresa, ClienteCobro c) {
        return (c.vencido() != null && c.vencido().signum() > 0 ? "Recordatorio de pago" : "Estado de cuenta")
                + " - " + empresa;
    }

    private static String mensaje(String empresa, ClienteCobro c) {
        StringBuilder m = new StringBuilder("Hola ").append(c.nombre() != null ? c.nombre().trim() : "").append(",\n\n");
        if (c.vencido() != null && c.vencido().signum() > 0) {
            m.append("Le recordamos que tiene un saldo vencido de ").append(pesos(c.vencido()))
                    .append(" (saldo total pendiente ").append(pesos(c.saldo())).append(").");
        } else if (c.saldo() != null && c.saldo().signum() > 0) {
            m.append("Le compartimos su estado de cuenta: tiene un saldo pendiente de ").append(pesos(c.saldo()))
                    .append(".");
        } else {
            m.append("Le compartimos su estado de cuenta.");
        }
        m.append(" Adjuntamos el detalle.\n\nSi ya realizó el pago, por favor ignore este mensaje.\n\nCordialmente,\n")
                .append(empresa);
        return m.toString();
    }

    private static String pesos(BigDecimal v) {
        NumberFormat f = NumberFormat.getCurrencyInstance(CO);
        f.setMaximumFractionDigits(0);
        return f.format(v == null ? BigDecimal.ZERO : v);
    }

    private ClienteCobro cliente(Integer empresaId, Long terceroId) {
        ClienteCobro c = query.clienteCobro(terceroId, empresaId);
        if (c == null) throw new GlobalException(HttpStatus.NOT_FOUND, "Cliente no encontrado");
        return c;
    }
}
