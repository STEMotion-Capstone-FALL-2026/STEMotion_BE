package com.stemotion.domain.collab.repository;

import com.stemotion.domain.collab.entity.CollabEnums.LeaseResource;
import com.stemotion.domain.collab.entity.EditLease;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface EditLeaseRepository extends JpaRepository<EditLease, String> {
    Optional<EditLease> findByResourceTypeAndResourceId(LeaseResource resourceType, String resourceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from EditLease l where l.resourceType = :type and l.resourceId = :resourceId")
    Optional<EditLease> lockByResource(@Param("type") LeaseResource type, @Param("resourceId") String resourceId);
}
