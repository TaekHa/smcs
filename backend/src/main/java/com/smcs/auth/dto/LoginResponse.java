package com.smcs.auth.dto;

import com.smcs.user.dto.UserSummary;

public record LoginResponse(String token, long expiresInSeconds, UserSummary user) {
}
