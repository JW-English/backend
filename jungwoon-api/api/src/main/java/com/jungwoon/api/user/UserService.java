package com.jungwoon.api.user;

import com.jungwoon.api.auth.UserPrincipal;
import com.jungwoon.api.user.dto.OnboardingRequest;
import com.jungwoon.common.error.BusinessException;
import com.jungwoon.common.error.ErrorCode;
import com.jungwoon.domain.user.User;
import com.jungwoon.domain.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * 온보딩. 다시 호출하면 프로필 수정으로 동작한다 —
     * 학년은 진급하면 바뀌므로 한 번만 되게 막지 않는다.
     */
    @Transactional
    public User completeOnboarding(UserPrincipal me, OnboardingRequest request) {
        User user = userRepository.findById(me.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));

        user.completeOnboarding(request.name(), request.grade(), request.school());
        return user;
    }
}
