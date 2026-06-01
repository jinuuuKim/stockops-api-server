package com.stockops.auth;

public record AuthResult(LoginResponse loginResponse, String refreshToken, long refreshExpiresIn) {
}
