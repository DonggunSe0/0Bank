package com.team10.backend.domain.youngPolicy.repository;

import com.team10.backend.domain.youngPolicy.entity.YoungPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface YoungPolicyRepository extends JpaRepository<YoungPolicy, Long> {
}
