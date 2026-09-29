package com.cloud_technological.aura_pos;

import java.util.TimeZone;

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

	/**
	 * El negocio ocurre en Colombia y toda la base guarda hora de pared:
	 * de 274 columnas de fecha-hora, 273 son {@code timestamp without time
	 * zone}. El driver escribe ahí el {@code LocalDateTime} tal cual se lo
	 * entregan, así que lo que devuelva {@code LocalDateTime.now()} es
	 * literalmente lo que queda guardado.
	 *
	 * <p>En un servidor con la JVM en UTC, esas ~460 llamadas a
	 * {@code LocalDateTime.now()} / {@code LocalDate.now()} devolvían cinco
	 * horas de más. Lo peor no era la hora sino el día: entre las 7 p.m. y
	 * medianoche UTC ya es la fecha siguiente, así que arqueos, cierres y
	 * cortes diarios caían en el día equivocado.
	 *
	 * <p>Además el servidor de Postgres corre en {@code America/Bogota}, y hay
	 * consultas que resuelven la fecha con {@code CURRENT_DATE}. Con la JVM en
	 * UTC, Java y SQL no coincidían en qué día era.
	 */
	private static final String ZONA_NEGOCIO = "America/Bogota";

	public static void main(String[] args) {
		// Se fija antes de arrancar Spring a propósito: en cuanto se levantan
		// el pool de conexiones y los beans ya hay código pidiendo la hora, y
		// ponerla después dejaría en UTC todo lo que corra durante el arranque.
		//
		// Va aquí y no solo en la variable TZ del despliegue para que la app no
		// dependa de que alguien recuerde configurarla en el servidor.
		TimeZone.setDefault(TimeZone.getTimeZone(ZONA_NEGOCIO));
		System.setProperty("user.timezone", ZONA_NEGOCIO);

		SpringApplication.run(AuraPosApplication.class, args);
	}

}
