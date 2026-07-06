package com.gameplatform.client.application.service;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class Service {
	protected ScheduledFuture<?> scheduledTask;
	protected ScheduledExecutorService scheduler;

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
