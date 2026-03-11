package com.cloud_technological.aura_pos.controllers;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.cloud_technological.aura_pos.services.implementations.R2StorageService;

@RestController
@RequestMapping("/api/storage")
public class StorageController {

    private final R2StorageService storageService;

    public StorageController(R2StorageService storageService) {
        this.storageService = storageService;
    }

    /**
     * POST /api/storage/upload
     * Sube una imagen al bucket de Cloudflare R2 y devuelve la URL pública.
     * Param: file — MultipartFile (imagen)
     * Param: carpeta — subdirectorio dentro del bucket (ej: "productos", "logos"). Default: "general"
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "carpeta", defaultValue = "general") String carpeta) {

        String url = storageService.subirImagen(file, carpeta);
        return ResponseEntity.ok(Map.of("url", url));
    }
}
