import type { AgeGroup, FinancialInterest, OccupationStatus, Region } from './types'

// 마이페이지 프로필과 회원가입 2단계(프로필 설정)에서 공통으로 쓰는 선택 옵션.
// 두 화면의 라벨이 어긋나지 않도록 한 곳에서만 정의한다.

export const ageGroupOptions: { value: AgeGroup; label: string }[] = [
  { value: 'TEENS', label: '10대' },
  { value: 'TWENTIES', label: '20대' },
  { value: 'THIRTIES', label: '30대' },
  { value: 'FORTIES', label: '40대' },
  { value: 'FIFTIES_PLUS', label: '50대 이상' },
]

export const occupationOptions: { value: OccupationStatus; label: string }[] = [
  { value: 'EMPLOYED', label: '직장인' },
  { value: 'SELF_EMPLOYED', label: '자영업' },
  { value: 'STUDENT', label: '학생' },
  { value: 'FREELANCER', label: '프리랜서' },
  { value: 'UNEMPLOYED', label: '무직' },
  { value: 'ETC', label: '기타' },
]

export const interestOptions: { value: FinancialInterest; label: string }[] = [
  { value: 'SAVINGS', label: '저축' },
  { value: 'INVESTMENT', label: '투자' },
  { value: 'LOAN', label: '대출' },
  { value: 'INSURANCE', label: '보험' },
  { value: 'PENSION', label: '연금' },
  { value: 'FOREIGN_EXCHANGE', label: '환전' },
]

export const regionOptions: { value: Region; label: string }[] = [
  { value: 'SEOUL', label: '서울특별시' },
  { value: 'BUSAN', label: '부산광역시' },
  { value: 'DAEGU', label: '대구광역시' },
  { value: 'INCHEON', label: '인천광역시' },
  { value: 'GWANGJU', label: '광주광역시' },
  { value: 'DAEJEON', label: '대전광역시' },
  { value: 'ULSAN', label: '울산광역시' },
  { value: 'SEJONG', label: '세종특별자치시' },
  { value: 'GYEONGGI', label: '경기도' },
  { value: 'GANGWON', label: '강원특별자치도' },
  { value: 'CHUNGBUK', label: '충청북도' },
  { value: 'CHUNGNAM', label: '충청남도' },
  { value: 'JEONBUK', label: '전북특별자치도' },
  { value: 'JEONNAM', label: '전라남도' },
  { value: 'GYEONGBUK', label: '경상북도' },
  { value: 'GYEONGNAM', label: '경상남도' },
  { value: 'JEJU', label: '제주특별자치도' },
]
