package com.stockops.notification.escalation;

import com.stockops.entity.Role;
import com.stockops.entity.User;
import com.stockops.repository.UserRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Scheduled job that checks pending alerts every minute and escalates
 * unacknowledged alerts according to their escalation policy rules.
 *
 * <p>For each PENDING alert:
 * <ol>
 *   <li>Resolve the EscalationPolicy for (centerId, warehouseId, alertType)</li>
 *   <li>Get rules ordered by level</li>
 *   <li>Find the next rule whose delayMinutes has elapsed since alert creation</li>
 *   <li>If next rule found: dispatch notification, increment currentLevel</li>
 *   <li>If no more rules and not acknowledged: mark ESCALATED</li>
 * </ol>
 *
 * @author StockOps Team
 * @since 2.0
 * @see PendingAlert
 * @see EscalationDispatcher
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EscalationScheduler {

    private final PendingAlertRepository pendingAlertRepository;
    private final EscalationService escalationService;
    private final EscalationDispatcher escalationDispatcher;
    private final UserRepository userRepository;
    private final TransactionTemplate transactionTemplate;

    @Scheduled(fixedDelay = 60_000)
    public void checkAndEscalate() {
        log.debug("Starting escalation check cycle");

        final List<PendingAlert> pendingAlerts = pendingAlertRepository.findByStatus(PendingAlertStatus.PENDING);
        if (pendingAlerts.isEmpty()) {
            return;
        }

        log.info("Found {} pending alerts to evaluate", pendingAlerts.size());

        int escalated = 0;
        int dispatched = 0;

        for (final PendingAlert alert : pendingAlerts) {
            try {
                final boolean result = processAlert(alert);
                if (result) {
                    dispatched++;
                } else if (alert.getStatus() == PendingAlertStatus.ESCALATED) {
                    escalated++;
                }
            } catch (final Exception e) {
                log.error("Failed to process pending alert id={}: {}", alert.getId(), e.getMessage(), e);
            }
        }

        if (dispatched > 0 || escalated > 0) {
            log.info("Escalation cycle complete: dispatched={}, fullyEscalated={}", dispatched, escalated);
        }
    }

    private boolean processAlert(final PendingAlert alert) {
        final Optional<EscalationPolicy> policyOpt = escalationService.resolvePolicy(
                alert.getCenterId(), alert.getWarehouseId(), alert.getAlertType());

        if (policyOpt.isEmpty()) {
            log.warn("No escalation policy found for alert id={}, centerId={}, warehouseId={}, alertType={}",
                    alert.getId(), alert.getCenterId(), alert.getWarehouseId(), alert.getAlertType());
            return false;
        }

        final EscalationPolicy policy = policyOpt.get();
        final List<EscalationRule> rules = policy.getRules().stream()
                .sorted(Comparator.comparingInt(EscalationRule::getLevel))
                .toList();

        if (rules.isEmpty()) {
            log.warn("Escalation policy id={} has no rules, skipping alert id={}", policy.getId(), alert.getId());
            return false;
        }

        final int nextLevelIndex = alert.getCurrentLevel();
        final Instant now = Instant.now();

        if (nextLevelIndex >= rules.size()) {
            markEscalated(alert);
            return false;
        }

        final EscalationRule nextRule = rules.get(nextLevelIndex);
        final Instant escalationTime = alert.getCreatedAt().plusSeconds(
                cumulativeDelaySeconds(rules, nextLevelIndex));

        if (now.isAfter(escalationTime) || now.equals(escalationTime)) {
            dispatchAndAdvance(alert, nextRule);
            return true;
        }

        return false;
    }

    private long cumulativeDelaySeconds(final List<EscalationRule> rules, final int upToLevelIndex) {
        long totalMinutes = 0;
        for (int i = 0; i <= upToLevelIndex; i++) {
            totalMinutes += rules.get(i).getDelayMinutes();
        }
        return totalMinutes * 60;
    }

    private void dispatchAndAdvance(final PendingAlert alert, final EscalationRule rule) {
        transactionTemplate.executeWithoutResult(status -> {
            final List<String> phoneNumbers = resolvePhoneNumbers(rule.getNotifyRoles());
            escalationDispatcher.dispatch(alert, rule, phoneNumbers);

            alert.setCurrentLevel(alert.getCurrentLevel() + 1);
            pendingAlertRepository.save(alert);
            log.info("Alert id={} escalated to level={}, dispatched channels={}",
                    alert.getId(), alert.getCurrentLevel(), rule.getChannels());
        });
    }

    private void markEscalated(final PendingAlert alert) {
        transactionTemplate.executeWithoutResult(status -> {
            alert.setStatus(PendingAlertStatus.ESCALATED);
            pendingAlertRepository.save(alert);
            log.info("Alert id={} marked as ESCALATED (all levels exhausted)", alert.getId());
        });
    }

    private List<String> resolvePhoneNumbers(final List<String> roleNames) {
        if (roleNames == null || roleNames.isEmpty()) {
            return List.of();
        }

        return userRepository.findAll().stream()
                .filter(user -> user.getRole() != null && roleNames.contains(user.getRole().getName()))
                .filter(user -> user.getPhone() != null && !user.getPhone().isBlank())
                .map(User::getPhone)
                .distinct()
                .toList();
    }
}