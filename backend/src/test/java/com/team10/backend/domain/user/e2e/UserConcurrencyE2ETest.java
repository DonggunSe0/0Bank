package com.team10.backend.domain.user.e2e;

import com.team10.backend.domain.user.client.PortOneClient;
import com.team10.backend.domain.user.client.PortOneIdentityVerification;
import com.team10.backend.domain.user.client.PortOneIdentityVerification.VerifiedCustomer;
import com.team10.backend.domain.user.dto.req.UserCreateReq;
import com.team10.backend.domain.user.exception.UserErrorCode;
import com.team10.backend.domain.user.ocr.OcrService;
import com.team10.backend.domain.user.repository.UserRepository;
import com.team10.backend.domain.user.service.UserService;
import com.team10.backend.domain.user.type.AgeGroup;
import com.team10.backend.domain.user.type.FinancialInterest;
import com.team10.backend.domain.user.type.OccupationStatus;
import com.team10.backend.domain.user.type.Region;
import com.team10.backend.domain.user.verification.OneWonVerificationService;
import com.team10.backend.global.exception.BusinessException;
import com.team10.backend.global.jwt.RefreshTokenService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * domain/user의 Redis 기반 분산락·원자 스크립트가 실제 동시 요청({@link ExecutorService} + 실제 Redis) 아래에서도
 * "단 하나만 성공"을 보장하는지 검증한다. 기존 단위 테스트는 Mockito mock 기반이라 락/스크립트 자체의
 * 동시성 동작(SETNX 원자성, Lua compare-and-delete 원자성, DB unique 제약과 TOCTOU race)은 검증하지 못했다.
 */
@SpringBootTest
@ActiveProfiles("test")
class UserConcurrencyE2ETest {

    private static final Long OCR_LOCK_TEST_USER_ID = 9_900_001L;
    private static final Long ONE_WON_LOCK_TEST_USER_ID = 9_900_002L;
    private static final Long REFRESH_TOKEN_TEST_USER_ID = 9_900_003L;

    @Autowired
    private OcrService ocrService;

    @Autowired
    private OneWonVerificationService oneWonVerificationService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private PortOneClient portOneClient;

    @BeforeEach
    void setUp() {
        cleanUpRedisFixtures();
    }

    @AfterEach
    void tearDown() {
        cleanUpRedisFixtures();
    }

    @Test
    @DisplayName("OCR 제출 락 — 동시에 20개 요청이 같은 사용자로 락을 시도해도 단 하나만 획득한다")
    void ocrLock_concurrentAcquire_onlyOneSucceeds() throws InterruptedException {
        int threadCount = 20;
        AtomicInteger acquired = new AtomicInteger();

        List<Throwable> failures = runConcurrently(threadCount, () -> {
            if (ocrService.tryAcquireLock(OCR_LOCK_TEST_USER_ID)) {
                acquired.incrementAndGet();
            }
        });

        assertNoConcurrentFailures(failures);
        assertEquals(1, acquired.get());
    }

    @Test
    @DisplayName("1원 인증 시작 락 — 동시에 20개 요청이 같은 사용자로 락을 시도해도 단 하나만 획득한다")
    void oneWonStartLock_concurrentAcquire_onlyOneSucceeds() throws InterruptedException {
        int threadCount = 20;
        AtomicInteger acquired = new AtomicInteger();

        List<Throwable> failures = runConcurrently(threadCount, () -> {
            if (oneWonVerificationService.tryAcquireStartLock(ONE_WON_LOCK_TEST_USER_ID)) {
                acquired.incrementAndGet();
            }
        });

        assertNoConcurrentFailures(failures);
        assertEquals(1, acquired.get());
    }

    @Test
    @DisplayName("리프레시 토큰 로테이션 — 동일한 토큰으로 동시에 요청해도 단 하나만 소비(consume)에 성공한다")
    void refreshTokenRotation_concurrentConsume_onlyOneSucceeds() throws InterruptedException {
        String token = refreshTokenService.issue(REFRESH_TOKEN_TEST_USER_ID);
        int threadCount = 20;
        AtomicInteger consumed = new AtomicInteger();

        List<Throwable> failures = runConcurrently(threadCount, () -> {
            if (refreshTokenService.validateAndConsume(REFRESH_TOKEN_TEST_USER_ID, token)) {
                consumed.incrementAndGet();
            }
        });

        assertNoConcurrentFailures(failures);
        assertEquals(1, consumed.get());
    }

    @Test
    @DisplayName("회원가입 중복 이메일 race — 같은 이메일로 동시에 가입해도 단 한 명만 성공하고 나머지는 DUPLICATE_EMAIL로 거부된다")
    void signup_concurrentSameEmail_onlyOneSucceeds() throws InterruptedException {
        String email = "race-" + UUID.randomUUID() + "@example.com";
        String name = "동시가입자";
        String phone = "01099998888";
        LocalDate birthDate = LocalDate.of(1995, 5, 5);

        when(portOneClient.getIdentityVerification(anyString()))
                .thenReturn(verifiedPortOne(name, birthDate.toString(), phone));

        int threadCount = 10;
        AtomicInteger successCount = new AtomicInteger();

        try {
            List<Throwable> failures = runConcurrently(threadCount, () -> {
                try {
                    userService.signup(signupReq(email, name, phone, birthDate));
                    successCount.incrementAndGet();
                } catch (BusinessException e) {
                    // 중복 이메일 거부는 기대된 결과 — 그 외 예외만 실패로 취급한다.
                    if (e.getErrorCode() != UserErrorCode.DUPLICATE_EMAIL) {
                        throw e;
                    }
                }
            });

            assertNoConcurrentFailures(failures);
            assertEquals(1, successCount.get());
            assertTrue(userRepository.existsByEmail(email));
        } finally {
            cleanUpSignupTestData(email);
        }
    }

    private void cleanUpRedisFixtures() {
        redisTemplate.delete("identity:ocr:lock:" + OCR_LOCK_TEST_USER_ID);
        redisTemplate.delete("identity:one-won:lock:" + ONE_WON_LOCK_TEST_USER_ID);
        redisTemplate.delete("refresh:" + REFRESH_TOKEN_TEST_USER_ID);
    }

    private void cleanUpSignupTestData(String email) {
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.createNativeQuery(
                            "DELETE FROM user_consents WHERE user_id IN (SELECT id FROM users WHERE email = :email)")
                    .setParameter("email", email)
                    .executeUpdate();
            // user_profile_interests는 user_profiles를 FK(profile_id)로 참조하는 @ElementCollection 테이블 —
            // user_profiles보다 먼저 지우지 않으면 FK 제약 위반(ConstraintViolationException)이 발생한다.
            entityManager.createNativeQuery(
                            "DELETE FROM user_profile_interests WHERE profile_id IN "
                                    + "(SELECT id FROM user_profiles WHERE user_id IN "
                                    + "(SELECT id FROM users WHERE email = :email))")
                    .setParameter("email", email)
                    .executeUpdate();
            entityManager.createNativeQuery(
                            "DELETE FROM user_profiles WHERE user_id IN (SELECT id FROM users WHERE email = :email)")
                    .setParameter("email", email)
                    .executeUpdate();
            entityManager.createNativeQuery("DELETE FROM users WHERE email = :email")
                    .setParameter("email", email)
                    .executeUpdate();
            entityManager.clear();
        });
    }

    private UserCreateReq signupReq(String email, String name, String phone, LocalDate birthDate) {
        return new UserCreateReq(
                "portone-id", email, "Password1!", name, phone, birthDate,
                AgeGroup.TWENTIES, Region.SEOUL, OccupationStatus.EMPLOYED, Set.of(FinancialInterest.SAVINGS),
                true, true, true, false);
    }

    private PortOneIdentityVerification verifiedPortOne(String name, String birthDate, String phone) {
        return new PortOneIdentityVerification("VERIFIED", new VerifiedCustomer(name, birthDate, phone));
    }

    private List<Throwable> runConcurrently(int threadCount, ThrowingRunnable task) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Throwable>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    task.run();
                    return null;
                } catch (Throwable throwable) {
                    return throwable;
                }
            }));
        }

        assertTrue(ready.await(5, TimeUnit.SECONDS));
        start.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));

        List<Throwable> failures = new ArrayList<>();
        for (Future<Throwable> future : futures) {
            try {
                Throwable failure = future.get();
                if (failure != null) {
                    failures.add(failure);
                }
            } catch (Exception e) {
                failures.add(e);
            }
        }
        return failures;
    }

    private void assertNoConcurrentFailures(List<Throwable> failures) {
        assertTrue(failures.isEmpty(), () -> failures.stream()
                .map(throwable -> throwable.getClass().getSimpleName() + ": " + throwable.getMessage())
                .toList()
                .toString());
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
