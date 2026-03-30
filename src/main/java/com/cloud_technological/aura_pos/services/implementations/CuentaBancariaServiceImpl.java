package com.cloud_technological.aura_pos.services.implementations;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.cloud_technological.aura_pos.dto.tesoreria.CreateCuentaBancariaDto;
import com.cloud_technological.aura_pos.dto.tesoreria.CuentaBancariaDto;
import com.cloud_technological.aura_pos.entity.CuentaBancariaEntity;
import com.cloud_technological.aura_pos.repositories.tesoreria.CuentaBancariaJPARepository;
import com.cloud_technological.aura_pos.services.CuentaBancariaService;

@Service
public class CuentaBancariaServiceImpl implements CuentaBancariaService {

    @Autowired
    private CuentaBancariaJPARepository repo;

    @Override
    public List<CuentaBancariaDto> listar(Integer empresaId) {
        return repo.findByEmpresaIdOrderByNombreAsc(empresaId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public CuentaBancariaDto crear(Integer empresaId, CreateCuentaBancariaDto dto) {
        CuentaBancariaEntity entity = CuentaBancariaEntity.builder()
                .empresaId(empresaId)
                .nombre(dto.getNombre().trim())
                .tipo(dto.getTipo())
                .banco(dto.getBanco())
                .numeroCuenta(dto.getNumeroCuenta())
                .titular(dto.getTitular())
                .saldoInicial(dto.getSaldoInicial())
                .saldoActual(dto.getSaldoInicial())
                .build();
        return toDto(repo.save(entity));
    }

    @Override
    public CuentaBancariaDto actualizar(Long id, Integer empresaId, CreateCuentaBancariaDto dto) {
        CuentaBancariaEntity entity = repo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cuenta no encontrada"));

        entity.setNombre(dto.getNombre().trim());
        entity.setTipo(dto.getTipo());
        entity.setBanco(dto.getBanco());
        entity.setNumeroCuenta(dto.getNumeroCuenta());
        entity.setTitular(dto.getTitular());

        // Solo actualiza saldo_inicial si cambió y no hay movimientos aún
        if (entity.getSaldoActual().compareTo(entity.getSaldoInicial()) == 0) {
            entity.setSaldoInicial(dto.getSaldoInicial());
            entity.setSaldoActual(dto.getSaldoInicial());
        } else {
            entity.setSaldoInicial(dto.getSaldoInicial());
        }

        return toDto(repo.save(entity));
    }

    @Override
    public void toggleActiva(Long id, Integer empresaId) {
        CuentaBancariaEntity entity = repo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cuenta no encontrada"));
        entity.setActiva(!entity.getActiva());
        repo.save(entity);
    }

    private CuentaBancariaDto toDto(CuentaBancariaEntity e) {
        return CuentaBancariaDto.builder()
                .id(e.getId())
                .nombre(e.getNombre())
                .tipo(e.getTipo())
                .banco(e.getBanco())
                .numeroCuenta(e.getNumeroCuenta())
                .titular(e.getTitular())
                .saldoInicial(e.getSaldoInicial())
                .saldoActual(e.getSaldoActual())
                .activa(e.getActiva())
                .build();
    }
}
