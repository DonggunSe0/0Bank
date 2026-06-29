'use client'

import { useEffect, useState } from 'react'
import { useParams, useRouter } from 'next/navigation'
import {
  ArrowDownLeft,
  ArrowLeft,
  ArrowUpRight,
  ChevronLeft,
  ChevronRight,
  Edit2,
  KeyRound,
  Trash2,
} from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Separator } from '@/components/ui/separator'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from '@/components/ui/alert-dialog'
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog'
import { useAuth } from '@/contexts/AuthContext'
import {
  changeAccountPassword,
  closeAccount,
  getAccount,
  updateAccountNickname,
} from '@/lib/api/accounts'
import { getTransactions } from '@/lib/api/transactions'
import { getTransactionCategoryLabel, getTransactionDisplayName } from '@/lib/transaction-display'
import { formatCurrency, formatDate, formatDateTime } from '@/lib/format'
import type { Account, PageResponse, Transaction } from '@/lib/types'
import { ApiRequestError } from '@/lib/api'

const accountTypeLabel: Record<string, string> = {
  DEPOSIT: '입출금',
  SAVING_DEPOSIT: '예금',
  SAVING_INSTALLMENT: '적금',
}

function getAccountTypeLabel(accountType?: string) {
  if (!accountType) return '계좌'
  return accountTypeLabel[accountType] ?? accountType
}

const emptyTransactionPage: PageResponse<Transaction> = {
  content: [],
  totalPages: 0,
  totalElements: 0,
  number: 0,
  size: 0,
}

export default function AccountDetailPage() {
  const { id } = useParams<{ id: string }>()
  const router = useRouter()
  const { user } = useAuth()
  const [account, setAccount] = useState<Account | null>(null)
  const [loading, setLoading] = useState(true)
  const [editNickname, setEditNickname] = useState('')
  const [editOpen, setEditOpen] = useState(false)
  const [editLoading, setEditLoading] = useState(false)
  const [closeLoading, setCloseLoading] = useState(false)
  const [closeAccountPassword, setCloseAccountPassword] = useState('')
  const [passwordOpen, setPasswordOpen] = useState(false)
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [newPasswordConfirm, setNewPasswordConfirm] = useState('')
  const [passwordLoading, setPasswordLoading] = useState(false)
  const [transactionPage, setTransactionPage] = useState<PageResponse<Transaction>>(emptyTransactionPage)
  const [transactionsLoading, setTransactionsLoading] = useState(true)
  const [transactionsError, setTransactionsError] = useState('')
  const [currentPage, setCurrentPage] = useState(0)

  useEffect(() => {
    if (!user) return
    async function load() {
      try {
        const acc = await getAccount(id)
        setAccount(acc)
        setEditNickname(acc.nickname)
      } catch {
        setAccount(null)
      } finally {
        setLoading(false)
      }
    }
    load()
  }, [id, user])

  useEffect(() => {
    setCurrentPage(0)
    setTransactionPage(emptyTransactionPage)
    setTransactionsError('')
  }, [id])

  useEffect(() => {
    if (!user || !id) return
    setTransactionsLoading(true)
    setTransactionsError('')
    getTransactions(id, { page: currentPage })
      .then(setTransactionPage)
      .catch(() => {
        setTransactionPage(emptyTransactionPage)
        setTransactionsError('거래내역을 불러오지 못했습니다.')
      })
      .finally(() => setTransactionsLoading(false))
  }, [id, user, currentPage])

  async function handleNicknameUpdate() {
    if (!user || !account) return
    if (!editNickname.trim()) {
      toast.error('별칭을 입력해 주세요.')
      return
    }
    setEditLoading(true)
    try {
      const updated = await updateAccountNickname(account.id, editNickname.trim())
      setAccount(updated)
      setEditOpen(false)
      toast.success('별칭이 변경되었습니다.')
    } catch (err) {
      toast.error(err instanceof ApiRequestError ? err.message : '오류가 발생했습니다.')
    } finally {
      setEditLoading(false)
    }
  }


  async function handlePasswordChange() {
    if (!user || !account) return
    if (
      !/^\d{6}$/.test(currentPassword)
      || !/^\d{6}$/.test(newPassword)
      || !/^\d{6}$/.test(newPasswordConfirm)
    ) {
      toast.error('계좌 비밀번호는 숫자 6자리여야 합니다.')
      return
    }
    if (newPassword !== newPasswordConfirm) {
      toast.error('새 비밀번호가 일치하지 않습니다.')
      return
    }
    setPasswordLoading(true)
    try {
      await changeAccountPassword(account.id, currentPassword, newPassword)
      setPasswordOpen(false)
      setCurrentPassword('')
      setNewPassword('')
      setNewPasswordConfirm('')
      toast.success('계좌 비밀번호가 변경되었습니다.')
    } catch (err) {
      toast.error(err instanceof ApiRequestError ? err.message : '오류가 발생했습니다.')
    } finally {
      setPasswordLoading(false)
    }
  }

  async function handleClose() {
    if (!user || !account) return
    if (!/^\d{6}$/.test(closeAccountPassword)) {
      toast.error('계좌 비밀번호 숫자 6자리를 입력해 주세요.')
      return
    }
    setCloseLoading(true)
    try {
      await closeAccount(account.id, closeAccountPassword)
      toast.success('계좌가 해지되었습니다.')
      router.push('/accounts')
    } catch (err) {
      toast.error(err instanceof ApiRequestError ? err.message : '오류가 발생했습니다.')
    } finally {
      setCloseLoading(false)
    }
  }

  function closePasswordDialog() {
    setPasswordOpen(false)
    setCurrentPassword('')
    setNewPassword('')
    setNewPasswordConfirm('')
  }

  return (
    <div className="flex flex-col gap-5">
      <div className="flex items-center gap-3">
        <Button variant="ghost" size="sm" onClick={() => router.back()} className="-ml-2">
          <ArrowLeft data-icon="inline-start" />
          뒤로
        </Button>
      </div>

      {loading ? (
        <div className="flex flex-col gap-4">
          <Skeleton className="h-36 w-full rounded-lg" />
          <Skeleton className="h-24 w-full rounded-lg" />
        </div>
      ) : !account ? (
        <p className="text-center text-muted-foreground py-12">계좌를 찾을 수 없습니다.</p>
      ) : (
        <>
          {/* Balance Card */}
          <Card className="border-border bg-primary text-primary-foreground">
            <CardContent className="pt-5 pb-5">
              <div className="flex items-start justify-between mb-4">
                <div>
                  <p className="text-sm font-medium text-primary-foreground/70">잔액</p>
                  <p className="text-3xl font-bold tabular-nums mt-1">
                    {formatCurrency(account.balance)}
                  </p>
                </div>
                <Badge variant="default" className="text-xs">
                  {getAccountTypeLabel(account.accountType)}
                </Badge>
              </div>
              <div>
                <p className="text-xs text-primary-foreground/60">계좌번호</p>
                <p className="text-sm font-mono tracking-wider mt-0.5">{account.accountNumber}</p>
              </div>
            </CardContent>
          </Card>

          {/* Info */}
          <Card className="border-border">
            <CardHeader className="pb-3">
              <CardTitle className="text-sm font-medium text-muted-foreground">계좌 정보</CardTitle>
            </CardHeader>
            <CardContent className="flex flex-col gap-3">
              <div className="flex justify-between items-center">
                <span className="text-sm text-muted-foreground">별칭</span>
                <span className="text-sm font-medium text-foreground">{account.nickname}</span>
              </div>
              <div className="flex justify-between items-center">
                <span className="text-sm text-muted-foreground">계좌 유형</span>
                <span className="text-sm font-medium text-foreground">
                  {getAccountTypeLabel(account.accountType)}
                </span>
              </div>
              <div className="flex justify-between items-center">
                <span className="text-sm text-muted-foreground">개설일</span>
                <span className="text-sm font-medium text-foreground">
                  {account.createdAt ? formatDate(account.createdAt) : '-'}
                </span>
              </div>
            </CardContent>
          </Card>

          {/* Transactions */}
          <section className="flex flex-col gap-3">
            <div className="flex items-center justify-between">
              <div>
                <h2 className="text-base font-semibold text-foreground">거래내역</h2>
                <p className="text-sm text-muted-foreground">총 {transactionPage.totalElements}건</p>
              </div>
              {transactionsError && <Badge variant="destructive" className="text-xs">오류</Badge>}
            </div>

            {transactionsError && (
              <Card className="border-destructive/30">
                <CardContent className="py-3 text-sm text-destructive">{transactionsError}</CardContent>
              </Card>
            )}

            <Card className="border-border">
              <CardContent className="pt-4 flex flex-col gap-0">
                {transactionsLoading ? (
                  <div className="flex flex-col gap-3">
                    {[1, 2, 3, 4].map((i) => (
                      <Skeleton key={i} className="h-14 w-full" />
                    ))}
                  </div>
                ) : transactionPage.content.length === 0 ? (
                  <div className="py-10 text-center">
                    <p className="text-sm text-muted-foreground">거래 내역이 없습니다.</p>
                  </div>
                ) : (
                  transactionPage.content.map((txn, i) => (
                    <div key={txn.id}>
                      {i > 0 && <Separator className="my-0" />}
                      <div className="flex items-center justify-between gap-3 py-3.5">
                        <div className="flex min-w-0 items-center gap-3">
                          <div
                            className={`size-9 rounded-full flex items-center justify-center shrink-0 ${
                              txn.direction === 'IN'
                                ? 'bg-green-50 text-green-700'
                                : 'bg-red-50 text-red-600'
                            }`}
                          >
                            {txn.direction === 'IN' ? (
                              <ArrowDownLeft className="size-4" />
                            ) : (
                              <ArrowUpRight className="size-4" />
                            )}
                          </div>
                          <div className="min-w-0">
                            <div className="flex items-center gap-1.5">
                              <p className="truncate text-sm font-medium text-foreground">
                                {getTransactionDisplayName(txn)}
                              </p>
                              <Badge variant={txn.direction === 'IN' ? 'default' : 'secondary'} className="h-4 shrink-0 text-xs">
                                {getTransactionCategoryLabel(txn)}
                              </Badge>
                            </div>
                            <p className="text-xs text-muted-foreground">{formatDateTime(txn.createdAt)}</p>
                            {txn.memo && (
                              <p className="truncate text-xs text-muted-foreground">{txn.memo}</p>
                            )}
                          </div>
                        </div>
                        <div className="shrink-0 text-right">
                          <p
                            className={`text-sm font-bold tabular-nums ${
                              txn.direction === 'IN' ? 'text-green-700' : 'text-foreground'
                            }`}
                          >
                            {txn.direction === 'IN' ? '+' : '-'}
                            {formatCurrency(txn.amount)}
                          </p>
                          {txn.balanceAfter != null && (
                            <p className="text-xs text-muted-foreground">
                              잔액 {formatCurrency(txn.balanceAfter)}
                            </p>
                          )}
                        </div>
                      </div>
                    </div>
                  ))
                )}
              </CardContent>
            </Card>

            {transactionPage.totalPages > 1 && (
              <div className="flex items-center justify-center gap-3">
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => setCurrentPage((p) => Math.max(0, p - 1))}
                  disabled={currentPage === 0}
                  aria-label="이전 페이지"
                >
                  <ChevronLeft className="size-4" />
                </Button>
                <span className="text-sm text-muted-foreground">
                  {currentPage + 1} / {transactionPage.totalPages}
                </span>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => setCurrentPage((p) => Math.min(transactionPage.totalPages - 1, p + 1))}
                  disabled={currentPage >= transactionPage.totalPages - 1}
                  aria-label="다음 페이지"
                >
                  <ChevronRight className="size-4" />
                </Button>
              </div>
            )}
          </section>

          {/* Actions */}
          <div className="flex flex-col gap-2">
            {/* Edit nickname */}
            <Dialog open={editOpen} onOpenChange={setEditOpen}>
              <DialogTrigger render={<Button variant="outline" className="w-full justify-start" />}>
                <Edit2 data-icon="inline-start" />
                별칭 수정
              </DialogTrigger>
              <DialogContent>
                <DialogHeader>
                  <DialogTitle>계좌 별칭 수정</DialogTitle>
                </DialogHeader>
                <div className="flex flex-col gap-1.5 py-2">
                  <Label htmlFor="nickname">새 별칭</Label>
                  <Input
                    id="nickname"
                    value={editNickname}
                    onChange={(e) => setEditNickname(e.target.value)}
                    placeholder="별칭 입력"
                    maxLength={20}
                  />
                </div>
                <DialogFooter>
                  <Button variant="outline" onClick={() => setEditOpen(false)}>
                    취소
                  </Button>
                  <Button onClick={handleNicknameUpdate} disabled={editLoading}>
                    {editLoading ? '처리 중...' : '저장'}
                  </Button>
                </DialogFooter>
              </DialogContent>
            </Dialog>


            {/* Account password */}
            {account.status === 'ACTIVE' && (
              <Dialog open={passwordOpen} onOpenChange={(open) => (open ? setPasswordOpen(true) : closePasswordDialog())}>
                <DialogTrigger render={<Button variant="outline" className="w-full justify-start" />}>
                  <KeyRound data-icon="inline-start" />
                  계좌 비밀번호 변경
                </DialogTrigger>
                <DialogContent>
                  <DialogHeader>
                    <DialogTitle>계좌 비밀번호 변경</DialogTitle>
                  </DialogHeader>
                  <div className="flex flex-col gap-3 py-2">
                    <div className="flex flex-col gap-1.5">
                      <Label htmlFor="currentPassword">현재 비밀번호</Label>
                      <Input
                        id="currentPassword"
                        type="password"
                        inputMode="numeric"
                        value={currentPassword}
                        onChange={(e) => setCurrentPassword(e.target.value.replace(/[^0-9]/g, '').slice(0, 6))}
                        placeholder="현재 비밀번호 입력"
                        maxLength={6}
                      />
                    </div>
                    <div className="flex flex-col gap-1.5">
                      <Label htmlFor="newPassword">새 비밀번호</Label>
                      <Input
                        id="newPassword"
                        type="password"
                        inputMode="numeric"
                        value={newPassword}
                        onChange={(e) => setNewPassword(e.target.value.replace(/[^0-9]/g, '').slice(0, 6))}
                        placeholder="숫자 6자리"
                        maxLength={6}
                      />
                    </div>
                    <div className="flex flex-col gap-1.5">
                      <Label htmlFor="newPasswordConfirm">새 비밀번호 확인</Label>
                      <Input
                        id="newPasswordConfirm"
                        type="password"
                        inputMode="numeric"
                        value={newPasswordConfirm}
                        onChange={(e) => setNewPasswordConfirm(e.target.value.replace(/[^0-9]/g, '').slice(0, 6))}
                        placeholder="새 비밀번호 한 번 더 입력"
                        maxLength={6}
                      />
                    </div>
                  </div>
                  <DialogFooter>
                    <Button variant="outline" onClick={closePasswordDialog}>취소</Button>
                    <Button onClick={handlePasswordChange} disabled={passwordLoading}>
                      변경
                    </Button>
                  </DialogFooter>
                </DialogContent>
              </Dialog>
            )}

            {/* Close account */}
            {account.status === 'ACTIVE' && (
              <AlertDialog>
                <AlertDialogTrigger
                  render={
                    <Button
                      variant="ghost"
                      className="w-full justify-start text-destructive hover:text-destructive hover:bg-destructive/10"
                    />
                  }
                >
                  <Trash2 data-icon="inline-start" />
                  계좌 해지
                </AlertDialogTrigger>
                <AlertDialogContent>
                  <AlertDialogHeader>
                    <AlertDialogTitle>계좌를 해지하시겠습니까?</AlertDialogTitle>
                    <AlertDialogDescription>
                      이 작업은 되돌릴 수 없습니다. 계좌를 해지하면 더 이상 사용할 수 없습니다.
                    </AlertDialogDescription>
                  </AlertDialogHeader>
                  <div className="flex flex-col gap-1.5">
                    <Label htmlFor="closeAccountPassword">계좌 비밀번호</Label>
                    <Input
                      id="closeAccountPassword"
                      type="password"
                      inputMode="numeric"
                      value={closeAccountPassword}
                      onChange={(e) => setCloseAccountPassword(e.target.value.replace(/[^0-9]/g, '').slice(0, 6))}
                      placeholder="숫자 6자리"
                      maxLength={6}
                    />
                  </div>
                  <AlertDialogFooter>
                    <AlertDialogCancel onClick={() => setCloseAccountPassword('')}>취소</AlertDialogCancel>
                    <AlertDialogAction
                      onClick={(event) => {
                        event.preventDefault()
                        handleClose()
                      }}
                      disabled={closeLoading}
                      className="bg-destructive hover:bg-destructive/90"
                    >
                      {closeLoading ? '처리 중...' : '해지하기'}
                    </AlertDialogAction>
                  </AlertDialogFooter>
                </AlertDialogContent>
              </AlertDialog>
            )}
          </div>
        </>
      )}
    </div>
  )
}
