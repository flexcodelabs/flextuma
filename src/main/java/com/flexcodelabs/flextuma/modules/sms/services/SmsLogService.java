package com.flexcodelabs.flextuma.modules.sms.services;

import com.flexcodelabs.flextuma.core.entities.sms.SmsLog;
import com.flexcodelabs.flextuma.core.enums.SmsLogStatus;
import com.flexcodelabs.flextuma.core.repositories.SmsLogRepository;
import com.flexcodelabs.flextuma.core.services.BaseService;
import com.flexcodelabs.flextuma.core.helpers.CurrentUserResolver;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class SmsLogService extends BaseService<SmsLog> {

    private final SmsLogRepository smsLogRepository;
    private final CurrentUserResolver currentUserResolver;

    public SmsLogService(SmsLogRepository repository, CurrentUserResolver currentUserResolver) {
        this.smsLogRepository = repository;
        this.currentUserResolver = currentUserResolver;
    }

    @Override
    protected org.springframework.data.jpa.repository.JpaRepository<SmsLog, UUID> getRepository() {
        return smsLogRepository;
    }

    @Override
    protected String getReadPermission() {
        return SmsLog.READ;
    }

    @Override
    protected String getAddPermission() {
        return SmsLog.ADD;
    }

    @Override
    protected String getUpdatePermission() {
        return SmsLog.UPDATE;
    }

    @Override
    protected String getDeletePermission() {
        return SmsLog.DELETE;
    }

    @Override
    public String getEntityPlural() {
        return SmsLog.NAME_PLURAL;
    }

    @Override
    public String getPropertyName() {
        return SmsLog.PLURAL;
    }

    @Override
    protected String getEntitySingular() {
        return SmsLog.NAME_SINGULAR;
    }

    @Override
    protected org.springframework.data.jpa.repository.JpaSpecificationExecutor<SmsLog> getRepositoryAsExecutor() {
        return smsLogRepository;
    }

    @Override
    protected String getTableName() {
        return "smslog";
    }

    @Override
    protected void onPreSave(SmsLog entity) {
        throw new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED, "SMS logs cannot be created manually");
    }

    @Override
    protected SmsLog onPreUpdate(SmsLog newEntity, SmsLog oldEntity) {
        throw new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED, "SMS logs cannot be updated manually");
    }

    @Override
    protected void validateDelete(SmsLog entity) {
        throw new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED, "SMS logs cannot be deleted");
    }

    @Transactional
    public SmsLog retryFailedMessage(UUID id) {
        checkPermission(SmsLog.UPDATE);

        SmsLog log = smsLogRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "SMS log not found"));

        boolean ownsLog = currentUserResolver.getCurrentUser().map(user -> log.getCreatedBy() != null && user.getId().equals(log.getCreatedBy().getId())).orElse(false);
        if (!Boolean.TRUE.equals(isAdminPermission()) && !ownsLog) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have access to this SMS log");

        if (log.getStatus() != SmsLogStatus.FAILED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only failed messages can be retried");
        }

        log.setStatus(SmsLogStatus.PENDING);
        log.setRetries(log.getRetries() + 1);
        log.setError(null);
        log.setProviderResponse(null);

        return smsLogRepository.save(log);
    }
}
