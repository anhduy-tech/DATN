package com.lapxpert.backend.websocket.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Batch processing information for WebSocket messages
 * Vietnamese Business Context: Thông tin xử lý hàng loạt cho tin nhắn WebSocket
 * 
 * Used when multiple related updates are sent together to optimize
 * frontend processing and reduce UI flickering during bulk operations.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BatchInfo {

    /**
     * Unique identifier for the batch
     * Vietnamese: Mã định danh duy nhất cho lô
     */
    private String batchId;

    /**
     * Total number of items in the batch
     * Vietnamese: Tổng số mục trong lô
     */
    private Integer batchSize;

    /**
     * Current item index in the batch (0-based)
     * Vietnamese: Chỉ số mục hiện tại trong lô
     */
    private Integer batchIndex;

    /**
     * Whether this is the last message in the batch
     * Vietnamese: Có phải là tin nhắn cuối cùng trong lô không
     */
    private Boolean isLastInBatch;

    /**
     * Batch processing mode
     * Vietnamese: Chế độ xử lý hàng loạt
     * Values: SEQUENTIAL, PARALLEL, BULK_UPDATE
     */
    private String processingMode;

    /**
     * Expected completion time for the entire batch (ISO 8601)
     * Vietnamese: Thời gian hoàn thành dự kiến cho toàn bộ lô
     */
    private String expectedCompletionTime;

    /**
     * Batch metadata for additional context
     * Vietnamese: Metadata lô cho ngữ cảnh bổ sung
     */
    private Object metadata;

    /**
     * Create batch info for a single item in a batch
     */
    public static BatchInfo forItem(String batchId, int batchSize, int batchIndex, String processingMode) {
        BatchInfo batchInfo = new BatchInfo();
        batchInfo.setBatchId(batchId);
        batchInfo.setBatchSize(batchSize);
        batchInfo.setBatchIndex(batchIndex);
        batchInfo.setIsLastInBatch(batchIndex == batchSize - 1);
        batchInfo.setProcessingMode(processingMode);
        return batchInfo;
    }

    /**
     * Create batch info for the last item in a batch
     */
    public static BatchInfo forLastItem(String batchId, int batchSize, String processingMode) {
        return forItem(batchId, batchSize, batchSize - 1, processingMode);
    }

    /**
     * Create batch info for bulk update operations
     */
    public static BatchInfo forBulkUpdate(String batchId, int batchSize, Object metadata) {
        BatchInfo batchInfo = new BatchInfo();
        batchInfo.setBatchId(batchId);
        batchInfo.setBatchSize(batchSize);
        batchInfo.setBatchIndex(0);
        batchInfo.setIsLastInBatch(true);
        batchInfo.setProcessingMode("BULK_UPDATE");
        batchInfo.setMetadata(metadata);
        return batchInfo;
    }
}
