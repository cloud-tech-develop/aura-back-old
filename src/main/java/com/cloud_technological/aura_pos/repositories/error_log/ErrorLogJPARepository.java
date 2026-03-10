package com.cloud_technological.aura_pos.repositories.error_log;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.entity.ErrorLogEntity;

@Repository
public interface ErrorLogJPARepository extends JpaRepository<ErrorLogEntity, Long> {
}
