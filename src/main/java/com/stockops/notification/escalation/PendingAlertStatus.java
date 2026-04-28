package com.stockops.notification.escalation;

/**
 * Status of a pending alert in the escalation lifecycle.
 *
 * <ul>
 *   <li>{@link #PENDING} – alert created, awaiting escalation or acknowledgment</li>
 *   <li>{@link #ESCALATED} – all escalation levels exhausted, no acknowledgment received</li>
 *   <li>{@link #ACKNOWLEDGED} – user acknowledged the alert, no further escalation</li>
 * </ul>
 *
 * @author StockOps Team
 * @since 2.0
 */
public enum PendingAlertStatus {

    /** Alert created, awaiting escalation or acknowledgment. */
    PENDING,

    /** All escalation levels exhausted without acknowledgment. */
    ESCALATED,

    /** User acknowledged the alert; no further escalation. */
    ACKNOWLEDGED
}