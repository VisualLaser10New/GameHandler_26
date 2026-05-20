package com.gameplatform.central.infrastructure.adapters.out.mysql.repository;

import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.RegisteredLocalServerJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LocalServerJpaRepository extends JpaRepository<RegisteredLocalServerJpaEntity, String> {
    List<RegisteredLocalServerJpaEntity> findByIsActiveTrue();
}
