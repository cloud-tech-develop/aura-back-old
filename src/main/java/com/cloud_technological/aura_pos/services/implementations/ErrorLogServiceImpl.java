package com.cloud_technological.aura_pos.services.implementations;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.error_log.ErrorLogDetalleDto;
import com.cloud_technological.aura_pos.dto.error_log.ErrorLogGrupoDto;
import com.cloud_technological.aura_pos.dto.error_log.ErrorLogPageParamsDto;
import com.cloud_technological.aura_pos.dto.error_log.ErrorLogTableDto;
import com.cloud_technological.aura_pos.entity.ErrorLogEntity;
import com.cloud_technological.aura_pos.repositories.error_log.ErrorLogJPARepository;
import com.cloud_technological.aura_pos.repositories.error_log.ErrorLogQueryRepository;
import com.cloud_technological.aura_pos.repositories.users.UsuarioJPARepository;
import com.cloud_technological.aura_pos.services.ErrorLogService;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;

@Service
public class ErrorLogServiceImpl implements ErrorLogService {

    private static final Logger log = LoggerFactory.getLogger(ErrorLogServiceImpl.class);

    @Autowired
    private ErrorLogJPARepository jpaRepository;

    @Autowired
    private ErrorLogQueryRepository queryRepository;

    @Autowired
    private UsuarioJPARepository usuarioRepository;

    // ── Registro asíncrono ────────────────────────────────────
    @Override
    @Async("errorLogExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarAsync(String metodo, String endpoint, int statusCode,
                               String mensaje, String detalle,
                               String username, String ipOrigen) {
        try {
            String normalizedPath = normalizarPath(endpoint);
            String categoria     = resolverCategoria(statusCode);
            String grupoHash     = computarHash(metodo, normalizedPath, statusCode);

            Integer empresaId    = null;
            String  usuarioNombre = username;

            if (username != null && !username.isBlank()) {
                var usuarioOpt = usuarioRepository.findByUsername(username);
                if (usuarioOpt.isPresent()) {
                    var usuario = usuarioOpt.get();
                    if (usuario.getEmpresa() != null) {
                        empresaId = usuario.getEmpresa().getId();
                    }
                    if (usuario.getTercero() != null) {
                        usuarioNombre = usuario.getTercero().getNombres()
                                + " " + usuario.getTercero().getApellidos();
                    }
                }
            }

            ErrorLogEntity entity = new ErrorLogEntity();
            entity.setMetodo(metodo != null ? metodo.toUpperCase() : "UNKNOWN");
            entity.setEndpoint(normalizedPath);
            entity.setStatusCode(statusCode);
            entity.setCategoria(categoria);
            entity.setMensaje(truncar(mensaje, 2000));
            entity.setDetalle(truncar(detalle, 5000));
            entity.setGrupoHash(grupoHash);
            entity.setEmpresaId(empresaId);
            entity.setUsuarioNombre(usuarioNombre);
            entity.setIpOrigen(ipOrigen);
            entity.setCreatedAt(LocalDateTime.now());

            jpaRepository.saveAndFlush(entity);

        } catch (Exception e) {
            // Silencioso — el log nunca debe interrumpir el flujo principal
            log.error("Error al registrar error_log: {}", e.getMessage());
        }
    }

    // ── Listar paginado ───────────────────────────────────────
    @Override
    public PageImpl<ErrorLogTableDto> listar(PageableDto<ErrorLogPageParamsDto> pageable) {
        int page = pageable.getPage().intValue();
        int size = pageable.getRows().intValue();
        return queryRepository.listar(page, size, pageable.getParams());
    }

    // ── Listar agrupado ───────────────────────────────────────
    @Override
    public PageImpl<ErrorLogGrupoDto> listarGrupos(PageableDto<ErrorLogPageParamsDto> pageable) {
        int page = pageable.getPage().intValue();
        int size = pageable.getRows().intValue();
        return queryRepository.listarGrupos(page, size, pageable.getParams());
    }

    // ── Detalle ───────────────────────────────────────────────
    @Override
    public ErrorLogDetalleDto obtenerPorId(Long id) {
        return queryRepository.findById(id)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Registro no encontrado"));
    }

    // ── Helpers ───────────────────────────────────────────────
    private String resolverCategoria(int status) {
        if (status >= 500) return "danger";
        if (status >= 400) return "warn";
        return "info";
    }

    /**
     * Reemplaza segmentos numéricos o UUIDs del path por {id}
     * Ej: /api/ventas/123/detalle → /api/ventas/{id}/detalle
     */
    private String normalizarPath(String path) {
        if (path == null) return "unknown";
        return path.replaceAll("/\\d+", "/{id}")
                   .replaceAll("/[0-9a-fA-F\\-]{36}", "/{uuid}");
    }

    private String computarHash(String metodo, String path, int status) {
        try {
            String raw = metodo.toUpperCase() + "|" + path + "|" + status;
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            return String.valueOf(Math.abs((metodo + path + status).hashCode()));
        }
    }

    private String truncar(String texto, int max) {
        if (texto == null) return null;
        return texto.length() > max ? texto.substring(0, max) : texto;
    }
}
