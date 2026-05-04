## Descrizione

Il sistema gestisce una piattaforma di giochi da tavolo o da bar. Il sistema è composto come segue:
- un sistema centrale che gestisce tutto (utenti, giochi, prenotazioni, statistiche, stati dei giochi)
- ogni sottosistema è composto da un server in locale che gestisce il database per quell'edificio e diversi client che sono i diversi giochi (che inizialemnte saranno solo emulati e poi realizzati in modo più concreto)
- gli endpoint dei client sono giochi che si connettono al server locale e dispongono di un'interfaccia grafica.


## Requisiti funzionali
- il sistema deve consentire agli utenti di registrarsi e accedere al proprio account
- i sottosistemi devono essere in grado di comunicare con il sistema centrale e essere eseguiti anche in assenza di connessione al sistema centrale, sincronizzando i dati quando la connessione viene ristabilita
- gli utenti registrati per un gioco, saranno in grado di accedere a qualsiasi gioco di qualsiasi sottosistema, anche se non è presente una connessione al sistema centrale. (il cloning dei dati degli utenti deve essere distribuito su tutti i sottosistemi)
- il sistema deve consentire agli utenti di prenotare i giochi disponibili nei sottosistemi, visualizzare le prenotazioni e cancellarle se necessario
- il sistema deve tenere traccia dello stato dei giochi (disponibile, prenotato, in uso) e aggiornare le informazioni in tempo reale
- il sistema deve fornire statistiche sull'utilizzo dei giochi, come il numero di prenotazioni, il tempo di utilizzo e le preferenze degli utenti
- il sistema deve essere scalabile per supportare un numero crescente di utenti e giochi
- il sistema deve essere sicuro per proteggere i dati degli utenti e prevenire accessi non autorizzati

## Tecnologie

- Java per lo sviluppo del sistema centrale e dei sottosistemi
- API REST per la comunicazione tra il sistema centrale e i sottosistemi
- Database relazionale (ad esempio MySQL ) per la gestione dei dati
- MQTT per la comunicazione tra end-point e server locale
- Framework di sviluppo web (ad esempio Spring Boot) per la creazione dell'interfaccia
- Docker per la containerizzazione dei componenti del sistema
- TLS per crittografare le comunicazioni

In futuro potrebbero servire anche:
- componenti fisici per i giochi (ad esempio sensori, display, ecc.) che si interfacciano con i client software
- controller fisici per i giochi come sp32 o arduino che si interfacciano con i client software

## Architettura

Il sistema deve essere composto da microservizi.


## Scelte

- Usare il tls anche per mqtt
- Il local server ogni 5 minuti controlla se gli endpoint sono attivi, se non lo sono termina le partite in esecuzione, segnala il problema
- gli stati dei giochi vengono salvati in locale e utilizzati in locale per generare le statistiche (tra cui anche l'elenco delle partite completate e in corso), i dati delle statistiche vengono inviati al sistema centrale.
- Il local server, si sincronizza ogni 5 minuti i dati con il centrale, se non è connesso, alla ripristino della connessione vengono inviati i dati in backlog.
- poichè il sistema deve essere in grado di gestire diversi giochi, sarebbe utile implementare una serie di interfacce che dipendono da una interfaccia comune (generalissima), che definiscono i diversi stati e le diverse azioni che possono essere eseguite sui giochi (ad es. start, stop, pause, resume, ecc.).
