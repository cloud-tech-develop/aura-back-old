package com.cloud_technological.aura_pos.services.permisos;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.repositories.permisos.PermisoBloqueoLogRepository;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PoliticaRoles;

/**
 * Acciones especiales que se revisan dentro de un servicio (no por la ruta), con el
 * mismo modo que el interceptor: APAGADO no revisa, OBSERVAR anota sin bloquear y
 * BLOQUEAR responde 403. Ej.: vender a crédito se decide por la forma de pago.
 */
@Service
public class ControlPermisoService {

    private final PermisoUsuarioService permisos;
    private final PermisoBloqueoLogRepository registro;
    private final String modo;

    public ControlPermisoService(PermisoUsuarioService permisos, PermisoBloqueoLogRepository registro,
            @Value("${app.permisos.modo:OBSERVAR}") String modo) {
        this.permisos = permisos;
        this.registro = registro;
        this.modo = modo == null ? "OBSERVAR" : modo.trim().toUpperCase();
    }

    /**
     * Exige una de las acciones especiales ({@code modulo.submodulo:CODIGO}).
     *
     * @param que en palabras, para el mensaje ("vender a crédito")
     */
    public void exigirEspecial(Integer usuarioId, Integer empresaId, String rol, List<String> claves, String que) {
        if ("APAGADO".equals(modo) || usuarioId == null) return;
        if (PoliticaRoles.PLATFORM_ADMIN.equalsIgnoreCase(rol)) return;
        for (String c : claves) {
            if (permisos.puedeEspecial(usuarioId, c)) return;
        }
        registro.registrar(empresaId, usuarioId, modo, "SERVICIO", que, String.join(",", claves), "ESPECIAL");
        if ("BLOQUEAR".equals(modo)) {
            throw new GlobalException(HttpStatus.FORBIDDEN,
                    "Su perfil no le permite " + que + ". Pídale al administrador que lo agregue a su perfil");
        }
    }
}
