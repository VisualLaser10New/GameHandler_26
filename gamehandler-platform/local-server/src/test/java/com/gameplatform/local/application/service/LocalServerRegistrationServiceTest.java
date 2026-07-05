package com.gameplatform.local.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.gameplatform.local.domain.ports.out.RegisterLocalServerPort;
import com.gameplatform.local.domain.ports.out.SyncCentralSystemPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LocalServerRegistrationServiceTest {

    @Mock RegisterLocalServerPort registerLocalServerPort;
    @Mock SyncCentralSystemPort syncCentralSystemPort;

    private LocalServerRegistrationService service;

    @BeforeEach
    void setup() {
        service = new LocalServerRegistrationService(registerLocalServerPort, syncCentralSystemPort);
    }

    @Test
    void registersOnFirstAttemptWhenReachableAndAccepted() {
        when(syncCentralSystemPort.isReachable()).thenReturn(true);
        when(registerLocalServerPort.register()).thenReturn(true);

        boolean ok = service.register();

        assertThat(ok).isTrue();
        assertThat(service.isRegistered()).isTrue();
        verify(registerLocalServerPort, times(1)).register();
    }

    @Test
    void retriesWhenCentralUnreachableThenSucceeds() {
        when(syncCentralSystemPort.isReachable())
                .thenReturn(false)
                .thenReturn(false)
                .thenReturn(true);
        when(registerLocalServerPort.register()).thenReturn(true);

        boolean ok = service.register();

        assertThat(ok).isTrue();
        verify(syncCentralSystemPort, times(3)).isReachable();
        verify(registerLocalServerPort, times(1)).register();
    }

    @Test
    void retriesWhenRegistrationRejectedThenSucceeds() {
        when(syncCentralSystemPort.isReachable()).thenReturn(true);
        when(registerLocalServerPort.register())
                .thenReturn(false)
                .thenReturn(false)
                .thenReturn(true);

        boolean ok = service.register();

        assertThat(ok).isTrue();
        verify(registerLocalServerPort, times(3)).register();
    }

    @Test
    void stopInterruptsRetryLoop() throws InterruptedException {
        when(syncCentralSystemPort.isReachable()).thenReturn(false);

        service.start(); // launches daemon worker thread
        // let it enter the first sleep (initial delay ~1s)
        Thread.sleep(100);
        service.stop(); // interrupts the worker → loop exits

        // wait briefly for the worker to terminate
        Thread.sleep(200);
        assertThat(service.isRegistered()).isFalse();
        assertThat(service.isRunning()).isFalse();
    }
}
