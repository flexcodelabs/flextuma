package com.flexcodelabs.flextuma.core.repositories;

import com.flexcodelabs.flextuma.core.entities.sms.SmsCampaign;
import com.flexcodelabs.flextuma.core.enums.SmsCampaignStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface SmsCampaignRepository extends BaseRepository<SmsCampaign, UUID>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<SmsCampaign> {

    @Query("SELECT c FROM SmsCampaign c WHERE c.status = :status AND c.scheduledAt <= :now")
    List<SmsCampaign> findDueCampaigns(
            @Param("status") SmsCampaignStatus status,
            @Param("now") LocalDateTime now,
            Pageable pageable);
}
