package com.hyperplane.rodo.repository;

import com.hyperplane.rodo.model.SystemSettings;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SystemSettingsRepository extends ReactiveMongoRepository<SystemSettings, String> {
}
