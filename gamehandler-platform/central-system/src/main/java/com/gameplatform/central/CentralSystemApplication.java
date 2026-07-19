package com.gameplatform.central;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Classe di avvio del sistema centrale della piattaforma di gioco.
 * Configura l'applicazione come applicazione Spring Boot, disabilita la
 * configurazione automatica dell'autenticazione basata su {@code UserDetailsService}
 * e abilita la gestione delle attività pianificate tramite scheduling.
 *
 * @see org.springframework.boot.SpringApplication
 */
@SpringBootApplication(exclude = { UserDetailsServiceAutoConfiguration.class })
@EnableScheduling
public class CentralSystemApplication {
    /**
     * Punto di ingresso dell'applicazione.
     * Avvia il contesto dell'applicazione Spring Boot per il sistema centrale
     * utilizzando gli argomenti di avvio forniti.
     *
     * @param args argomenti di linea di comando passati all'avvio dell'applicazione;
     *             può essere {@code null} o vuoto, nel qual caso vengono applicate
     *             le configurazioni predefinite
     */
    public static void main(String[] args) {
        SpringApplication.run(CentralSystemApplication.class, args);
    }
}
