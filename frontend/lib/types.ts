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
export interface TransferRequest {
  senderAccountId: number | string
  receiverAccountNumber: string
  amount: number
  accountPassword: string
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


export type SavingStatus = 'ACTIVE' | 'MATURED' | 'CANCELLED' | 'PAYMENT_FAILED'

export interface DepositSummary {
  depositId: number
  productName: string
  bankName: string
  principal: number
  status: SavingStatus
}

export interface DepositDetail extends DepositSummary {
  interestRate: number
  expectedInterest: number
  maturityDate: string
}

export interface InstallmentSummary {
  installmentId: number
  productName: string
  bankName: string
  paidAmount: number
  progressRate: number
  status: SavingStatus
}

export interface InstallmentDetail extends InstallmentSummary {
  monthlyAmount: number
  targetAmount: number
  maturityDate: string
}

export interface InterestPreview {
  savingId: number
  savingType: SavingsType
  principal: number
  interestRate: number
  expectedInterest: number
  expectedTotalAmount: number
}

export interface SavingOperationResult {
  savingId: number
  savingType: SavingsType
  principalAmount: number
  interestAmount: number
  refundAmount?: number
  payoutAmount?: number
  status: string
}

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

export interface InvestmentAccountUpdateResult {
  nickname: string | null
  updatedAt: string
}

export interface InvestmentAccountCloseResult {
  status: InvestmentAccountStatus
  updatedAt: string
}

// ──────────────────────────────────────────────
// Investment Stock
// ──────────────────────────────────────────────
export type StockMarket = 'KOSPI'
export type StockStatus = 'ACTIVE' | 'SUSPENDED' | 'DELISTED'
export type StockSortType = 'STOCK_NAME' | 'MARKET_CAP' | 'SALES_AMOUNT' | 'NET_INCOME'

export interface StockSummary {
  id: number
  stockCode: string
  stockName: string
  market: StockMarket
  status: StockStatus
  marketCap: number | null
  previousVolume: number | null
}

export interface StockDetail {
  id: number
  stockCode: string
  standardCode: string
  stockName: string
  market: StockMarket
  currencyCode: CurrencyCode
  status: StockStatus
  listedDate: string | null
  capitalAmount: number | null
  salesAmount: number | null
  netIncome: number | null
  marketCap: number | null
  previousVolume: number | null
  updatedAt: string | null
}

// ──────────────────────────────────────────────
// Investment Portfolio
// ──────────────────────────────────────────────
export interface InvestmentHolding {
  id: number
  stockId: number
  stockCode: string
  stockName: string
  market: StockMarket
  status: StockStatus
  quantity: number
  averagePrice: number
  marketCap: number | null
  previousVolume: number | null
}

// ──────────────────────────────────────────────
// Investment Trade
// ──────────────────────────────────────────────
export type InvestmentTradeType = 'BUY' | 'SELL'

export interface MarketOrderRequest {
  accountId: number
  stockId: number
  streamId: string
  tradeType: InvestmentTradeType
  quantity: number
  accountPassword: string
  expectedPrice: number
}

export interface InvestmentTradeResult {
  id: number
  accountId: number
  stockId: number
  stockCode: string
  stockName: string
  tradeType: InvestmentTradeType
  quantity: number
  executionPrice: number
  totalAmount: number
  requestedPrice: number
  priceDeviationBps: number
  snapshotAt: string
  executedAt: string
}

// ──────────────────────────────────────────────
// Realtime Orderbook
// ──────────────────────────────────────────────
export interface RealtimeOrderbookLevel {
  level: number
  price: number
  quantity: number
}

export interface RealtimeOrderbookSnapshot {
  stockCode: string
  businessTime: string
  timeType: string
  asks: RealtimeOrderbookLevel[]
  bids: RealtimeOrderbookLevel[]
  totalAskQuantity: number
  totalBidQuantity: number
}

export interface RealtimeOrderbookStreamCreated {
  streamId: string
  stockCode: string
}

// ──────────────────────────────────────────────
// API Error
// ──────────────────────────────────────────────
export interface ApiError {
  code: string
  message: string
  details?: { field: string; reason: string }[]
}
