package com.newtonite.ops.dto;

import lombok.*;

import java.util.Map;

/**
 * Summary metrics and statistics for dashboard visualization.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardStatsResponse {

    private long totalWorkItems;
    private long openCount;
    private long inProgressCount;
    private long awaitingApprovalCount;
    private long resolvedCount;
    private long closedCount;

    private long criticalPriorityCount;
    private long highPriorityCount;
    private long mediumPriorityCount;
    private long lowPriorityCount;

    private long bugTypeCount;
    private long incidentTypeCount;
    private long taskTypeCount;
    private long featureTypeCount;

    private Map<String, Long> statusBreakdown;
    private Map<String, Long> priorityBreakdown;
    private Map<String, Long> typeBreakdown;
}
