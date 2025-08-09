package com.lapxpert.backend.websocket.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Throttling information for WebSocket messages
 * Vietnamese Business Context: Thông tin điều tiết cho tin nhắn WebSocket
 * 
 * Used to control message frequency and prevent overwhelming the frontend
 * with too many updates during high-activity periods.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ThrottleInfo {

    /**
     * Throttle key for grouping related messages
     * Vietnamese: Khóa điều tiết để nhóm các tin nhắn liên quan
     */
    private String throttleKey;

    /**
     * Throttle interval in milliseconds
     * Vietnamese: Khoảng thời gian điều tiết tính bằng mili giây
     */
    private Long throttleIntervalMs;

    /**
     * Number of messages throttled/skipped before this one
     * Vietnamese: Số lượng tin nhắn bị điều tiết/bỏ qua trước tin nhắn này
     */
    private Integer throttledCount;

    /**
     * Whether this message was delayed due to throttling
     * Vietnamese: Tin nhắn này có bị trễ do điều tiết không
     */
    private Boolean wasDelayed;

    /**
     * Next allowed send time (ISO 8601)
     * Vietnamese: Thời gian gửi được phép tiếp theo
     */
    private String nextAllowedSendTime;

    /**
     * Throttling strategy used
     * Vietnamese: Chiến lược điều tiết được sử dụng
     * Values: RATE_LIMIT, DEBOUNCE, BATCH_COLLECT, PRIORITY_QUEUE
     */
    private String throttleStrategy;

    /**
     * Priority level for throttled messages
     * Vietnamese: Mức độ ưu tiên cho tin nhắn được điều tiết
     * Values: HIGH, MEDIUM, LOW
     */
    private String priority;

    /**
     * Additional throttling metadata
     * Vietnamese: Metadata điều tiết bổ sung
     */
    private Object metadata;

    /**
     * Create throttle info for rate-limited messages
     */
    public static ThrottleInfo forRateLimit(String throttleKey, long intervalMs, int throttledCount) {
        ThrottleInfo throttleInfo = new ThrottleInfo();
        throttleInfo.setThrottleKey(throttleKey);
        throttleInfo.setThrottleIntervalMs(intervalMs);
        throttleInfo.setThrottledCount(throttledCount);
        throttleInfo.setThrottleStrategy("RATE_LIMIT");
        throttleInfo.setWasDelayed(throttledCount > 0);
        return throttleInfo;
    }

    /**
     * Create throttle info for debounced messages
     */
    public static ThrottleInfo forDebounce(String throttleKey, long intervalMs, boolean wasDelayed) {
        ThrottleInfo throttleInfo = new ThrottleInfo();
        throttleInfo.setThrottleKey(throttleKey);
        throttleInfo.setThrottleIntervalMs(intervalMs);
        throttleInfo.setThrottleStrategy("DEBOUNCE");
        throttleInfo.setWasDelayed(wasDelayed);
        return throttleInfo;
    }

    /**
     * Create throttle info for batch collected messages
     */
    public static ThrottleInfo forBatchCollect(String throttleKey, long intervalMs, int collectedCount) {
        ThrottleInfo throttleInfo = new ThrottleInfo();
        throttleInfo.setThrottleKey(throttleKey);
        throttleInfo.setThrottleIntervalMs(intervalMs);
        throttleInfo.setThrottledCount(collectedCount);
        throttleInfo.setThrottleStrategy("BATCH_COLLECT");
        throttleInfo.setWasDelayed(true);
        return throttleInfo;
    }

    /**
     * Create throttle info for priority queue messages
     */
    public static ThrottleInfo forPriorityQueue(String throttleKey, String priority, boolean wasDelayed) {
        ThrottleInfo throttleInfo = new ThrottleInfo();
        throttleInfo.setThrottleKey(throttleKey);
        throttleInfo.setThrottleStrategy("PRIORITY_QUEUE");
        throttleInfo.setPriority(priority);
        throttleInfo.setWasDelayed(wasDelayed);
        return throttleInfo;
    }
}
