package com.gameplatform.shared.domain.model;

public record BuildingId(String id) {
    public BuildingId {
        if (id == null || id.isBlank())
            throw new IllegalArgumentException("BuildingId cannot be null");
    }
}
