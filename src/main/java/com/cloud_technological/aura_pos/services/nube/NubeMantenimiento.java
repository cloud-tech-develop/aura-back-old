package com.cloud_technological.aura_pos.services.nube;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.cloud_technological.aura_pos.entity.nube.NubeEventoEntity;
import com.cloud_technological.aura_pos.repositories.nube.NubeQueryRepository;
import com.cloud_technological.aura_pos.repositories.nube.NubeQueryRepository.CarpetaPurgada;
import com.cloud_technological.aura_pos.repositories.nube.NubeQueryRepository.ObjetoVencido;

import lombok.extern.slf4j.Slf4j;

/**
 * Tareas de la nube sin usuario (docs/PLAN_NUBE_DOCUMENTOS.md F6). Las corre
 * {@code NubeScheduler}; cada acción queda en la bitácora de la empresa como
 * hecha por el sistema (usuario 0).
 */
@Slf4j
@Service
public class NubeMantenimiento {

    /** Archivos por lote, para no tener una transacción gigante. */
    public static final int LOTE = 200;

    private static final int USUARIO_SISTEMA = 0;

    private final NubeQueryRepository query;
    private final NubeAlmacenamiento almacenamiento;
    private final NubeBitacora bitacora;

    public NubeMantenimiento(NubeQueryRepository query, NubeAlmacenamiento almacenamiento, NubeBitacora bitacora) {
        this.query = query;
        this.almacenamiento = almacenamiento;
        this.bitacora = bitacora;
    }

    /**
     * Borra para siempre un lote de lo que lleva más de 30 días en la papelera: las
     * filas en esta transacción y los objetos del bucket solo después de confirmar
     * (si el bucket falla, queda un objeto cifrado huérfano, nunca una fila sin objeto).
     *
     * @return archivos borrados en este lote (si es igual a {@link #LOTE}, hay más)
     */
    @Transactional
    public int purgarLote() {
        List<ObjetoVencido> vencidos = query.archivosVencidos(LOTE);
        List<ObjetoVencido> borrados = new ArrayList<>();
        for (ObjetoVencido o : vencidos) {
            if (query.borrarArchivoVencido(o.id(), o.empresaId()) == 1) {
                borrados.add(o);
                bitacora.registrar(sistema(o.empresaId()), NubeEventoEntity.PURGAR, o.id(), o.carpetaId(), false,
                        detalle("tipo", "archivo", "nombre", o.nombre(), "automatica", true));
            }
        }
        // De las hojas hacia arriba (6 niveles como máximo).
        for (int vuelta = 0; vuelta < 8; vuelta++) {
            List<CarpetaPurgada> carpetas = query.borrarCarpetasVencidas();
            if (carpetas.isEmpty()) break;
            for (CarpetaPurgada c : carpetas)
                bitacora.registrar(sistema(c.empresaId()), NubeEventoEntity.PURGAR, null, c.id(), false,
                        detalle("tipo", "carpeta", "nombre", c.nombre(), "automatica", true));
        }
        if (!borrados.isEmpty()) alConfirmar(() -> borrados.forEach(this::borrarObjeto));
        return borrados.size();
    }

    /**
     * Cuando la firma deja de atender a la empresa, sus carpetas privadas pasan a
     * compartidas: los documentos son del cliente y nadie más los vería.
     *
     * @return carpetas liberadas
     */
    @Transactional
    public int liberarPrivadasSinVinculo() {
        List<CarpetaPurgada> liberadas = query.liberarPrivadasSinVinculo();
        for (CarpetaPurgada c : liberadas)
            bitacora.registrar(sistema(c.empresaId()), "LIBERAR_PRIVADA", null, c.id(), false,
                    detalle("nombre", c.nombre(), "motivo", "La firma contable ya no atiende la empresa"));
        return liberadas.size();
    }

    private void borrarObjeto(ObjetoVencido o) {
        try {
            almacenamiento.eliminar(o.empresaId(), o.objectKey());
        } catch (RuntimeException e) {
            log.error("Nube: no se pudo borrar del bucket el objeto {}: {}", o.objectKey(), e.getMessage());
        }
    }

    private static void alConfirmar(Runnable accion) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                accion.run();
            }
        });
    }

    private static NubeContexto sistema(Integer empresaId) {
        return new NubeContexto(empresaId, USUARIO_SISTEMA, null, "sistema", "tarea programada");
    }

    private static Map<String, Object> detalle(Object... pares) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pares.length; i += 2) m.put(String.valueOf(pares[i]), pares[i + 1]);
        return m;
    }
}
