package com.gameplatform.local.domain.ports.in;

import com.gameplatform.local.domain.model.Reservation;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.UserId;
import java.util.List;

public interface GetReservationsUseCase {
    List<Reservation> getByUser(UserId userId);
    List<Reservation> getByGame(GameId gameId);
}
