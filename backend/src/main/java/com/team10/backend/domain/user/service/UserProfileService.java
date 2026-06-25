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
import org.springframework.dao.DataIntegrityViolationException;
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

        // FK 연결용으로만 쓰이므로 findById(풀 로딩) 대신 존재 확인 + getReferenceById(프록시)로 SELECT 한 번 절약
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
        }
        User user = userRepository.getReferenceById(userId);

        UserProfile profile = UserProfile.create(
                user,
                request.ageGroup(),
                request.region(),
                request.occupationStatus(),
                request.financialInterests()
        );

        try {
            return UserProfileRes.from(userId, userProfileRepository.save(profile));
        } catch (DataIntegrityViolationException e) {
            // 동시 요청으로 existsByUserId 체크를 통과한 경우 DB unique 제약조건에서 잡힘
            throw new BusinessException(UserErrorCode.PROFILE_ALREADY_EXISTS);
        }
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
