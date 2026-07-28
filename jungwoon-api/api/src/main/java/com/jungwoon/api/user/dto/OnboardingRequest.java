package com.jungwoon.api.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 최초 프로필 설정.
 * 학년은 단어 DAY 가 열리는 기준이라 필수다.
 */
public record OnboardingRequest(
        @NotBlank @Size(max = 30) String name,
        @NotNull @Min(1) @Max(3) Integer grade,
        @Size(max = 50) String school
) {
}
