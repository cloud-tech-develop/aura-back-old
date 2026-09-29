package com.cloud_technological.aura_pos.services.implementations;

import java.util.List;

import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.dto.bodegas.BodegaDto;
import com.cloud_technological.aura_pos.dto.bodegas.BodegaTableDto;
import com.cloud_technological.aura_pos.dto.bodegas.CreateBodegaDto;
import com.cloud_technological.aura_pos.dto.bodegas.UpdateBodegaDto;
import com.cloud_technological.aura_pos.entity.BodegaEntity;
import com.cloud_technological.aura_pos.entity.SucursalEntity;
import com.cloud_technological.aura_pos.repositories.bodegas.BodegaJPARepository;
import com.cloud_technological.aura_pos.repositories.bodegas.BodegaQueryRepository;
import com.cloud_technological.aura_pos.repositories.sucursales.SucursalJPARepository;
import com.cloud_technological.aura_pos.repositories.users.UsuarioJPARepository;
import com.cloud_technological.aura_pos.services.BodegaService;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;

import jakarta.transaction.Transactional;

@Service
public class BodegaServiceImpl implements BodegaService {

    private final BodegaJPARepository bodegaRepository;
    private final BodegaQueryRepository bodegaQueryRepository;
    private final SucursalJPARepository sucursalRepository;
    private final UsuarioJPARepository usuarioRepository;

    public BodegaServiceImpl(BodegaJPARepository bodegaRepository,
            BodegaQueryRepository bodegaQueryRepository,
            SucursalJPARepository sucursalRepository,
            UsuarioJPARepository usuarioRepository) {
        this.bodegaRepository = bodegaRepository;
        this.bodegaQueryRepository = bodegaQueryRepository;
        this.sucursalRepository = sucursalRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public PageImpl<BodegaTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        return bodegaQueryRepository.listar(pageable, empresaId);
    }

    @Override
    public List<BodegaDto> list(Integer empresaId, Integer sucursalId, boolean soloVenta) {
        return bodegaQueryRepository.list(empresaId, sucursalId, soloVenta);
    }

    @Override
    public BodegaTableDto obtenerPorId(Long id, Integer empresaId) {
        BodegaTableDto dto = bodegaQueryRepository.obtener(id, empresaId);
        if (dto == null)
            throw new GlobalException(HttpStatus.NOT_FOUND, "Bodega no encontrada");
        return dto;
    }

    @Override
    @Transactional
    public BodegaTableDto crear(CreateBodegaDto dto, Integer empresaId) {
        SucursalEntity sucursal = sucursalRepository
                .findByIdAndEmpresaId(dto.getSucursalId(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Sucursal no encontrada"));

        String nombre = dto.getNombre().trim();
        if (bodegaRepository.existsBySucursalIdAndNombreIgnoreCase(dto.getSucursalId(), nombre))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Ya hay una bodega llamada " + nombre + " en esta sucursal");

        validarResponsable(dto.getResponsableUsuarioId(), empresaId);

        BodegaEntity entity = new BodegaEntity();
        entity.setEmpresaId(empresaId);
        entity.setSucursal(sucursal);
        entity.setCodigo(normalizar(dto.getCodigo()));
        entity.setNombre(nombre);
        entity.setResponsableUsuarioId(dto.getResponsableUsuarioId());
        entity.setPermiteVenta(dto.getPermiteVenta() == null || dto.getPermiteVenta());
        entity.setUbicacion(normalizar(dto.getUbicacion()));
        entity.setObservacion(normalizar(dto.getObservacion()));
        entity.setActiva(Boolean.TRUE);

        // La primera bodega de una sucursal es principal quiera o no: alguien
        // tiene que recibir el stock cuando el documento no dice bodega.
        boolean primera = bodegaRepository.findBySucursalIdOrderByNombreAsc(dto.getSucursalId()).isEmpty();
        boolean principal = primera || Boolean.TRUE.equals(dto.getEsPrincipal());
        entity.setEsPrincipal(principal);

        if (principal && !primera)
            quitarPrincipalDe(dto.getSucursalId(), null);

        return obtenerPorId(bodegaRepository.save(entity).getId(), empresaId);
    }

    @Override
    @Transactional
    public void crearPrincipal(SucursalEntity sucursal) {
        if (sucursal == null || sucursal.getId() == null) return;
        if (bodegaRepository.findBySucursalIdAndEsPrincipalTrue(sucursal.getId()).isPresent()) return;

        // Mismo nombre y código que les dio V172 a las sucursales existentes.
        BodegaEntity entity = new BodegaEntity();
        entity.setEmpresaId(sucursal.getEmpresa().getId());
        entity.setSucursal(sucursal);
        entity.setCodigo("BOD-" + sucursal.getId());
        entity.setNombre("Bodega Principal");
        entity.setEsPrincipal(Boolean.TRUE);
        entity.setPermiteVenta(Boolean.TRUE);
        entity.setActiva(Boolean.TRUE);
        bodegaRepository.save(entity);
    }

    @Override
    @Transactional
    public BodegaTableDto actualizar(Long id, UpdateBodegaDto dto, Integer empresaId) {
        BodegaEntity entity = bodegaRepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Bodega no encontrada"));

        Integer sucursalId = entity.getSucursal().getId();
        String nombre = dto.getNombre().trim();

        if (!entity.getNombre().equalsIgnoreCase(nombre)
                && bodegaRepository.existsBySucursalIdAndNombreIgnoreCase(sucursalId, nombre))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Ya hay una bodega llamada " + nombre + " en esta sucursal");

        validarResponsable(dto.getResponsableUsuarioId(), empresaId);

        entity.setCodigo(normalizar(dto.getCodigo()));
        entity.setNombre(nombre);
        entity.setResponsableUsuarioId(dto.getResponsableUsuarioId());
        entity.setUbicacion(normalizar(dto.getUbicacion()));
        entity.setObservacion(normalizar(dto.getObservacion()));
        if (dto.getPermiteVenta() != null) entity.setPermiteVenta(dto.getPermiteVenta());

        if (Boolean.TRUE.equals(dto.getEsPrincipal()) && !Boolean.TRUE.equals(entity.getEsPrincipal())) {
            quitarPrincipalDe(sucursalId, id);
            entity.setEsPrincipal(Boolean.TRUE);
        }

        if (dto.getActiva() != null) {
            if (!dto.getActiva() && Boolean.TRUE.equals(entity.getEsPrincipal()))
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "No se puede desactivar la bodega principal. Marque otra como principal primero.");
            entity.setActiva(dto.getActiva());
        }

        bodegaRepository.save(entity);
        return obtenerPorId(id, empresaId);
    }

    @Override
    @Transactional
    public void eliminar(Long id, Integer empresaId) {
        BodegaEntity entity = bodegaRepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Bodega no encontrada"));

        if (Boolean.TRUE.equals(entity.getEsPrincipal()))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "La bodega principal no se elimina. Marque otra como principal primero.");

        // Borrarla dejaría el kardex apuntando al vacío: el histórico manda.
        if (bodegaQueryRepository.tieneMovimiento(id))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "La bodega ya tiene saldo o movimientos. Desactívela en vez de eliminarla.");

        bodegaRepository.delete(entity);
    }

    @Override
    public BodegaEntity resolver(Long bodegaId, Integer sucursalId, Integer empresaId) {
        if (bodegaId != null) {
            BodegaEntity bodega = bodegaRepository.findByIdAndEmpresaId(bodegaId, empresaId)
                    .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Bodega no encontrada"));
            if (!Boolean.TRUE.equals(bodega.getActiva()))
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "La bodega " + bodega.getNombre() + " está inactiva");
            if (sucursalId != null && !bodega.getSucursal().getId().equals(sucursalId))
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "La bodega " + bodega.getNombre() + " no pertenece a la sucursal del documento");
            return bodega;
        }

        if (sucursalId == null)
            throw new GlobalException(HttpStatus.BAD_REQUEST, "No hay sucursal para resolver la bodega");

        return bodegaRepository.findBySucursalIdAndEsPrincipalTrue(sucursalId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST,
                        "La sucursal no tiene bodega principal. Cree una en Inventario, opción Bodegas."));
    }

    @Override
    public BodegaEntity resolverParaVenta(Long bodegaId, Integer sucursalId, Integer empresaId) {
        BodegaEntity bodega = resolver(bodegaId, sucursalId, empresaId);
        if (!Boolean.TRUE.equals(bodega.getPermiteVenta()))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "La bodega " + bodega.getNombre() + " no despacha ventas");
        return bodega;
    }

    /** Solo una principal por sucursal: el índice único parcial lo exige. */
    private void quitarPrincipalDe(Integer sucursalId, Long exceptoId) {
        bodegaRepository.findBySucursalIdAndEsPrincipalTrue(sucursalId)
                .filter(actual -> exceptoId == null || !actual.getId().equals(exceptoId))
                .ifPresent(actual -> {
                    actual.setEsPrincipal(Boolean.FALSE);
                    bodegaRepository.saveAndFlush(actual);
                });
    }

    private void validarResponsable(Integer usuarioId, Integer empresaId) {
        if (usuarioId == null) return;
        usuarioRepository.findById(usuarioId)
                .filter(u -> u.getEmpresa() != null && u.getEmpresa().getId().equals(empresaId))
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST,
                        "El responsable no es un usuario de la empresa"));
    }

    private String normalizar(String valor) {
        if (valor == null) return null;
        String limpio = valor.trim();
        return limpio.isEmpty() ? null : limpio;
    }
}
