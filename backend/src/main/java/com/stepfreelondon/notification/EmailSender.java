package com.stepfreelondon.notification;

/** Sends one plain-text email. An interface so tests can replace SMTP. */
public interface EmailSender {

    /** @throws RuntimeException if the message was not accepted; the delivery is then retried */
    void send(String to, String subject, String body);
}
