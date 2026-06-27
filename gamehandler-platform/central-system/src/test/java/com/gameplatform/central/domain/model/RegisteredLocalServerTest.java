package com.gameplatform.central.domain.model;

import com.gameplatform.shared.domain.model.BuildingId;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegisteredLocalServerTest {

    @Test
    void shouldCreateRegisteredLocalServerSuccessfullyWhenInputsAreValid() {
        BuildingId buildingId = new BuildingId("bld-1");
        Instant now = Instant.now();

        RegisteredLocalServer server = new RegisteredLocalServer(buildingId, "http://localhost:8080", now, true);

        assertThat(server.getBuildingId()).isEqualTo(buildingId);
        assertThat(server.getBaseUrl()).isEqualTo("http://localhost:8080");
        assertThat(server.getLastSeenAt()).isEqualTo(now);
        assertThat(server.isActive()).isTrue();
    }

    @Test
    void shouldThrowExceptionWhenConstructorInputsAreInvalid() {
        BuildingId validBuildingId = new BuildingId("bld-1");
        Instant now = Instant.now();

        // Null BuildingId
        assertThatThrownBy(() -> new RegisteredLocalServer(null, "http://localhost:8080", now, true))
                .isInstanceOf(IllegalArgumentException.class);

        // Null/empty/blank baseUrl
        assertThatThrownBy(() -> new RegisteredLocalServer(validBuildingId, null, now, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RegisteredLocalServer(validBuildingId, "", now, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RegisteredLocalServer(validBuildingId, "   ", now, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldUpdateLastSeenAndValidate() {
        RegisteredLocalServer server = new RegisteredLocalServer(new BuildingId("bld-1"), "http://localhost", Instant.now(), true);
        Instant newTime = Instant.now().plusSeconds(10);

        server.updateLastSeen(newTime);
        assertThat(server.getLastSeenAt()).isEqualTo(newTime);

        assertThatThrownBy(() -> server.updateLastSeen(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldSetActive() {
        RegisteredLocalServer server = new RegisteredLocalServer(new BuildingId("bld-1"), "http://localhost", Instant.now(), true);

        server.setActive(false);
        assertThat(server.isActive()).isFalse();

        server.setActive(true);
        assertThat(server.isActive()).isTrue();
    }

    @Test
    void shouldImplementEqualsAndHashCodeBasedOnBuildingId() {
        BuildingId id1 = new BuildingId("bld-1");
        BuildingId id2 = new BuildingId("bld-1");
        BuildingId id3 = new BuildingId("bld-2");

        RegisteredLocalServer server1 = new RegisteredLocalServer(id1, "http://localhost:1", Instant.now(), true);
        RegisteredLocalServer server2 = new RegisteredLocalServer(id2, "http://localhost:2", Instant.now(), false);
        RegisteredLocalServer server3 = new RegisteredLocalServer(id3, "http://localhost:1", Instant.now(), true);

        assertThat(server1).isEqualTo(server2);
        assertThat(server1).isNotEqualTo(server3);
        assertThat(server1.hashCode()).isEqualTo(server2.hashCode());
        assertThat(server1.hashCode()).isNotEqualTo(server3.hashCode());
    }
}
