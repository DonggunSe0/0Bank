-- k6 청년정책 테스트용 실제 데이터 확인 쿼리입니다.
--
-- 이 파일은 가짜 young_policy 데이터를 생성하지 않습니다.
-- 청년정책 부하 테스트는 외부 청년정책 API로 적재된 실제 데이터를 사용합니다.
-- AWS/DB 담당자는 실제 정책 동기화가 끝난 뒤 이 파일을 실행합니다.
-- 정책 상세 조회 대상을 고정하고 싶으면, 아래 조회 결과의 id 값을
-- perf/k6/.env.local의 POLICY_IDS에 넣으면 됩니다.

SELECT
  COUNT(*) AS total_young_policy_count
FROM young_policy;

SELECT
  id,
  policy_id,
  title,
  category,
  sub_category,
  min_age,
  max_age,
  region_code
FROM young_policy
WHERE (min_age IS NULL OR min_age <= 25)
  AND (max_age IS NULL OR max_age >= 25)
  AND (
    region_code LIKE '%서울%'
    OR region_code LIKE '%11%'
    OR region_code LIKE '%전국%'
  )
  AND (
    category LIKE '%금융%'
    OR sub_category LIKE '%금융%'
    OR title LIKE '%대출%'
    OR description LIKE '%대출%'
  )
ORDER BY updated_at DESC
LIMIT 20;
