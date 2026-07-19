package com.gameplatform.local;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Punto di avvio dell'applicazione server locale per la piattaforma di gioco.
 * <p>
 * Configura automaticamente il contesto Spring Boot escludendo la configurazione
 * automatica di {@link UserDetailsServiceAutoConfiguration} e abilita la
 * pianificazione di attività periodiche tramite {@link EnableScheduling}.
 * </p>
 *
 * @see org.springframework.boot.SpringApplication
 * @see org.springframework.scheduling.annotation.EnableScheduling
 */
@SpringBootApplication(exclude = { UserDetailsServiceAutoConfiguration.class })
@EnableScheduling
public class LocalServerApplication {
    /**
     * Avvia l'applicazione Spring Boot inizializzando il contesto e avviando
     * il server embedded.
     *
     * @param args argomenti passati da riga di comando; può essere un array vuoto
     *             ma non {@code null}
     */
    public static void main(String[] args) {
        SpringApplication.run(LocalServerApplication.class, args);
    }
}
