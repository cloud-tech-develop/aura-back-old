package com.cloud_technological.aura_pos.services.nube;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.utils.GlobalException;

/**
 * Tope de archivos subidos por usuario por minuto (docs/PLAN_NUBE_DOCUMENTOS.md §4).
 * Frena un script o una cuenta robada que intente llenar el bucket. En memoria:
 * el backend corre en una sola instancia.
 */
@Component
public class LimiteSubidasNube {

    static final int MAX_POR_MINUTO = 60;
    private static final long VENTANA_MS = 60_000;

    private final Map<Integer, Deque<Long>> subidas = new ConcurrentHashMap<>();

    /**
     * Reserva {@code cantidad} subidas para el usuario o lanza 429.
     *
     * @param usuarioId usuario del token
     * @param cantidad  archivos de esta petición
     */
    public void reservar(Integer usuarioId, int cantidad) {
        long ahora = System.currentTimeMillis();
        Deque<Long> cola = subidas.computeIfAbsent(usuarioId, k -> new ArrayDeque<>());
        synchronized (cola) {
            while (!cola.isEmpty() && ahora - cola.peekFirst() > VENTANA_MS) cola.pollFirst();
            if (cola.size() + cantidad > MAX_POR_MINUTO)
                throw new GlobalException(HttpStatus.TOO_MANY_REQUESTS,
                        "Subiste demasiados archivos en poco tiempo. Espera un minuto e intenta de nuevo.");
            for (int i = 0; i < cantidad; i++) cola.addLast(ahora);
        }
    }
}
