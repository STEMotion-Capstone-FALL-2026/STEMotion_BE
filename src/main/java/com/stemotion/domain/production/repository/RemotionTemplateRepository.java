package com.stemotion.domain.production.repository;

import com.stemotion.domain.production.entity.RemotionTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RemotionTemplateRepository extends JpaRepository<RemotionTemplate, String> {

    List<RemotionTemplate> findByIsActiveTrueOrderByNameAsc();

    List<RemotionTemplate> findByCategoryAndIsActiveTrue(String category);

    Optional<RemotionTemplate> findByName(String name);
}
