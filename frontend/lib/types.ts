// ──────────────────────────────────────────────
// Auth
// ──────────────────────────────────────────────
export interface User {
  id: number
  email: string
  name: string
  phoneNumber?: string
  birthDate?: string
  identityVerified?: boolean
  createdAt?: string
}

export interface AuthResponse {
  accessToken: string
  user: User
}

// ──────────────────────────────────────────────
// Account
// ──────────────────────────────────────────────
export type AccountStatus = 'ACTIVE' | 'SUSPENDED' | 'CLOSED'
export type AccountType = 'DEPOSIT'

export interface Account {
  id: number
  accountNumber: string
  nickname: string
  accountType?: AccountType
  balance: number
  status: AccountStatus
  createdAt?: string
  updatedAt?: string
}

// ──────────────────────────────────────────────
// Transaction
// ──────────────────────────────────────────────
export type TransactionDirection = 'IN' | 'OUT'

export interface Transaction {
  id: number
  accountId: number
  amount: number
  direction: TransactionDirection
  type?: string
  counterpartyName?: string
  memo?: string
  balanceAfter?: number
  createdAt: string
}

export interface PageResponse<T> {
  content: T[]
  totalPages: number
  totalElements: number
  number: number
  size: number
}

export interface TransactionFilter {
  startDate?: string
  endDate?: string
  direction?: TransactionDirection
  minAmount?: number
  maxAmount?: number
  counterpartyName?: string
  page?: number
  sortDirection?: 'ASC' | 'DESC'
}

// ──────────────────────────────────────────────
// Transfer
// ──────────────────────────────────────────────
export interface DepositRequest {
  accountId: number | string
  amount: number
  memo?: string
}

export interface TransferRequest {
  senderAccountId: number | string
  receiverAccountNumber: string
  amount: number
  memo?: string
}

export interface TransferResult {
  success: boolean
  transactionId?: number
  transferId?: number
  status?: string
  accountId?: number
  senderAccountId?: number
  amount: number
  memo?: string
  senderAccountNumber?: string
  receiverAccountNumber?: string
  balanceBefore?: number
  createdAt?: string
  balanceAfter?: number
  senderBalanceAfter?: number
}

// ──────────────────────────────────────────────
// Identity Verification
// ──────────────────────────────────────────────
export type VerificationStatus =
  | 'OCR_PENDING'
  | 'OCR_COMPLETED'
  | 'GOVERNMENT_VERIFIED'
  | 'ONE_WON_PENDING'
  | 'COMPLETED'
  | 'FAILED'

export interface VerificationResponse {
  verificationId?: string | number
  status: VerificationStatus
  message?: string
  failureReason?: string
}

// ──────────────────────────────────────────────
// User Profile
// ──────────────────────────────────────────────
export type AgeGroup = 'TEENS' | 'TWENTIES' | 'THIRTIES' | 'FORTIES' | 'FIFTIES_PLUS'

export type OccupationStatus =
  | 'EMPLOYED'
  | 'SELF_EMPLOYED'
  | 'STUDENT'
  | 'FREELANCER'
  | 'UNEMPLOYED'
  | 'ETC'

export type FinancialInterest =
  | 'SAVINGS'
  | 'INVESTMENT'
  | 'LOAN'
  | 'INSURANCE'
  | 'PENSION'
  | 'FOREIGN_EXCHANGE'

export type Region =
  | 'SEOUL'
  | 'BUSAN'
  | 'DAEGU'
  | 'INCHEON'
  | 'GWANGJU'
  | 'DAEJEON'
  | 'ULSAN'
  | 'SEJONG'
  | 'GYEONGGI'
  | 'GANGWON'
  | 'CHUNGBUK'
  | 'CHUNGNAM'
  | 'JEONBUK'
  | 'JEONNAM'
  | 'GYEONGBUK'
  | 'GYEONGNAM'
  | 'JEJU'

export interface UserProfile {
  userId: number
  ageGroup: AgeGroup
  region: Region
  occupationStatus: OccupationStatus
  financialInterests: FinancialInterest[]
}

export interface UserProfileRequest {
  ageGroup: AgeGroup
  region: Region
  occupationStatus: OccupationStatus
  financialInterests: FinancialInterest[]
}

// ──────────────────────────────────────────────
// Savings Products
// ──────────────────────────────────────────────
export type SavingsType = 'DEPOSIT' | 'INSTALLMENT'

export interface SavingsProduct {
  id: number
  name: string
  bankName: string
  bankCode?: string
  type: SavingsType
  interestRate: number
  periodMonth: number
  minAmount: number
  maxAmount?: number
  monthlyLimit?: number
  terms?: string
}

// ──────────────────────────────────────────────
// Youth Policy
// ──────────────────────────────────────────────
export interface YouthPolicy {
  id: number
  policyId: string
  title: string
  description?: string
  category?: string
  subCategory?: string
  minAge?: number
  maxAge?: number
  regionCode?: string
  jobCode?: string
  applyPeriod?: string
  applyUrl?: string
  applyMethod?: string
}

// ──────────────────────────────────────────────
// Investment Account
// ──────────────────────────────────────────────
export type InvestmentAccountStatus = 'ACTIVE' | 'CLOSED'
export type CurrencyCode = 'KRW'

export interface InvestmentAccount {
  id: number
  accountNumber: string
  nickname: string | null
  cashBalance: number
  currencyCode: CurrencyCode
  status: InvestmentAccountStatus
  createdAt?: string
  updatedAt?: string
}

export interface InvestmentAccountOpenVerification {
  verificationKey: string
  expiresInSeconds: number
}

export interface InvestmentAccountUpdateResult {
  nickname: string | null
  updatedAt: string
}

export interface InvestmentAccountCloseResult {
  status: InvestmentAccountStatus
  updatedAt: string
}

// ──────────────────────────────────────────────
// API Error
// ──────────────────────────────────────────────
export interface ApiError {
  code: string
  message: string
  details?: { field: string; reason: string }[]
}
