package com.team10.backend.domain.exchange.service;

import com.team10.backend.domain.exchange.dto.res.FxWalletRes;
import com.team10.backend.domain.exchange.entity.Currency;
import com.team10.backend.domain.exchange.entity.FxWallet;
import com.team10.backend.domain.exchange.exception.ExchangeErrorCode;
import com.team10.backend.domain.exchange.repository.CurrencyRepository;
import com.team10.backend.domain.exchange.repository.FxWalletRepository;
import com.team10.backend.domain.exchange.type.CurrencyCode;
import com.team10.backend.domain.exchange.type.CurrencyStatus;
import com.team10.backend.domain.user.entity.User;
import com.team10.backend.domain.user.repository.UserRepository;
import com.team10.backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FxWalletService {

    private final FxWalletRepository fxWalletRepository;
    private final UserRepository userRepository;
    private final CurrencyRepository currencyRepository;

    // 외화 지갑 생성
    @Transactional
    public FxWalletRes createFxWallet(CurrencyCode currencyCode, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ExchangeErrorCode.USER_NOT_FOUND));
        // currency 조회
        Currency currency = currencyRepository.findByCurrencyCode(currencyCode)
                .orElseThrow(() -> new BusinessException(ExchangeErrorCode.CURRENCY_NOT_FOUND));

        if (currency.getStatus() != CurrencyStatus.ACTIVE) {
            throw new BusinessException(ExchangeErrorCode.CURRENCY_NOT_SUPPORTED);
        }

        // [동일 사용자 + 동일 통화] 지갑이 이미 있으면 상태에 따라 처리
        FxWallet existingWallet = fxWalletRepository.findByUserIdAndCurrencyCurrencyCode(userId, currencyCode)
                .orElse(null);
        if (existingWallet != null) {
            // 이미 활성상태인 지갑 존재하는 경우
            if (existingWallet.isActive()) {
                throw new BusinessException(ExchangeErrorCode.FX_WALLET_ALREADY_EXISTS);
            }
            // 존재하지만 활성상태가 아닌 경우 -> 활성상태로 변경
            existingWallet.activate();
            return FxWalletRes.from(existingWallet);
        }

        // FxWallet 생성 후 저장
        FxWallet fxWallet = fxWalletRepository.save(FxWallet.create(user, currency));
        return FxWalletRes.from(fxWallet);
    }

    // 내 외화 지갑 목록 조회
    @Transactional(readOnly = true)
    public List<FxWalletRes> getFxWallets(Long userId) {
        // 생성일 최신순
        List<FxWallet> fxWallets = fxWalletRepository.findAllByUserIdOrderByCreatedAtDesc(userId);

        return fxWallets.stream()
                .map(FxWalletRes::from)
                .toList();
    }

    // 외화 지갑 상세 조회
    @Transactional(readOnly = true)
    public FxWalletRes getFxWallet(Long fxWalletId, Long userId) {
        FxWallet fxWallet = fxWalletRepository.findByIdAndUserId(fxWalletId, userId)
                .orElseThrow(() -> new BusinessException(ExchangeErrorCode.FX_WALLET_NOT_FOUND));
        return FxWalletRes.from(fxWallet);
    }

    // 외화 지갑 해지/비활성화
    @Transactional
    public FxWalletRes closeFxWallet(Long fxWalletId, Long userId) {
        FxWallet fxWallet = fxWalletRepository.findByIdAndUserIdForUpdate(fxWalletId, userId)
                .orElseThrow(() -> new BusinessException(ExchangeErrorCode.FX_WALLET_NOT_FOUND));

        // 상태 검증
        if (!fxWallet.isActive()) {
            throw new BusinessException(ExchangeErrorCode.FX_WALLET_NOT_ACTIVE);
        }

        // 잔액 검증: 잔액 > 0 이면 해지 불가
        if (fxWallet.hasBalance()) {
            throw new BusinessException(ExchangeErrorCode.FX_WALLET_BALANCE_REMAINING);
        }

        fxWallet.close();
        return FxWalletRes.from(fxWallet);
    }

}
