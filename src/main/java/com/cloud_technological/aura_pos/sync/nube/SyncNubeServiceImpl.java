package com.cloud_technological.aura_pos.sync.nube;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.repositories.sucursales.SucursalJPARepository;
import com.cloud_technological.aura_pos.sync.SyncProperties;
import com.cloud_technological.aura_pos.sync.SyncTablas;
import com.cloud_technological.aura_pos.sync.TiposComando;
import com.cloud_technological.aura_pos.sync.dto.SyncDtos;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class SyncNubeServiceImpl implements SyncNubeService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final SyncProperties props;
    private final SyncNubeQueryRepository repo;
    private final ProcesadorComandos procesador;
    private final SucursalJPARepository sucursales;
    private final ObjectMapper objectMapper;

    /** {@inheritDoc} */
    @Override
    public SyncDtos.NodoInfo autenticar(String token) {
        if (!props.esNube())
            throw new GlobalException(HttpStatus.NOT_FOUND, "Esta instancia no es la nube");
        if (token == null || token.isBlank())
            throw new GlobalException(HttpStatus.UNAUTHORIZED, "Falta el token del nodo");
        SyncDtos.NodoInfo nodo = repo.nodoPorToken(hash(token.trim()))
                .orElseThrow(() -> new GlobalException(HttpStatus.UNAUTHORIZED, "Token de nodo inválido o nodo inactivo"));
        repo.tocar(nodo.getId());
        return nodo;
    }

    /** {@inheritDoc} */
    @Override
    public SyncDtos.PushRespuesta push(SyncDtos.NodoInfo nodo, SyncDtos.PushPeticion peticion) {
        return procesador.procesar(nodo, peticion);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Todo se lee en una misma foto de la base (REPEATABLE READ): el
     * inventario y los lotes salen del mismo instante.
     */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public SyncDtos.PullRespuesta pull(SyncDtos.NodoInfo nodo, boolean saldos, boolean soloTurnos) {
        SyncDtos.PullRespuesta r = new SyncDtos.PullRespuesta();
        r.setNodo(nodo);
        r.setGeneradoEn(LocalDateTime.now());
        r.setIncluyeSaldos(saldos && !soloTurnos);
        r.setIncluyeTurnos(saldos || soloTurnos);
        for (SyncTablas.Tabla t : SyncTablas.TODAS) {
            boolean va = switch (t.grupo()) {
                case CATALOGO -> !soloTurnos;
                case SALDO -> r.isIncluyeSaldos();
                case TURNOS -> r.isIncluyeTurnos();
            };
            if (!va) continue;
            String json = repo.extraer(t, nodo.getEmpresaId());
            if (json == null) continue;
            JsonNode filas;
            try {
                filas = objectMapper.readTree(json);
            } catch (Exception e) {
                throw new GlobalException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo leer la tabla " + t.nombre());
            }
            try {
                GuardaEmpresa.verificar(t.nombre(), filas, nodo.getEmpresaId());
            } catch (GuardaEmpresa.DatosDeOtraEmpresa e) {
                log.error("[SYNC] Nodo {} (empresa {}): {}", nodo.getCodigo(), nodo.getEmpresaId(), e.getMessage());
                throw new GlobalException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
            }
            r.getTablas().put(t.nombre(), filas);
        }
        if (soloTurnos) return r;
        r.setMapas(repo.mapas(nodo.getId(), TiposComando.T_TERCERO));
        r.setPisosConsecutivo(repo.pisosConsecutivo(nodo.getEmpresaId(), nodo.getPrefijoVentas()));
        return r;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public SyncDtos.NodoCreado crearNodo(Integer empresaId, SyncDtos.NodoNuevo n) {
        if (n.getCodigo() == null || n.getCodigo().isBlank())
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Indique el código del nodo");
        if (n.getNombre() == null || n.getNombre().isBlank())
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Indique el nombre del nodo");
        if (n.getSucursalId() != null)
            sucursales.findByIdAndEmpresaId(n.getSucursalId(), empresaId)
                    .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Sucursal no encontrada"));
        // Sin serie propia, el nodo y la nube sacarían consecutivos de la misma
        // serie sin verse: dos ventas con el mismo número.
        if (n.getPrefijoVentas() == null || n.getPrefijoVentas().isBlank())
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Asigne un prefijo de ventas propio al nodo (ej. T1): sus consecutivos no pueden compartir serie con la nube");
        if (repo.prefijoEnUso(empresaId, n.getPrefijoVentas().trim()))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "El prefijo " + n.getPrefijoVentas().trim().toUpperCase() + " ya lo usa una sucursal u otro nodo");

        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Long id = repo.crearNodo(empresaId, n, hash(token));
        return new SyncDtos.NodoCreado(repo.nodo(id, empresaId).orElseThrow(), token);
    }

    /** {@inheritDoc} */
    @Override
    public List<SyncDtos.NodoInfo> nodos(Integer empresaId) {
        return repo.nodos(empresaId);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void cambiarActivo(Long id, Integer empresaId, boolean activo) {
        if (!repo.cambiarActivo(id, empresaId, activo))
            throw new GlobalException(HttpStatus.NOT_FOUND, "Nodo no encontrado");
    }

    /** SHA-256 en hexadecimal: el token nunca se guarda en claro. */
    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
