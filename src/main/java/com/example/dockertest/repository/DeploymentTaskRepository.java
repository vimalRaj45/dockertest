package com.example.dockertest.repository;

import com.example.dockertest.model.DeploymentTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeploymentTaskRepository extends JpaRepository<DeploymentTask, Long> {
}
