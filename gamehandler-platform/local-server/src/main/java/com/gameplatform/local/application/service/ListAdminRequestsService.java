package com.gameplatform.local.application.service;

import com.gameplatform.local.domain.model.AdminRequestLocal;
import com.gameplatform.local.domain.ports.in.ListAdminRequestsUseCase;
import com.gameplatform.local.domain.ports.out.AdminRequestRepository;
import com.gameplatform.shared.dto.AdminRequestDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Caso d'uso in lettura (PIANO §7.B): restituisce le righe di richieste
 * admin appartenenti a un determinato utente agente, o una singola
 * richiesta per {@code requestId}. Il filtro {@code actingUserId == principal}
 * e' applicato dal controller per impedire letture cross-utente.
 *
 * @see ListAdminRequestsUseCase
 * @see AdminRequestRepository
 */
@Service
@Transactional(readOnly = true)
public class ListAdminRequestsService implements ListAdminRequestsUseCase {

    private final AdminRequestRepository adminRequestRepository;

    /**
     * Costruisce il servizio con il repository delle richieste admin.
     *
     * @param adminRequestRepository il repository per l'accesso alle richieste admin (non null)
     */
    public ListAdminRequestsService(AdminRequestRepository adminRequestRepository) {
        this.adminRequestRepository = adminRequestRepository;
    }

    /**
     * Restituisce tutte le richieste admin per un determinato utente agente.
     *
     * @param actingUserId l'identificativo dell'utente agente
     * @return la lista delle richieste admin (vuota se actingUserId e' null o blank)
     */
    @Override
    public List<AdminRequestDto> listByActingUser(String actingUserId) {
        if (actingUserId == null || actingUserId.isBlank()) {
            return List.of();
        }
        return adminRequestRepository.findByActingUserId(actingUserId).stream()
                .map(ListAdminRequestsService::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Restituisce una singola richiesta admin per requestId.
     *
     * @param requestId l'identificativo della richiesta
     * @return un Optional contenente la richiesta, o vuoto se non trovata o requestId e' blank
     */
    @Override
    public Optional<AdminRequestDto> findByRequestId(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return Optional.empty();
        }
        return adminRequestRepository.findByRequestId(requestId).map(ListAdminRequestsService::toDto);
    }

    /**
     * Converte un {@link AdminRequestLocal} nel corrispondente
     * {@link AdminRequestDto}.
     *
     * @param request l'entita' del modello di dominio (non null)
     * @return il DTO con tutti i campi mappati uno-a-uno
     */
    private static AdminRequestDto toDto(AdminRequestLocal request) {
        return new AdminRequestDto(
                request.getRequestId(),
                request.getEventType(),
                request.getActingUserId(),
                request.getActingRole(),
                request.getBuildingId(),
                request.getPayloadJson(),
                request.getStatus(),
                request.getResultDataJson(),
                request.getCreatedAt(),
                request.getCompletedAt(),
                request.getOutboxEventId()
        );
    }
}