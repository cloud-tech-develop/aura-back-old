package com.cloud_technological.aura_pos.controllers;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.municipios.MunicipioDto;
import com.cloud_technological.aura_pos.repositories.municipios.MunicipioQueryRepository;
import com.cloud_technological.aura_pos.utils.ApiResponse;

import lombok.Getter;
import lombok.Setter;

@RestController
@RequestMapping("/api/municipios")
public class MunicipioController {

    @Autowired
    private MunicipioQueryRepository municipioQueryRepository;

    @PostMapping
    public ResponseEntity<ApiResponse<List<MunicipioDto>>> buscar(@RequestBody BuscarMunicipioRequest request) {
        List<MunicipioDto> result = municipioQueryRepository.buscar(request.getSearch());
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "", false, result),
                HttpStatus.OK);
    }

    @Getter
    @Setter
    public static class BuscarMunicipioRequest {
        private String search;
    }
}
