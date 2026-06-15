package com.team10.backend.domain.user.service;

import com.team10.backend.domain.user.dto.req.UserProfileReq;
import com.team10.backend.domain.user.dto.res.UserProfileRes;
import com.team10.backend.domain.user.entity.User;
import com.team10.backend.domain.user.entity.UserProfile;
import com.team10.backend.domain.user.exception.UserErrorCode;
import com.team10.backend.domain.user.repository.UserProfileRepository;
import com.team10.backend.domain.user.repository.UserRepository;
import com.team10.backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserProfileService {

    private final UserProfileRepository userProfileRepository;
    private final UserRepository userRepository;

    @Transactional
    public UserProfileRes create(Long userId, UserProfileReq request) {
        if (userProfileRepository.existsByUserId(userId)) {
            throw new BusinessException(UserErrorCode.PROFILE_ALREADY_EXISTS);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        UserProfile profile = UserProfile.create(
                user,
                request.ageGroup(),
                request.region(),
                request.occupationStatus(),
                request.financialInterests()
        );

        return UserProfileRes.from(userId, userProfileRepository.save(profile));
    }

    public UserProfileRes get(Long userId) {
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.PROFILE_NOT_FOUND));
        return UserProfileRes.from(userId, profile);
    }

    @Transactional
    public UserProfileRes update(Long userId, UserProfileReq request) {
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.PROFILE_NOT_FOUND));

        profile.update(
                request.ageGroup(),
                request.region(),
                request.occupationStatus(),
                request.financialInterests()
        );

        return UserProfileRes.from(userId, profile);
    }
}
