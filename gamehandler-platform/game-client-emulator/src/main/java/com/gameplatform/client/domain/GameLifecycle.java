package com.gameplatform.client.domain;

import com.fasterxml.jackson.databind.ser.impl.UnknownSerializer;
import com.gameplatform.shared.domain.model.StopReason;
import com.gameplatform.shared.domain.model.UserId;

import java.util.List;

public interface GameLifecycle {
    void start(List<UserId> participants);
    void stop(StopReason reason);
    void pause();
    void resume();
    List<UserId> getParticipants();
}
