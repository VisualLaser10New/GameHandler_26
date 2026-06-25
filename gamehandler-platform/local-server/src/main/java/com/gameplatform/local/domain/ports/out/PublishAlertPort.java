package com.gameplatform.local.domain.ports.out;

import com.gameplatform.shared.mqtt.payload.AlertPayload;

public interface PublishAlertPort {
    void publishAlert(AlertPayload payload);
}
