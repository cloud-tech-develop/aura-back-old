package com.cloud_technological.aura_pos.sync.nube;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.cloud_technological.aura_pos.entity.UsuarioEntity;
import com.cloud_technological.aura_pos.repositories.users.UsuarioJPARepository;
import com.cloud_technological.aura_pos.security.JwtTokenProvider;
import com.cloud_technological.aura_pos.sync.SyncContexto;
import com.cloud_technological.aura_pos.sync.dto.SyncDtos;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Reproduce en la nube los comandos que sube un nodo.
 *
 * <p>Cada comando va en su propia transacción y con el usuario que lo hizo en
 * la tienda autenticado, así los servicios corren igual que si la petición
 * hubiera llegado del front: los asientos (AFTER_COMMIT) se generan al confirmar
 * cada uno.
 *
 * <p>Se procesan en orden y se detiene en el primer error: lo que sigue puede
 * depender de él.
 */
@Slf4j
@Component
public class ProcesadorComandos {

    private final Map<String, ManejadorComando> manejadores;
    private final SyncNubeQueryRepository repo;
    private final UsuarioJPARepository usuarios;
    private final JwtTokenProvider jwt;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate tx;

    public ProcesadorComandos(List<ManejadorComando> manejadores, SyncNubeQueryRepository repo,
            UsuarioJPARepository usuarios, JwtTokenProvider jwt, ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager) {
        this.manejadores = manejadores.stream()
                .collect(Collectors.toMap(ManejadorComando::tipo, Function.identity()));
        this.repo = repo;
        this.usuarios = usuarios;
        this.jwt = jwt;
        this.objectMapper = objectMapper;
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Procesa un lote del nodo.
     *
     * @param nodo     nodo autenticado
     * @param peticion comandos en el orden en que ocurrieron
     * @return resultados hasta el primer error, incluido
     */
    public SyncDtos.PushRespuesta procesar(SyncDtos.NodoInfo nodo, SyncDtos.PushPeticion peticion) {
        SyncDtos.PushRespuesta respuesta = new SyncDtos.PushRespuesta();
        for (SyncDtos.Comando c : peticion.getComandos()) {
            SyncDtos.Resultado r = procesar(nodo, c);
            respuesta.getResultados().add(r);
            if (!r.isOk()) break;
        }
        return respuesta;
    }

    private SyncDtos.Resultado procesar(SyncDtos.NodoInfo nodo, SyncDtos.Comando c) {
        if (c.getUuid() == null || c.getUuid().isBlank())
            return fallo(c, "Comando sin uuid");

        // Ya llegó antes (el nodo reintentó porque no alcanzó a ver la respuesta).
        Optional<String> previo = repo.recibido(c.getUuid());
        if (previo.isPresent()) return leer(previo.get(), c);

        ManejadorComando m = manejadores.get(c.getTipo());
        if (m == null) return fallo(c, "Tipo de comando desconocido: " + c.getTipo());

        // La empresa la define el token del nodo, nunca el comando.
        c.setEmpresaId(nodo.getEmpresaId());
        ManejadorComando.Numeracion num = m.numeracion(c);
        SyncContexto.Replay replay = new SyncContexto.Replay(nodo.getId(), c.getFecha(), num.prefijo(), num.consecutivo());
        try {
            autenticar(c.getUsuarioId(), nodo);
            return SyncContexto.ejecutar(replay, () -> tx.execute(status -> {
                ManejadorComando.Salida s = m.reproducir(c, nodo);
                SyncDtos.Resultado r = new SyncDtos.Resultado(c.getUuid(), true, s.idNube(), null, s.datos());
                repo.guardarRecibido(nodo.getId(), c, escribir(r));
                return r;
            }));
        } catch (Exception e) {
            log.warn("[SYNC] Nodo {} · {} {} falló: {}", nodo.getCodigo(), c.getTipo(), c.getIdLocal(), e.getMessage());
            return fallo(c, mensaje(e));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * Autentica al usuario del comando con un token propio, para que
     * {@code SecurityUtils} y los permisos funcionen como en una petición normal.
     */
    private void autenticar(Long usuarioId, SyncDtos.NodoInfo nodo) {
        if (usuarioId == null) return;
        UsuarioEntity u = usuarios.findById(usuarioId.intValue())
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Usuario " + usuarioId + " no existe en la nube"));
        if (u.getEmpresa() == null || !nodo.getEmpresaId().equals(u.getEmpresa().getId()))
            throw new GlobalException(HttpStatus.FORBIDDEN, "El usuario " + usuarioId + " no es de la empresa del nodo");
        String token = jwt.generateToken(u.getUsername(), nodo.getEmpresaId(),
                nodo.getSucursalId() != null ? nodo.getSucursalId().longValue() : null,
                u.getRol(), usuarioId, u.getTokenVersion());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                u.getUsername(), token, List.of(new SimpleGrantedAuthority(String.valueOf(u.getRol())))));
    }

    private SyncDtos.Resultado fallo(SyncDtos.Comando c, String error) {
        return new SyncDtos.Resultado(c.getUuid(), false, null, error, null);
    }

    private String mensaje(Exception e) {
        Throwable t = e;
        while (t.getCause() != null && !(t instanceof GlobalException)) t = t.getCause();
        return t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
    }

    private String escribir(SyncDtos.Resultado r) {
        try {
            return objectMapper.writeValueAsString(r);
        } catch (Exception e) {
            return null;
        }
    }

    private SyncDtos.Resultado leer(String json, SyncDtos.Comando c) {
        try {
            if (json != null) return objectMapper.readValue(json, SyncDtos.Resultado.class);
        } catch (Exception e) {
            log.warn("[SYNC] Resultado guardado ilegible para {}: {}", c.getUuid(), e.getMessage());
        }
        return new SyncDtos.Resultado(c.getUuid(), true, null, null, null);
    }
}
