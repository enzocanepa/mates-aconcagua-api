package com.matesaconcahua.api.dto.auth;

public record AuthResponse(String token, UserDto user) {

    public record UserDto(String id, String email, String name, String role) {}
}
