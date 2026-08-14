package com.cloud_technological.aura_pos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * {@code considerNestedRepositories = true}: varios repos de nómina se declaran
 * como interfaces anidadas dentro de una clase contenedora (Afiliacion, Pila,
 * Retefuente, NominaElectronica). Por defecto Spring Data no las escanea, así
 * que sin esto no se crean sus beans y el arranque falla. El paquete base queda
 * en la raíz (el de esta clase), que cubre todos los repositorios.
 */
@SpringBootApplication
@EnableJpaRepositories(considerNestedRepositories = true)
public class AuraPosApplication {

	public static void main(String[] args) {
		SpringApplication.run(AuraPosApplication.class, args);
	}

}
