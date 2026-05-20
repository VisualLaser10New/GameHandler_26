package com.gameplatform.central.domain.ports.out;

import com.gameplatform.central.domain.model.RegisteredLocalServer;
import java.util.List;

public interface LocalServerRegistryPort {
    List<RegisteredLocalServer> getActiveLocalServers();
    void register(RegisteredLocalServer server);
}

