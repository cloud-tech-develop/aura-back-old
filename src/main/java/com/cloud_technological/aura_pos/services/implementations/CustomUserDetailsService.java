package com.cloud_technological.aura_pos.services.implementations;

import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.entity.UsuarioEntity;
import com.cloud_technological.aura_pos.repositories.users.UsuarioJPARepository;

import lombok.RequiredArgsConstructor;
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService{
    @Autowired
    private UsuarioJPARepository usuarioRepository;


    @Override
    @Transactional(readOnly = true) // Importante para rendimiento
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        
        // 1. Buscamos el usuario con nuestra nueva entidad
        UsuarioEntity usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + username));
        // 2. Validamos si está activo (Opcional, pero recomendado)
        if (Boolean.FALSE.equals(usuario.getActivo())) {
            throw new UsernameNotFoundException("El usuario está inactivo");
        }

        // 3. Mapeamos el rol de la base de datos a un GrantedAuthority de Spring
        // Asumimos que en tu tabla 'usuario' el campo 'rol' tiene algo como "ADMIN", "CAJERO"
        List<GrantedAuthority> authorities = Collections.singletonList(
                new SimpleGrantedAuthority(usuario.getRol()) 
        );
        // 4. Retornamos el objeto User de Spring Security con la data real
        return new User(
                usuario.getUsername(),
                usuario.getPassword(), // El hash que viene de la DB
                authorities
        );
    }
}
