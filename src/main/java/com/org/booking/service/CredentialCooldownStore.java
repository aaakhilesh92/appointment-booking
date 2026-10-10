package com.org.booking.service;

import com.org.booking.config.VfsConfig;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CredentialCooldownStore {

    private static final Duration COOLDOWN_DURATION = Duration.ofHours(12);

    private final Map<String, CredentialState> stateByCredential = new ConcurrentHashMap<>();
    private final int maxConsecutiveFailures;

    public CredentialCooldownStore(VfsConfig vfsConfig) {
        int maxConsecutiveFailures = vfsConfig.getMaxConsecutiveFailures();
        if (maxConsecutiveFailures < 1) {
            throw new IllegalArgumentException("Maximum consecutive failures must be at least 1");
        }
        this.maxConsecutiveFailures = maxConsecutiveFailures;
    }

    public boolean isDisabled(String id) {
        Instant now = Instant.now();
        CredentialState state = stateByCredential.computeIfPresent(id, (key, current) -> {
            if (current.disabledUntil() == null) {
                return current;
            }
            if (now.isBefore(current.disabledUntil())) {
                return current;
            }
            return CredentialState.empty();
        });
        return state != null && state.disabledUntil() != null && now.isBefore(state.disabledUntil());
    }

    public FailureStatus recordFailure(String id) {
        Instant now = Instant.now();
        CredentialState state = stateByCredential.compute(id, (key, current) -> {
            CredentialState existing = current == null ? CredentialState.empty() : current;
            if (existing.disabledUntil() != null && now.isBefore(existing.disabledUntil())) {
                return existing;
            }

            int failures = existing.disabledUntil() == null ? existing.consecutiveFailures() : 0;
            int updatedFailures = failures + 1;
            Instant disabledUntil = updatedFailures >= maxConsecutiveFailures
                    ? now.plus(COOLDOWN_DURATION)
                    : null;
            return new CredentialState(updatedFailures, disabledUntil);
        });
        return new FailureStatus(state.consecutiveFailures(), state.disabledUntil());
    }

    public void recordSuccess(String id) {
        stateByCredential.remove(id);
    }

    public record FailureStatus(int consecutiveFailures, Instant disabledUntil) {
        public boolean isDisabled() {
            return disabledUntil != null;
        }
    }

    private record CredentialState(int consecutiveFailures, Instant disabledUntil) {
        private static CredentialState empty() {
            return new CredentialState(0, null);
        }
    }
}