package com.gameplatform.local.domain.ports.out;

import com.gameplatform.local.domain.model.DeadLetterEvent;

public interface DeadLetterRepository {

    void save(DeadLetterEvent event);

    long count();
}
