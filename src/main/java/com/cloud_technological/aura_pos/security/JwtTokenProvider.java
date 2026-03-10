package com.cloud_technological.aura_pos.security;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.utils.GlobalException;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtTokenProvider {
    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration-milliseconds}")
    private long jwtExpirationDate;

    // Generar Token con Claims personalizados (Empresa y Sucursal)
    public String generateToken(Authentication authentication, Integer empresaId, Long sucursalId, String rol, Long usuarioId) {
        String username = authentication.getName();
        Date currentDate = new Date();
        Date expireDate = new Date(currentDate.getTime() + jwtExpirationDate);

        Map<String, Object> claims = new HashMap<>();
        claims.put("empresaId", empresaId);
        claims.put("sucursalId", sucursalId); // ID de la sede donde está trabajando
        claims.put("rol", rol);
        claims.put("usuarioId", usuarioId);
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(username)
                .setIssuedAt(new Date())
                .setExpiration(expireDate)
                .signWith(key(), SignatureAlgorithm.HS256)
                .compact();
    }

    private Key key() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
    }

    // Obtener username del token
    public String getUsername(String token) {
        return Jwts.parserBuilder().setSigningKey(key()).build()
                .parseClaimsJws(token).getBody().getSubject();
    }

    // Obtener empresaId del token
    public Integer getEmpresaId(String token) {
        return getClaimAsInteger(token, "empresaId");
    }

    // Obtener sucursalId del token
    public Long getSucursalId(String token) {
        return getClaimAsLong(token, "sucursalId");
    }

    // Obtener rol del token
    public String getRol(String token) {
        return getClaimAsString(token, "rol");
    }

    private Integer getClaimAsInteger(String token, String claimName) {
        return getClaims(token).get(claimName, Integer.class);
    }

    private Long getClaimAsLong(String token, String claimName) {
        return getClaims(token).get(claimName, Long.class);
    }

    private String getClaimAsString(String token, String claimName) {
        return getClaims(token).get(claimName, String.class);
    }

    private io.jsonwebtoken.Claims getClaims(String token) {
        return Jwts.parserBuilder().setSigningKey(key()).build()
                .parseClaimsJws(token).getBody();
    }

    // Validar Token
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                .setSigningKey(key())
                .build()
                .parseClaimsJws(token); // 👈 CAMBIO AQUÍ

            return true;

        } catch (MalformedJwtException e) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Token JWT inválido");
        } catch (ExpiredJwtException e) {
            throw new GlobalException(HttpStatus.UNAUTHORIZED, "Token JWT expirado");
        } catch (UnsupportedJwtException e) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Token JWT no soportado");
        } catch (IllegalArgumentException e) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La cadena claims JWT está vacía");
        }
    }
}
