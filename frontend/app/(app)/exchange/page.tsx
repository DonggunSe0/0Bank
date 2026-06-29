'use client'

import { useEffect, useRef, useState } from 'react'
import {
  ArrowRightLeft,
  ChevronLeft,
  ChevronRight,
  Clock3,
  Eye,
  Plus,
  ReceiptText,
  Trash2,
  WalletCards,
} from 'lucide-react'
import { toast } from 'sonner'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { getAccounts } from '@/lib/api/accounts'
import {
  closeFxWallet,
  createExchangeOrder,
  createFxWallet,
  createExchangeQuote,
  getExchangeCurrencies,
  getExchangeOrder,
  getExchangeOrders,
  getExchangeRates,
  getFxWallets,
  type ExchangeCurrency,
  type ExchangeCurrencyCode,
  type ExchangeOrder,
  type ExchangeQuote,
  type ExchangeRate,
  type FxWallet,
} from '@/lib/api/exchanges'
import { ApiRequestError } from '@/lib/api'
import { formatCurrency, formatDateTime } from '@/lib/format'
import { createIdempotencyKey } from '@/lib/idempotency'
import type { Account, PageResponse } from '@/lib/types'

const emptyExchangeOrderPage: PageResponse<ExchangeOrder> = {
  content: [],
  totalPages: 0,
  totalElements: 0,
  number: 0,
  size: 0,
}

export default function ExchangePage() {
  const [accounts, setAccounts] = useState<Account[]>([])
  const [currencies, setCurrencies] = useState<ExchangeCurrency[]>([])
  const [rates, setRates] = useState<ExchangeRate[]>([])
  const [wallets, setWallets] = useState<FxWallet[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    loadExchangeData()
  }, [])

  async function loadExchangeData() {
    setLoading(true)
    setError('')
    try {
      const [accountData, currencyData, rateData, walletData] = await Promise.all([
        getAccounts(),
        getExchangeCurrencies(),
        getExchangeRates(),
        getFxWallets(),
      ])

      setAccounts(accountData.filter((account) => account.status === 'ACTIVE'))
      setCurrencies(currencyData)
      setRates(rateData)
      setWallets(walletData)
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : '환전 정보를 불러오지 못했습니다.')
    } finally {
      setLoading(false)
    }
  }

  async function refreshAccountsAndWallets() {
    const [accountData, walletData] = await Promise.all([getAccounts(), getFxWallets()])
    setAccounts(accountData.filter((account) => account.status === 'ACTIVE'))
    setWallets(walletData)
  }

  function handleWalletCreated(wallet: FxWallet) {
    setWallets((prev) => [wallet, ...prev.filter((item) => item.walletId !== wallet.walletId)])
  }

  function handleWalletClosed(wallet: FxWallet) {
    setWallets((prev) =>
      prev.map((item) => (item.walletId === wallet.walletId ? wallet : item)),
    )
  }

  return (
    <div className="flex flex-col gap-5 max-w-3xl">
      <div>
        <h1 className="text-xl font-bold text-foreground">환전</h1>
        <p className="text-sm text-muted-foreground mt-0.5">
          환율을 확인하고 외화 지갑으로 환전하세요.
        </p>
      </div>

      <Tabs defaultValue="exchange">
        <TabsList className="w-full">
          <TabsTrigger value="exchange" className="flex-1">
            <ArrowRightLeft className="size-4 mr-1.5" />
            환전하기
          </TabsTrigger>
          <TabsTrigger value="wallets" className="flex-1">
            <WalletCards className="size-4 mr-1.5" />
            외화지갑
          </TabsTrigger>
          <TabsTrigger value="orders" className="flex-1">
            <ReceiptText className="size-4 mr-1.5" />
            환전내역
          </TabsTrigger>
        </TabsList>

        <TabsContent value="exchange" className="mt-4">
          <ExchangeFormTab
            accounts={accounts}
            currencies={currencies}
            rates={rates}
            wallets={wallets}
            loading={loading}
            initialError={error}
            onBalancesChanged={refreshAccountsAndWallets}
          />
        </TabsContent>

        <TabsContent value="wallets" className="mt-4">
          <FxWalletTab
            currencies={currencies}
            wallets={wallets}
            loading={loading}
            initialError={error}
            onWalletCreated={handleWalletCreated}
            onWalletClosed={handleWalletClosed}
          />
        </TabsContent>

        <TabsContent value="orders" className="mt-4">
          <ExchangeOrdersTab wallets={wallets} />
        </TabsContent>
      </Tabs>
    </div>
  )
}

function ExchangeFormTab({
  accounts,
  currencies,
  rates,
  wallets,
  loading,
  initialError,
  onBalancesChanged,
}: {
  accounts: Account[]
  currencies: ExchangeCurrency[]
  rates: ExchangeRate[]
  wallets: FxWallet[]
  loading: boolean
  initialError: string
  onBalancesChanged: () => Promise<void>
}) {
  const [amount, setAmount] = useState('')
  const [fromCurrencyCode, setFromCurrencyCode] = useState<ExchangeCurrencyCode>('KRW')
  const [toCurrencyCode, setToCurrencyCode] = useState<ExchangeCurrencyCode | ''>('')
  const [krwAccountId, setKrwAccountId] = useState('')
  const [fxWalletId, setFxWalletId] = useState('')
  const [quote, setQuote] = useState<ExchangeQuote | null>(null)
  const [order, setOrder] = useState<ExchangeOrder | null>(null)
  const [quoting, setQuoting] = useState(false)
  const [ordering, setOrdering] = useState(false)
  const [error, setError] = useState(initialError)
  const idempotencyKeyRef = useRef<string | null>(null)
  const idempotencyRequestSignatureRef = useRef<string | null>(null)

  useEffect(() => {
    setError(initialError)
  }, [initialError])

  function resetExchangeAttempt() {
    setQuote(null)
    setOrder(null)
    idempotencyKeyRef.current = null
    idempotencyRequestSignatureRef.current = null
  }

  async function handleCreateQuote() {
    const parsedAmount = Number(amount)
    if (!fromCurrencyCode || !toCurrencyCode || !krwAccountId || !fxWalletId || !Number.isFinite(parsedAmount) || parsedAmount <= 0) {
      setError('환전 정보를 모두 입력해 주세요.')
      return
    }

    setQuoting(true)
    setError('')
    try {
      const nextQuote = await createExchangeQuote({
        fromCurrencyCode,
        toCurrencyCode,
        fromAmount: parsedAmount,
      })
      idempotencyKeyRef.current = null
      setOrder(null)
      setQuote(nextQuote)
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : '환전 견적 생성에 실패했습니다.')
    } finally {
      setQuoting(false)
    }
  }

  async function handleCreateOrder() {
    if (!quote || !krwAccountId || !fxWalletId) return

    const request = {
      exchangeQuoteId: quote.exchangeQuoteId,
      krwAccountId: Number(krwAccountId),
      fxWalletId: Number(fxWalletId),
    }
    const requestSignature = JSON.stringify(request)

    if (!idempotencyKeyRef.current || idempotencyRequestSignatureRef.current !== requestSignature) {
      idempotencyKeyRef.current = createIdempotencyKey('exchange-order')
      idempotencyRequestSignatureRef.current = requestSignature
    }

    setOrdering(true)
    setError('')
    try {
      const nextOrder = await createExchangeOrder(
        request,
        idempotencyKeyRef.current,
      )
      await onBalancesChanged()
      setOrder(nextOrder)
      idempotencyKeyRef.current = null
      idempotencyRequestSignatureRef.current = null
      toast.success('환전 주문이 완료되었습니다.')
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : '환전 주문 실행에 실패했습니다.')
    } finally {
      setOrdering(false)
    }
  }

  const targetCurrencies = currencies.filter(
    (currency) => currency.status === 'ACTIVE' && currency.currencyCode !== 'KRW',
  )
  const foreignCurrencyCode = fromCurrencyCode === 'KRW' ? toCurrencyCode : fromCurrencyCode
  const selectedRate = rates.find((rate) => rate.currencyCode === foreignCurrencyCode)
  const selectedAccount = accounts.find((account) => String(account.id) === krwAccountId)
  const selectedWallet = wallets.find((wallet) => String(wallet.walletId) === fxWalletId)
  const isKrwToForeign = fromCurrencyCode === 'KRW'
  const canCreateQuote = Boolean(
    fromCurrencyCode && toCurrencyCode && krwAccountId && fxWalletId && Number(amount) > 0 && !quoting,
  )
  const canCreateOrder = Boolean(quote && !order && !ordering)

  useEffect(() => {
    if (!krwAccountId && accounts[0]) setKrwAccountId(String(accounts[0].id))
  }, [accounts, krwAccountId])

  useEffect(() => {
    if (!toCurrencyCode && targetCurrencies[0]) {
      setToCurrencyCode(targetCurrencies[0].currencyCode)
    }
  }, [targetCurrencies, toCurrencyCode])

  useEffect(() => {
    if (!foreignCurrencyCode || foreignCurrencyCode === 'KRW') {
      if (fxWalletId) {
        setFxWalletId('')
        resetExchangeAttempt()
      }
      return
    }

    const selectedStillAvailable = wallets.some(
      (wallet) =>
        wallet.status === 'ACTIVE'
        && wallet.currencyCode === foreignCurrencyCode
        && String(wallet.walletId) === fxWalletId,
    )
    if (selectedStillAvailable) return

    const matchingWallet = wallets.find(
      (wallet) => wallet.status === 'ACTIVE' && wallet.currencyCode === foreignCurrencyCode,
    )
    setFxWalletId(matchingWallet ? String(matchingWallet.walletId) : '')
    resetExchangeAttempt()
  }, [foreignCurrencyCode, fxWalletId, wallets])

  if (loading) {
    return (
      <div className="grid gap-4 lg:grid-cols-[1fr_18rem]">
        <Skeleton className="h-80 w-full rounded-lg" />
        <Skeleton className="h-56 w-full rounded-lg" />
      </div>
    )
  }

  return (
    <div className="grid gap-4 lg:grid-cols-[1fr_18rem]">
      <Card className="border-border">
        <CardHeader className="pb-3">
          <CardTitle className="text-sm font-medium text-muted-foreground">환전 정보</CardTitle>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          {error && (
            <Alert variant="destructive">
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          )}

          <div className="grid grid-cols-2 gap-3">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="from-currency">출금 통화</Label>
              <Select
                value={fromCurrencyCode}
                onValueChange={(value) => {
                  const nextFromCurrencyCode = value as ExchangeCurrencyCode
                  setFromCurrencyCode(nextFromCurrencyCode)
                  setToCurrencyCode(
                    nextFromCurrencyCode === 'KRW'
                      ? targetCurrencies[0]?.currencyCode ?? ''
                      : 'KRW',
                  )
                  resetExchangeAttempt()
                }}
              >
                <SelectTrigger id="from-currency">
                  <span className="truncate">{fromCurrencyCode}</span>
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="KRW">KRW 원화</SelectItem>
                  {targetCurrencies.map((currency) => (
                    <SelectItem key={currency.currencyId} value={currency.currencyCode}>
                      {currency.currencyCode} {currency.currencyName}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="flex flex-col gap-1.5">
              <Label htmlFor="to-currency">입금 통화</Label>
              <Select
                value={toCurrencyCode}
                onValueChange={(value) => {
                  setToCurrencyCode(value as ExchangeCurrencyCode)
                  resetExchangeAttempt()
                }}
                disabled={fromCurrencyCode !== 'KRW'}
              >
                <SelectTrigger id="to-currency">
                  <span className="truncate">
                    {toCurrencyCode || '통화 선택'}
                  </span>
                </SelectTrigger>
                <SelectContent>
                  {fromCurrencyCode === 'KRW' ? (
                    targetCurrencies.map((currency) => (
                      <SelectItem key={currency.currencyId} value={currency.currencyCode}>
                        {currency.currencyCode} {currency.currencyName}
                      </SelectItem>
                    ))
                  ) : (
                    <SelectItem value="KRW">KRW 원화</SelectItem>
                  )}
                </SelectContent>
              </Select>
            </div>
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="exchange-amount">환전 금액</Label>
            <Input
              id="exchange-amount"
              type="number"
              inputMode="numeric"
              min={1}
              placeholder="0"
              value={amount}
              onChange={(event) => {
                setAmount(event.target.value)
                resetExchangeAttempt()
              }}
            />
            {selectedRate && (
              <p className="text-xs text-muted-foreground">
                기준 환율 {selectedRate.currencyUnit} {selectedRate.currencyCode} = {formatCurrency(selectedRate.basePrice)}
              </p>
            )}
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="krw-account">원화 계좌</Label>
              <Select
                value={krwAccountId}
                onValueChange={(value) => {
                  if (value == null) return
                  setKrwAccountId(value)
                  resetExchangeAttempt()
                }}
              >
                <SelectTrigger id="krw-account">
                  {selectedAccount ? (
                    <span className="grid w-full min-w-0 grid-cols-[6.5rem_minmax(0,1fr)] items-center gap-2">
                      <span className="min-w-0 truncate">{selectedAccount.nickname}</span>
                      <span className="min-w-0 truncate text-right tabular-nums text-muted-foreground">
                        {formatCurrency(selectedAccount.balance)}
                      </span>
                    </span>
                  ) : (
                    <span className="truncate">계좌 선택</span>
                  )}
                </SelectTrigger>
                <SelectContent className="w-80">
                  {accounts.map((account) => (
                    <SelectItem key={account.id} value={String(account.id)}>
                      <span className="grid w-full min-w-0 grid-cols-[minmax(0,1fr)_7rem] items-center gap-3">
                        <span className="min-w-0 truncate">{account.nickname}</span>
                        <span className="min-w-0 truncate text-right tabular-nums text-muted-foreground">
                          {formatCurrency(account.balance)}
                        </span>
                      </span>
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="flex flex-col gap-1.5">
              <Label htmlFor="fx-wallet">외화 지갑</Label>
              <div
                id="fx-wallet"
                className="flex h-9 w-full items-center justify-between gap-3 rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-xs"
              >
                {selectedWallet ? (
                  <>
                    <span className="min-w-0 truncate font-medium">{selectedWallet.currencyCode}</span>
                    <span className="min-w-0 truncate text-right tabular-nums text-muted-foreground">
                      {formatFxBalance(selectedWallet.balance)} {selectedWallet.currencyCode}
                    </span>
                  </>
                ) : (
                  <span className="truncate text-muted-foreground">활성 외화 지갑 없음</span>
                )}
              </div>
              {foreignCurrencyCode && !selectedWallet && (
                <p className="text-xs text-muted-foreground">
                  {foreignCurrencyCode} 외화 지갑이 없습니다. 외화지갑 탭에서 먼저 지갑을 만들어 주세요.
                </p>
              )}
            </div>
          </div>

          <Button type="button" className="w-full" disabled={!canCreateQuote} onClick={handleCreateQuote}>
            {quoting ? '견적 확인 중...' : '견적 확인'}
          </Button>
        </CardContent>
      </Card>

      <Card className="border-border">
        <CardHeader className="pb-3">
          <CardTitle className="text-sm font-medium text-muted-foreground">견적 요약</CardTitle>
        </CardHeader>
        <CardContent className="flex flex-col gap-3">
          <SummaryRow
            label={isKrwToForeign ? '출금 계좌' : '입금 계좌'}
            value={selectedAccount?.nickname ?? '-'}
          />
          <SummaryRow
            label={isKrwToForeign ? '입금 지갑' : '출금 지갑'}
            value={selectedWallet ? selectedWallet.currencyCode : '-'}
          />
          <SummaryRow
            label="적용 환율"
            value={quote ? `${formatNumber(quote.rate)}원` : '-'}
          />
          <SummaryRow
            label="예상 수수료"
            value={quote ? formatCurrency(quote.fee) : '-'}
          />
          <SummaryRow
            label="예상 입금액"
            value={quote ? formatExchangeAmount(quote.expectedToAmount, quote.toCurrencyCode) : '-'}
            strong
          />
          {order && (
            <>
              <SummaryRow label="주문 상태" value={exchangeOrderStatusLabel[order.status]} />
              <SummaryRow
                label="완료 금액"
                value={quote ? formatExchangeAmount(order.toAmount, quote.toCurrencyCode) : formatFxBalance(order.toAmount)}
                strong
              />
            </>
          )}
          <Button
            type="button"
            variant="outline"
            className="mt-1"
            disabled={!canCreateOrder}
            onClick={handleCreateOrder}
          >
            {ordering ? '환전 실행 중...' : order ? '환전 완료' : '환전 실행'}
          </Button>
        </CardContent>
      </Card>
    </div>
  )
}

function FxWalletTab({
  currencies,
  wallets,
  loading,
  initialError,
  onWalletCreated,
  onWalletClosed,
}: {
  currencies: ExchangeCurrency[]
  wallets: FxWallet[]
  loading: boolean
  initialError: string
  onWalletCreated: (wallet: FxWallet) => void
  onWalletClosed: (wallet: FxWallet) => void
}) {
  const [selectedCurrencyCode, setSelectedCurrencyCode] = useState<ExchangeCurrencyCode | ''>('')
  const [creating, setCreating] = useState(false)
  const [closingWalletId, setClosingWalletId] = useState<number | null>(null)
  const [error, setError] = useState(initialError)

  useEffect(() => {
    setError(initialError)
  }, [initialError])

  async function handleCreateWallet() {
    if (!selectedCurrencyCode) return

    setCreating(true)
    setError('')
    try {
      const wallet = await createFxWallet({ currencyCode: selectedCurrencyCode })
      onWalletCreated(wallet)
      setSelectedCurrencyCode('')
      toast.success(`${wallet.currencyCode} 지갑을 만들었습니다.`)
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : '외화 지갑 생성에 실패했습니다.')
    } finally {
      setCreating(false)
    }
  }

  async function handleCloseWallet(wallet: FxWallet) {
    const ok = window.confirm(`${wallet.currencyCode} 지갑을 해지할까요?`)
    if (!ok) return

    setClosingWalletId(wallet.walletId)
    setError('')
    try {
      const closedWallet = await closeFxWallet(wallet.walletId)
      onWalletClosed(closedWallet)
      toast.success(`${closedWallet.currencyCode} 지갑을 해지했습니다.`)
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : '외화 지갑 해지에 실패했습니다.')
    } finally {
      setClosingWalletId(null)
    }
  }

  const unavailableCurrencyCodes = new Set(
    wallets
      .filter((wallet) => wallet.status === 'ACTIVE' || wallet.status === 'SUSPENDED')
      .map((wallet) => wallet.currencyCode),
  )

  const creatableCurrencies = currencies.filter(
    (currency) =>
      currency.status === 'ACTIVE'
      && currency.currencyCode !== 'KRW'
      && !unavailableCurrencyCodes.has(currency.currencyCode),
  )

  if (loading) {
    return (
      <div className="flex flex-col gap-3">
        <Skeleton className="h-24 w-full rounded-lg" />
        <div className="grid gap-3 sm:grid-cols-2">
          <Skeleton className="h-32 w-full rounded-lg" />
          <Skeleton className="h-32 w-full rounded-lg" />
        </div>
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-3">
      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      <Card className="border-border">
        <CardHeader className="pb-3">
          <CardTitle className="text-sm font-medium text-muted-foreground">외화 지갑 만들기</CardTitle>
        </CardHeader>
        <CardContent className="grid gap-3 sm:grid-cols-[1fr_auto]">
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="wallet-currency">통화</Label>
            <Select
              value={selectedCurrencyCode}
              onValueChange={(value) => setSelectedCurrencyCode(value as ExchangeCurrencyCode)}
              disabled={creating || creatableCurrencies.length === 0}
            >
              <SelectTrigger id="wallet-currency">
                <SelectValue placeholder="통화 선택" />
              </SelectTrigger>
              <SelectContent>
                {creatableCurrencies.map((currency) => (
                  <SelectItem key={currency.currencyId} value={currency.currencyCode}>
                    {currency.currencyCode} {currency.currencyName}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <Button
            type="button"
            className="self-end"
            disabled={!selectedCurrencyCode || creating}
            onClick={handleCreateWallet}
          >
            <Plus data-icon="inline-start" />
            {creating ? '생성 중...' : '지갑 만들기'}
          </Button>
        </CardContent>
      </Card>

      {wallets.length === 0 ? (
        <Card className="border-border">
          <CardContent className="py-10 text-center text-sm text-muted-foreground">
            만든 외화 지갑이 없습니다.
          </CardContent>
        </Card>
      ) : (
        <div className="grid gap-3 sm:grid-cols-2">
          {wallets.map((wallet) => (
            <Card key={wallet.walletId} className="border-border">
              <CardContent className="pt-4 flex flex-col gap-3">
                <div className="flex items-center justify-between gap-3">
                  <div className="flex items-center gap-2">
                    <div className="size-9 rounded-md bg-primary/10 text-primary flex items-center justify-center">
                      <WalletCards className="size-5" />
                    </div>
                    <div>
                      <p className="text-sm font-semibold text-foreground">{wallet.currencyCode}</p>
                      <p className="text-xs text-muted-foreground">{fxWalletStatusLabel[wallet.status]}</p>
                    </div>
                  </div>
                  <p className="text-base font-bold text-foreground tabular-nums">
                    {formatFxBalance(wallet.balance)} {wallet.currencyCode}
                  </p>
                </div>

                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  disabled={wallet.status === 'CLOSED' || closingWalletId === wallet.walletId}
                  onClick={() => handleCloseWallet(wallet)}
                >
                  <Trash2 data-icon="inline-start" />
                  {closingWalletId === wallet.walletId ? '해지 중...' : '지갑 해지'}
                </Button>
              </CardContent>
            </Card>
          ))}
        </div>
      )}
    </div>
  )
}

function ExchangeOrdersTab({ wallets }: { wallets: FxWallet[] }) {
  const [orderPage, setOrderPage] = useState<PageResponse<ExchangeOrder>>(emptyExchangeOrderPage)
  const [selectedOrder, setSelectedOrder] = useState<ExchangeOrder | null>(null)
  const [loading, setLoading] = useState(true)
  const [detailLoading, setDetailLoading] = useState(false)
  const [currentPage, setCurrentPage] = useState(0)
  const [error, setError] = useState('')

  useEffect(() => {
    loadOrders()
  }, [currentPage])

  async function loadOrders() {
    setLoading(true)
    setError('')
    try {
      const nextPage = await getExchangeOrders(currentPage)
      setOrderPage(nextPage)
      setSelectedOrder(nextPage.content[0] ?? null)
    } catch (err) {
      setOrderPage(emptyExchangeOrderPage)
      setSelectedOrder(null)
      setError(err instanceof ApiRequestError ? err.message : '환전내역을 불러오지 못했습니다.')
    } finally {
      setLoading(false)
    }
  }

  async function handleSelectOrder(exchangeOrderId: number) {
    setDetailLoading(true)
    setError('')
    try {
      const order = await getExchangeOrder(exchangeOrderId)
      setSelectedOrder(order)
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : '환전 주문 상세를 불러오지 못했습니다.')
    } finally {
      setDetailLoading(false)
    }
  }

  if (loading) {
    return (
      <div className="grid gap-4 lg:grid-cols-[1fr_18rem]">
        <Card className="border-border">
          <CardContent className="pt-4 flex flex-col gap-3">
            <Skeleton className="h-16 w-full rounded-lg" />
            <Skeleton className="h-16 w-full rounded-lg" />
            <Skeleton className="h-16 w-full rounded-lg" />
          </CardContent>
        </Card>
        <Skeleton className="h-72 w-full rounded-lg" />
      </div>
    )
  }

  const orders = orderPage.content
  const hasPreviousPage = currentPage > 0
  const hasNextPage = currentPage + 1 < orderPage.totalPages

  return (
    <div className="flex flex-col gap-3">
      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      {orders.length === 0 ? (
        <Card className="border-border">
          <CardContent className="py-10 text-center text-sm text-muted-foreground">
            환전내역이 없습니다.
          </CardContent>
        </Card>
      ) : (
        <div className="grid gap-4 lg:grid-cols-[1fr_18rem]">
          <Card className="border-border">
            <CardHeader className="pb-3">
              <div className="flex items-center justify-between gap-3">
                <CardTitle className="text-sm font-medium text-muted-foreground">환전내역</CardTitle>
                <span className="text-xs text-muted-foreground">총 {orderPage.totalElements}건</span>
              </div>
            </CardHeader>
            <CardContent className="flex flex-col gap-0">
              {orders.map((order) => {
                const selected = selectedOrder?.exchangeOrderId === order.exchangeOrderId
                const { fromCurrencyCode, toCurrencyCode } = getOrderCurrencyCodes(order, wallets)
                return (
                  <div
                    key={order.exchangeOrderId}
                    className="flex items-center justify-between gap-3 py-3 border-b last:border-b-0 border-border"
                  >
                    <div className="flex items-center gap-3 min-w-0">
                      <div className="size-9 rounded-md bg-muted flex items-center justify-center shrink-0">
                        <Clock3 className="size-4 text-muted-foreground" />
                      </div>
                      <div className="min-w-0">
                        <p className="text-sm font-medium text-foreground truncate">
                          {exchangeDirectionLabel[order.direction]}
                        </p>
                        <p className="text-xs text-muted-foreground">
                          {formatDateTime(order.createdAt)}
                        </p>
                      </div>
                    </div>
                    <div className="flex items-center gap-3 shrink-0">
                      <div className="text-right">
                        <p className="text-sm font-semibold text-foreground">
                          {formatCurrencyAmount(order.toAmount, toCurrencyCode)}
                        </p>
                        <p className="text-xs text-muted-foreground">
                          {formatCurrencyAmount(order.fromAmount, fromCurrencyCode)} · {exchangeOrderStatusLabel[order.status]}
                        </p>
                      </div>
                      <Button
                        type="button"
                        variant={selected ? 'default' : 'outline'}
                        size="icon-sm"
                        disabled={detailLoading}
                        onClick={() => handleSelectOrder(order.exchangeOrderId)}
                        aria-label="환전 주문 상세 보기"
                      >
                        <Eye />
                      </Button>
                    </div>
                  </div>
                )
              })}
              <div className="flex items-center justify-between pt-3">
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  disabled={!hasPreviousPage || loading}
                  onClick={() => setCurrentPage((page) => Math.max(page - 1, 0))}
                >
                  <ChevronLeft data-icon="inline-start" />
                  이전
                </Button>
                <span className="text-xs text-muted-foreground">
                  {orderPage.totalPages === 0 ? 0 : currentPage + 1} / {orderPage.totalPages}
                </span>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  disabled={!hasNextPage || loading}
                  onClick={() => setCurrentPage((page) => page + 1)}
                >
                  다음
                  <ChevronRight data-icon="inline-end" />
                </Button>
              </div>
            </CardContent>
          </Card>

          <Card className="border-border">
            <CardHeader className="pb-3">
              <CardTitle className="text-sm font-medium text-muted-foreground">상세 정보</CardTitle>
            </CardHeader>
            <CardContent className="flex flex-col gap-3">
              {selectedOrder ? (
                <ExchangeOrderDetail order={selectedOrder} wallets={wallets} />
              ) : (
                <p className="text-sm text-muted-foreground">확인할 환전내역을 선택해 주세요.</p>
              )}
            </CardContent>
          </Card>
        </div>
      )}
    </div>
  )
}

function ExchangeOrderDetail({ order, wallets }: { order: ExchangeOrder; wallets: FxWallet[] }) {
  const { fromCurrencyCode, toCurrencyCode } = getOrderCurrencyCodes(order, wallets)

  return (
    <>
      <SummaryRow label="상태" value={exchangeOrderStatusLabel[order.status]} />
      <SummaryRow label="방향" value={exchangeDirectionLabel[order.direction]} />
      <SummaryRow
        label="출금 금액"
        value={formatCurrencyAmount(order.fromAmount, fromCurrencyCode)}
      />
      <SummaryRow
        label="입금 금액"
        value={formatCurrencyAmount(order.toAmount, toCurrencyCode)}
        strong
      />
      <SummaryRow
        label="적용 환율"
        value={`${formatNumber(order.appliedRate)}원`}
      />
      <SummaryRow label="수수료" value={formatCurrency(order.fee)} />
      <SummaryRow
        label="수수료율"
        value={`${formatNumber(order.feeRate * 100)}%`}
      />
      <SummaryRow label="주문 시각" value={formatDateTime(order.createdAt)} />
      <SummaryRow
        label="완료 시각"
        value={order.completedAt ? formatDateTime(order.completedAt) : '-'}
      />
    </>
  )
}

function SummaryRow({
  label,
  value,
  strong = false,
}: {
  label: string
  value: string
  strong?: boolean
}) {
  return (
    <div className="flex items-center justify-between gap-3">
      <span className="text-sm text-muted-foreground">{label}</span>
      <span className={`text-sm tabular-nums ${strong ? 'font-bold text-foreground' : 'font-medium text-foreground'}`}>
        {value}
      </span>
    </div>
  )
}

const fxWalletStatusLabel = {
  ACTIVE: '활성',
  SUSPENDED: '거래 제한',
  CLOSED: '해지',
}

const exchangeOrderStatusLabel = {
  REQUESTED: '요청',
  COMPLETED: '완료',
  FAILED: '실패',
  CANCELED: '취소',
}

const exchangeDirectionLabel = {
  KRW_TO_FOREIGN: '원화 → 외화',
  FOREIGN_TO_KRW: '외화 → 원화',
}

function formatFxBalance(value: number) {
  return new Intl.NumberFormat('ko-KR', {
    minimumFractionDigits: 0,
    maximumFractionDigits: 8,
  }).format(value)
}

function formatExchangeAmount(value: number, currencyCode: ExchangeCurrencyCode) {
  if (currencyCode === 'KRW') return formatCurrency(value)
  return `${formatFxBalance(value)} ${currencyCode}`
}

function formatCurrencyAmount(value: number, currencyCode?: ExchangeCurrencyCode) {
  if (currencyCode === 'KRW') return formatCurrency(value)
  return `${formatFxBalance(value)} ${currencyCode ?? '외화'}`
}

function getOrderCurrencyCodes(order: ExchangeOrder, wallets: FxWallet[]) {
  const foreignCurrencyCode = wallets.find(
    (wallet) => wallet.walletId === order.fxWalletId,
  )?.currencyCode

  if (order.direction === 'KRW_TO_FOREIGN') {
    return {
      fromCurrencyCode: 'KRW' as const,
      toCurrencyCode: foreignCurrencyCode,
    }
  }

  return {
    fromCurrencyCode: foreignCurrencyCode,
    toCurrencyCode: 'KRW' as const,
  }
}

function formatAccountOption(account: Account) {
  return `${account.nickname} ${formatCurrency(account.balance)}`
}

function formatNumber(value: number) {
  return new Intl.NumberFormat('ko-KR', {
    minimumFractionDigits: 0,
    maximumFractionDigits: 4,
  }).format(value)
}
