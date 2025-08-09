package com.lapxpert.backend.common.service;

import com.lapxpert.backend.common.util.ValidationUtils;
import com.lapxpert.backend.common.util.ExceptionHandlingUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;

import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Enhanced business entity service template extending BaseService.
 * Specifically designed for complex business entities that require:
 * - Real-time WebSocket notification hooks
 * - Business rule enforcement
 * - Vietnamese field validation
 * - Event publishing for real-time updates
 *
 * This template is intended for business entities like:
 * SanPham, HoaDon, NguoiDung, PhieuGiamGia, DotGiamGia
 *
 * @param <T> Entity type (e.g., SanPham, HoaDon)
 * @param <ID> Primary key type (typically Long)
 * @param <DTO> Data Transfer Object type
 * @param <AUDIT> Audit history entity type
 */
@Slf4j
public abstract class BusinessEntityService<T, ID, DTO, AUDIT> extends BaseService<T, ID, DTO, AUDIT> {

    // Abstract methods for business-specific functionality
    protected abstract ApplicationEventPublisher getEventPublisher();
    protected abstract WebSocketIntegrationService getWebSocketIntegrationService();
    protected abstract void publishEntityCreatedEvent(T entity);
    protected abstract void publishEntityUpdatedEvent(T entity, T oldEntity);
    protected abstract void publishEntityDeletedEvent(ID entityId);
    protected abstract void validateBusinessRules(T entity);
    protected abstract void validateBusinessRulesForUpdate(T entity, T existingEntity);

    /**
     * Create entity with WebSocket notification and event publishing
     */
    @Override
    @Transactional
    public DTO create(DTO dto, String nguoiThucHien, String lyDo) {
        try {
            T entity = toEntity(dto);

            // Validate entity and business rules
            validateEntity(entity);
            validateBusinessRules(entity);

            // Save entity
            T savedEntity = getRepository().save(entity);

            // Create audit trail
            String newValues = buildAuditJson(savedEntity);
            AUDIT auditEntry = createAuditEntry(
                getEntityId(savedEntity),
                "CREATE",
                null,
                newValues,
                nguoiThucHien,
                lyDo != null ? lyDo : String.format("Tạo %s mới", getEntityName())
            );
            getAuditRepository().save(auditEntry);

            // Publish event for real-time updates
            publishEntityCreatedEvent(savedEntity);

            // Send WebSocket notification for real-time data updates
            sendEntityCreatedNotification(savedEntity);

            log.info("Đã tạo {} mới với ID: {} - Người thực hiện: {}",
                    getEntityName(), getEntityId(savedEntity), nguoiThucHien);

            return toDto(savedEntity);

        } catch (Exception e) {
            log.error("Lỗi khi tạo {}: {}", getEntityName(), e.getMessage(), e);
            throw ExceptionHandlingUtils.createBusinessException(
                String.format("Không thể tạo %s mới", getEntityName()), e);
        }
    }

    /**
     * Update entity with WebSocket notification and event publishing
     */
    @Override
    @Transactional
    public DTO update(ID id, DTO dto, String nguoiThucHien, String lyDo) {
        try {
            ValidationUtils.validateId(id, getEntityName());

            T existingEntity = getRepository().findById(id)
                    .orElseThrow(() -> ExceptionHandlingUtils.createNotFoundException(
                        String.format("%s không tồn tại với ID: %s", getEntityName(), id)));

            // Capture old values for audit and event
            String oldValues = buildAuditJson(existingEntity);
            T oldEntityCopy = cloneEntity(existingEntity);

            // Update entity
            T updatedEntity = toEntity(dto);
            setEntityId(updatedEntity, id);

            // Validate entity and business rules
            validateEntity(updatedEntity);
            validateBusinessRulesForUpdate(updatedEntity, existingEntity);

            // Save updated entity
            T savedEntity = getRepository().save(updatedEntity);

            // Create audit trail
            String newValues = buildAuditJson(savedEntity);
            AUDIT auditEntry = createAuditEntry(
                id,
                "UPDATE",
                oldValues,
                newValues,
                nguoiThucHien,
                lyDo != null ? lyDo : String.format("Cập nhật thông tin %s", getEntityName())
            );
            getAuditRepository().save(auditEntry);

            // Publish event for real-time updates
            publishEntityUpdatedEvent(savedEntity, oldEntityCopy);

            // Send WebSocket notification for real-time data updates
            sendEntityUpdatedNotification(savedEntity, oldEntityCopy);

            log.info("Đã cập nhật {} với ID: {} - Người thực hiện: {}",
                    getEntityName(), id, nguoiThucHien);

            return toDto(savedEntity);

        } catch (Exception e) {
            log.error("Lỗi khi cập nhật {} với ID {}: {}", getEntityName(), id, e.getMessage(), e);
            throw ExceptionHandlingUtils.createBusinessException(
                String.format("Không thể cập nhật %s với ID %s", getEntityName(), id), e);
        }
    }

    /**
     * Soft delete with WebSocket notification and event publishing
     */
    @Override
    @Transactional
    public void softDelete(ID id, String nguoiThucHien, String lyDo) {
        try {
            ValidationUtils.validateId(id, getEntityName());

            T entity = getRepository().findById(id)
                    .orElseThrow(() -> ExceptionHandlingUtils.createNotFoundException(
                        String.format("%s không tồn tại với ID: %s", getEntityName(), id)));

            // Capture old values for audit
            String oldValues = buildAuditJson(entity);

            // Perform soft delete
            setSoftDeleteStatus(entity, false);
            getRepository().save(entity);

            // Create audit trail
            AUDIT auditEntry = createAuditEntry(
                id,
                "SOFT_DELETE",
                oldValues,
                null,
                nguoiThucHien,
                lyDo != null ? lyDo : String.format("Xóa mềm %s", getEntityName())
            );
            getAuditRepository().save(auditEntry);

            // Publish event for real-time updates
            publishEntityDeletedEvent(id);

            // Send WebSocket notification for real-time data updates
            sendEntityDeletedNotification(id);

            log.info("Đã xóa mềm {} với ID: {} - Người thực hiện: {}",
                    getEntityName(), id, nguoiThucHien);

        } catch (Exception e) {
            log.error("Lỗi khi xóa mềm {} với ID {}: {}", getEntityName(), id, e.getMessage(), e);
            throw ExceptionHandlingUtils.createBusinessException(
                String.format("Không thể xóa %s với ID %s", getEntityName(), id), e);
        }
    }

    /**
     * Find all - Direct database access for real-time data
     */
    @Override
    @Transactional(readOnly = true)
    public List<DTO> findAll() {
        try {
            List<T> entities = getRepository().findAll();
            return entities.stream()
                    .map(this::toDto)
                    .toList();
        } catch (Exception e) {
            log.error("Lỗi khi tìm tất cả {}: {}", getEntityName(), e.getMessage(), e);
            throw ExceptionHandlingUtils.createBusinessException(
                String.format("Không thể tải danh sách %s", getEntityName()), e);
        }
    }

    /**
     * Find by ID - Direct database access for real-time data
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<DTO> findById(ID id) {
        try {
            ValidationUtils.validateId(id, getEntityName());

            Optional<T> entityOpt = getRepository().findById(id);
            return entityOpt.map(this::toDto);
        } catch (Exception e) {
            log.error("Lỗi khi tìm {} với ID {}: {}", getEntityName(), id, e.getMessage(), e);
            throw ExceptionHandlingUtils.createBusinessException(
                String.format("Không thể tìm %s với ID %s", getEntityName(), id), e);
        }
    }

    /**
     * Batch create with WebSocket notification and event publishing
     */
    @Override
    @Transactional
    public List<DTO> createBatch(List<DTO> dtos, String nguoiThucHien, String lyDo) {
        try {
            List<T> entities = dtos.stream()
                    .map(this::toEntity)
                    .toList();

            // Validate all entities and business rules
            entities.forEach(entity -> {
                validateEntity(entity);
                validateBusinessRules(entity);
            });

            // Save all entities
            List<T> savedEntities = getRepository().saveAll(entities);

            // Create audit trails
            List<AUDIT> auditEntries = savedEntities.stream()
                    .map(entity -> {
                        String newValues = buildAuditJson(entity);
                        return createAuditEntry(
                            getEntityId(entity),
                            "BATCH_CREATE",
                            null,
                            newValues,
                            nguoiThucHien,
                            lyDo != null ? lyDo : String.format("Tạo hàng loạt %s", getEntityName())
                        );
                    })
                    .toList();
            getAuditRepository().saveAll(auditEntries);

            // Publish events for all created entities
            savedEntities.forEach(this::publishEntityCreatedEvent);

            // Send WebSocket notification for batch creation
            sendBatchCreatedNotification(savedEntities);

            log.info("Đã tạo hàng loạt {} {} thực thể - Người thực hiện: {}",
                    savedEntities.size(), getEntityName(), nguoiThucHien);

            return savedEntities.stream()
                    .map(this::toDto)
                    .toList();

        } catch (Exception e) {
            log.error("Lỗi khi tạo hàng loạt {}: {}", getEntityName(), e.getMessage(), e);
            throw ExceptionHandlingUtils.createBusinessException(
                String.format("Không thể tạo hàng loạt %s", getEntityName()), e);
        }
    }

    // Abstract method for entity cloning (needed for event publishing)
    protected abstract T cloneEntity(T entity);

    // ==================== WEBSOCKET NOTIFICATION METHODS ====================

    /**
     * Send WebSocket notification when entity is created
     */
    protected void sendEntityCreatedNotification(T entity) {
        try {
            WebSocketIntegrationService webSocketService = getWebSocketIntegrationService();
            if (webSocketService != null) {
                DTO dto = toDto(entity);
                webSocketService.sendDataListUpdate(
                    getEntityTypeForWebSocket(),
                    "CREATED",
                    dto
                );
            }
        } catch (Exception e) {
            log.warn("Failed to send WebSocket notification for entity creation: {}", e.getMessage());
        }
    }

    /**
     * Send WebSocket notification when entity is updated
     */
    protected void sendEntityUpdatedNotification(T entity, T oldEntity) {
        try {
            WebSocketIntegrationService webSocketService = getWebSocketIntegrationService();
            if (webSocketService != null) {
                DTO dto = toDto(entity);
                webSocketService.sendDataListUpdate(
                    getEntityTypeForWebSocket(),
                    "UPDATED",
                    dto
                );
            }
        } catch (Exception e) {
            log.warn("Failed to send WebSocket notification for entity update: {}", e.getMessage());
        }
    }

    /**
     * Send WebSocket notification when entity is deleted
     */
    protected void sendEntityDeletedNotification(ID entityId) {
        try {
            WebSocketIntegrationService webSocketService = getWebSocketIntegrationService();
            if (webSocketService != null) {
                webSocketService.sendDataListUpdate(
                    getEntityTypeForWebSocket(),
                    "DELETED",
                    entityId
                );
            }
        } catch (Exception e) {
            log.warn("Failed to send WebSocket notification for entity deletion: {}", e.getMessage());
        }
    }

    /**
     * Send WebSocket notification for batch creation
     */
    protected void sendBatchCreatedNotification(List<T> entities) {
        try {
            WebSocketIntegrationService webSocketService = getWebSocketIntegrationService();
            if (webSocketService != null) {
                List<DTO> dtos = entities.stream().map(this::toDto).toList();
                webSocketService.sendDataListUpdate(
                    getEntityTypeForWebSocket(),
                    "BATCH_CREATED",
                    dtos
                );
            }
        } catch (Exception e) {
            log.warn("Failed to send WebSocket notification for batch creation: {}", e.getMessage());
        }
    }

    /**
     * Get entity type for WebSocket notifications
     * Override this method in subclasses to provide specific entity type names
     */
    protected String getEntityTypeForWebSocket() {
        return getEntityName().toLowerCase().replace(" ", "-");
    }
}
