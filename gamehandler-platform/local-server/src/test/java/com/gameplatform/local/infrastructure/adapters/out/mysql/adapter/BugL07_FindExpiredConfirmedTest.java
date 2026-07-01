package com.gameplatform.local.infrastructure.adapters.out.mysql.adapter;

import com.gameplatform.local.infrastructure.adapters.out.mysql.mapper.ReservationMapper;
import com.gameplatform.local.infrastructure.adapters.out.mysql.repository.ReservationJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Bug L-07: ReservationRepository.findExpired(now) is documented as status IN
 * (PENDING, CONFIRMED) and end_time < now, but the adapter queries only PENDING.
 */
@ExtendWith(MockitoExtension.class)
class BugL07_FindExpiredConfirmedTest {

    @Mock ReservationJpaRepository jpaRepository;

    @Test
    @DisplayName("BUG L-07: findExpired must query both PENDING and CONFIRMED reservations")
    void findExpiredShouldIncludeConfirmedReservations() {
        Instant now = Instant.parse("2026-06-29T08:00:00Z");
        when(jpaRepository.findByStatusInAndEndTimeBefore(org.mockito.ArgumentMatchers.anyCollection(), eq(now)))
                .thenReturn(List.of());

        ReservationRepositoryAdapter adapter = new ReservationRepositoryAdapter(jpaRepository, new ReservationMapper());
        adapter.findExpired(now);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> statusesCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(jpaRepository).findByStatusInAndEndTimeBefore(statusesCaptor.capture(), eq(now));

        Collection<String> statuses = statusesCaptor.getValue();
        assertTrue(statuses.contains("PENDING"), "Expired lookup must include PENDING reservations.");
        assertTrue(statuses.contains("CONFIRMED"), "Expired lookup must also include CONFIRMED reservations per point-5 contract.");
    }
}
