package com.purut.api.auth;

public record TokenPair(String accessToken, String refreshToken, long expiresIn) {
}
