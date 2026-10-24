package com.stepfreelondon.notification.dto;

import java.util.List;

public record AlertListDto(List<AlertDto> alerts, int unreadCount) {
}
