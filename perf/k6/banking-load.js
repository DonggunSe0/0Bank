import { options as smokeOptions } from './banking-smoke.js';
import runJourney from './banking-smoke.js';

// banking-smoke.js의 사용자 여정을 그대로 재사용하되,
// VU를 점진적으로 늘려 로컬 부하 테스트를 수행합니다.
export const options = {
  scenarios: {
    // 짧은 워밍업 구간입니다. JVM/JIT/커넥션 풀 초기 영향을 줄이는 용도입니다.
    warmup: {
      executor: 'constant-vus',
      vus: Number(__ENV.WARMUP_VUS || 2),
      duration: __ENV.WARMUP_DURATION || '30s',
      gracefulStop: '10s',
    },
    // 본 부하 구간입니다. TARGET_VUS까지 올린 뒤 유지하고 다시 내려옵니다.
    sustained_load: {
      executor: 'ramping-vus',
      startTime: __ENV.LOAD_START_TIME || '30s',
      stages: [
        // 점진적으로 목표 VU까지 증가합니다.
        { duration: __ENV.RAMP_UP || '1m', target: Number(__ENV.TARGET_VUS || 20) },
        // 목표 VU를 유지하면서 병목과 안정성을 봅니다.
        { duration: __ENV.HOLD || '3m', target: Number(__ENV.TARGET_VUS || 20) },
        // 부하를 천천히 줄여 종료 시점의 흔들림을 줄입니다.
        { duration: __ENV.RAMP_DOWN || '30s', target: 0 },
      ],
      gracefulRampDown: '30s',
    },
  },
  // smoke 테스트와 같은 성공 기준을 사용해서 부하 상황에서도 품질 기준을 맞춥니다.
  thresholds: smokeOptions.thresholds,
};

export default runJourney;
