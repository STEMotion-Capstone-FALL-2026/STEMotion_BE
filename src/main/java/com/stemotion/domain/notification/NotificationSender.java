package com.stemotion.domain.notification;

public interface NotificationSender {
    String send(String eventId, String recipient, String html);

    class Failure extends RuntimeException {
        final String code;
        final boolean retryable;
        final boolean uncertain;
        final long retryAfterSeconds;
        public Failure(String code, boolean retryable, boolean uncertain, long retryAfterSeconds) {
            super(code);
            this.code = code;
            this.retryable = retryable;
            this.uncertain = uncertain;
            this.retryAfterSeconds = retryAfterSeconds;
        }
    }
}
