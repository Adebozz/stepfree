package com.stepfreelondon.notification.dto;

/**
 * @param emailAvailable whether this deployment can send alert emails
 */
public record NotificationCapabilitiesDto(boolean emailAvailable) {
}
