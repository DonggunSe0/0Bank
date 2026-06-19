package com.team10.backend.domain.exAccount.service;

import com.team10.backend.domain.exAccount.dto.req.ExAccountLinkReq;
import com.team10.backend.domain.exAccount.dto.res.ExAccountCandidateRes;
import com.team10.backend.domain.exAccount.dto.res.ExAccountRes;
import com.team10.backend.domain.exAccount.entity.ExAccount;
import com.team10.backend.domain.exAccount.exception.ExAccountErrorCode;
import com.team10.backend.domain.exAccount.repository.ExAccountRepository;
import com.team10.backend.domain.user.entity.User;
import com.team10.backend.domain.user.exception.UserErrorCode;
import com.team10.backend.domain.user.repository.UserRepository;
import com.team10.backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

import static org.springframework.util.StringUtils.hasText;

@Service
@RequiredArgsConstructor
public class ExAccountSyncService {

    private final ExAccountRepository exAccountRepository;
    private final UserRepository userRepository;

    public List<ExAccountCandidateRes> getLinkCandidates(Long userId, List<ExAccountLinkReq> requests) {
        validateItems(requests);

        return requests.stream()
                .peek(this::validateItem)
                .map(request -> ExAccountCandidateRes.from(request, isAlreadyLinked(userId, request)))
                .toList();
    }

    public ExAccountRes linkAccount(Long userId, ExAccountLinkReq request) {
        validateItem(request);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        ExAccount account = upsertAccount(user, request);
        return ExAccountRes.from(account);
    }

    private void validateItems(List<ExAccountLinkReq> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new BusinessException(ExAccountErrorCode.EX_ACCOUNT_SYNC_ITEMS_REQUIRED);
        }
    }


    private void validateItem(ExAccountLinkReq request) {
        if (request == null
                || !hasText(request.organization())
                || !hasText(request.accountNumber())
                || !hasText(request.accountName())
                || request.assetType() == null) {
            throw new BusinessException(ExAccountErrorCode.EX_ACCOUNT_SYNC_REQUIRED_FIELD_MISSING);
        }
    }

    private boolean isAlreadyLinked(Long userId, ExAccountLinkReq request) {
        return exAccountRepository
                .findByUserIdAndOrganizationAndAccountNumber(
                        userId,
                        request.organization(),
                        request.accountNumber()
                )
                .isPresent();
    }

    private ExAccount upsertAccount(User user, ExAccountLinkReq request) {
        ExAccount exAccount = exAccountRepository
                .findByUserIdAndOrganizationAndAccountNumber(
                        user.getId(),
                        request.organization(),
                        request.accountNumber()
                )
                .orElse(null);

        if (exAccount == null) {
            return exAccountRepository.save(request.toEntity(user));
        }

        request.applyTo(exAccount);
        return exAccount;
    }
}
