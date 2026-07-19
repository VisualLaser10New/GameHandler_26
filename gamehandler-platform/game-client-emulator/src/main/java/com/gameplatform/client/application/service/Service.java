package com.gameplatform.client.application.service;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Classe base astratta per i servizi che richiedono uno scheduler periodico.
 * Fornisce il ciclo di vita comune per l'avvio e l'arresto di un
 * {@link java.util.concurrent.ScheduledExecutorService} e del relativo task.
 */
public class Service {
	protected ScheduledFuture<?> scheduledTask;
	protected ScheduledExecutorService scheduler;

	/**
	 * Arresta il task schedulato e lo scheduler, attendendo fino a 3 secondi
	 * per il termine delle esecuzioni in corso. Ripristina il flag di interruzione
	 * del thread corrente in caso di timeout.
	 */
	protected void stopService() {
		if (scheduledTask != null) {
			scheduledTask.cancel(false);
			scheduledTask = null;
		}
		if (scheduler != null) {
			scheduler.shutdown();
			try {
				if (!scheduler.awaitTermination(3, TimeUnit.SECONDS)) {
					scheduler.shutdownNow();
				}
			} catch (InterruptedException e) {
				scheduler.shutdownNow();
				Thread.currentThread().interrupt();
			}
			scheduler = null;
		}
	}
}
