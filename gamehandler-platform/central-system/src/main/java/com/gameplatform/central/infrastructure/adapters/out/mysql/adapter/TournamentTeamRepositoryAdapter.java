package com.gameplatform.central.infrastructure.adapters.out.mysql.adapter;

import com.gameplatform.central.domain.model.Team;
import com.gameplatform.central.domain.ports.out.TournamentTeamRepository;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.TournamentTeamJpaEntity;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.TournamentTeamMemberJpaEntity;
import com.gameplatform.central.infrastructure.adapters.out.mysql.mapper.TeamMapper;
import com.gameplatform.central.infrastructure.adapters.out.mysql.repository.TournamentTeamJpaRepository;
import com.gameplatform.central.infrastructure.adapters.out.mysql.repository.TournamentTeamMemberJpaRepository;
import com.gameplatform.shared.domain.model.TeamId;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.UserId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JPA adapter for the {@link TournamentTeamRepository} port. Mirrors the
 * {@code GameDefinitionRepositoryAdapter} / {@code LocalAdminBuildingRepositoryAdapter}
 * shape but constructor-injects <em>two</em> JPA repositories ({@code tournament_teams}
 * + {@code tournament_team_members}) per FASE 4 PIANO &sect;3.2 &amp; decision C.2:
 * {@link #save} is an atomic delete-all-then-insert over the members join-table
 * (writes carry the default {@code @Transactional} propagation so the whole
 * replace-members operation commits as one). All reads are marked
 * {@code @Transactional(readOnly = true)} and are null-safe
 * ({@code Optional.empty()} / {@code List.of()} / {@code false} on {@code null} args).
 */
@Component
public class TournamentTeamRepositoryAdapter implements TournamentTeamRepository {

    private final TournamentTeamJpaRepository teamRepo;
    private final TournamentTeamMemberJpaRepository memberRepo;
    private final TeamMapper mapper;

    /**
     * Costruisce l'adapter iniettando i repository JPA delle squadre e dei membri e il mapper dei team.
     *
     * @param teamRepo   repository JPA per la gestione delle entit&agrave; di squadra
     * @param memberRepo repository JPA per la gestione delle entit&agrave; di appartenenza ai membri
     * @param mapper     mapper che converte tra il modello di dominio e le entit&agrave; JPA
     */
    public TournamentTeamRepositoryAdapter(TournamentTeamJpaRepository teamRepo,
                                          TournamentTeamMemberJpaRepository memberRepo,
                                          TeamMapper mapper) {
        this.teamRepo = teamRepo;
        this.memberRepo = memberRepo;
        this.mapper = mapper;
    }

    /**
     * Salva (o aggiorna) una squadra, ricostruendone atomicamente l'elenco dei membri.
     *
     * @param team la squadra da persistere; se {@code null} restituisce {@code null}
     * @return la squadra salvata, o {@code null} se l'input era {@code null}
     * @see TournamentTeamJpaRepository#save
     */
    @Override
    @Transactional
    public Team save(Team team) {
        if (team == null) {
            return null;
        }
        teamRepo.save(mapper.toTeamEntity(team));
        memberRepo.deleteByTeamId(team.getTeamId().value());
        List<TournamentTeamMemberJpaEntity> memberEntities = mapper.toMemberEntities(team);
        for (TournamentTeamMemberJpaEntity memberEntity : memberEntities) {
            memberRepo.save(memberEntity);
        }
        return team;
    }

    /**
     * Restituisce la squadra identificata dal relativo identificativo, inclusi i membri.
     *
     * @param teamId l'identificativo della squadra; se {@code null} restituisce {@link Optional#empty()}
     * @return l'{@link Optional} contenente la squadra trovata, o vuoto se assente o se {@code teamId} &egrave; {@code null}
     * @see TournamentTeamJpaRepository#findById
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<Team> findById(TeamId teamId) {
        if (teamId == null) {
            return Optional.empty();
        }
        Optional<TournamentTeamJpaEntity> teamOpt = teamRepo.findById(teamId.value());
        if (teamOpt.isEmpty()) {
            return Optional.empty();
        }
        List<TournamentTeamMemberJpaEntity> members = memberRepo.findByTeamId(teamId.value());
        return Optional.of(mapper.toDomain(teamOpt.get(), members));
    }

    /**
     * Restituisce l'elenco delle squadre associate a un torneo, ciascuna con i propri membri.
     *
     * @param tournamentId l'identificativo del torneo; se {@code null} restituisce una lista vuota
     * @return la lista delle squadre del torneo; lista vuota se non ve ne sono o se {@code tournamentId} &egrave; {@code null}
     * @see TournamentTeamJpaRepository#findByTournamentId
     */
    @Override
    @Transactional(readOnly = true)
    public List<Team> findByTournament(TournamentId tournamentId) {
        if (tournamentId == null) {
            return List.of();
        }
        List<TournamentTeamJpaEntity> teams = teamRepo.findByTournamentId(tournamentId.value());
        if (teams == null) {
            return List.of();
        }
        List<Team> result = new ArrayList<>(teams.size());
        for (TournamentTeamJpaEntity team : teams) {
            List<TournamentTeamMemberJpaEntity> members = memberRepo.findByTeamId(team.getId());
            result.add(mapper.toDomain(team, members));
        }
        return result;
    }

    /**
     * Restituisce la squadra di un torneo individuata dal relativo nome.
     *
     * @param tournamentId l'identificativo del torneo; se {@code null} restituisce {@link Optional#empty()}
     * @param name         il nome della squadra; se {@code null} restituisce {@link Optional#empty()}
     * @return l'{@link Optional} contenente la squadra trovata, o vuoto se assente o se un argomento &egrave; {@code null}
     * @see TournamentTeamJpaRepository#findByTournamentIdAndName
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<Team> findByTournamentAndName(TournamentId tournamentId, String name) {
        if (tournamentId == null || name == null) {
            return Optional.empty();
        }
        return teamRepo.findByTournamentIdAndName(tournamentId.value(), name)
                .map(team -> {
                    List<TournamentTeamMemberJpaEntity> members = memberRepo.findByTeamId(team.getId());
                    return mapper.toDomain(team, members);
                });
    }

    /**
     * Restituisce la squadra di un torneo di cui fa parte l'utente indicato.
     *
     * @param tournamentId  l'identificativo del torneo; se {@code null} restituisce {@link Optional#empty()}
     * @param memberUserId  l'identificativo dell'utente membro; se {@code null} restituisce {@link Optional#empty()}
     * @return l'{@link Optional} contenente la squadra trovata, o vuoto se assente o se un argomento &egrave; {@code null}
     * @see TournamentTeamMemberJpaRepository#findTeamIdsByUserId
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<Team> findByTournamentAndMember(TournamentId tournamentId, UserId memberUserId) {
        if (tournamentId == null || memberUserId == null) {
            return Optional.empty();
        }
        List<String> teamIds = memberRepo.findTeamIdsByUserId(memberUserId.value());
        if (teamIds == null) {
            return Optional.empty();
        }
        for (String teamId : teamIds) {
            Optional<TournamentTeamJpaEntity> teamOpt = teamRepo.findById(teamId);
            if (teamOpt.isPresent() && teamOpt.get().getTournamentId().equals(tournamentId.value())) {
                List<TournamentTeamMemberJpaEntity> members = memberRepo.findByTeamId(teamId);
                return Optional.of(mapper.toDomain(teamOpt.get(), members));
            }
        }
        return Optional.empty();
    }

    /**
     * Verifica l'esistenza di una squadra in un torneo per il nome indicato.
     *
     * @param tournamentId l'identificativo del torneo; se {@code null} restituisce {@code false}
     * @param name         il nome della squadra; se {@code null} restituisce {@code false}
     * @return {@code true} se la squadra esiste, {@code false} altrimenti
     * @see TournamentTeamJpaRepository#existsByTournamentIdAndName
     */
    @Override
    @Transactional(readOnly = true)
    public boolean existsByTournamentAndName(TournamentId tournamentId, String name) {
        if (tournamentId == null || name == null) {
            return false;
        }
        return teamRepo.existsByTournamentIdAndName(tournamentId.value(), name);
    }

    /**
     * Elimina la squadra identificata, inclusi i relativi membri.
     *
     * @param teamId l'identificativo della squadra da eliminare; se {@code null} il metodo non effettua alcuna operazione
     * @see TournamentTeamMemberJpaRepository#deleteByTeamId
     */
    @Override
    @Transactional
    public void deleteById(TeamId teamId) {
        if (teamId == null) {
            return;
        }
        memberRepo.deleteByTeamId(teamId.value());
        teamRepo.deleteById(teamId.value());
    }
}