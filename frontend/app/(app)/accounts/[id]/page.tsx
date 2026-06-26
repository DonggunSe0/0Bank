'use client'

import { useEffect, useState } from 'react'
import { useParams, useRouter } from 'next/navigation'
import { ArrowLeft, Edit2, KeyRound, Trash2 } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
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
import { formatCurrency, formatDate } from '@/lib/format'
import type { Account } from '@/lib/types'
import { ApiRequestError } from '@/lib/api'

const statusLabel: Record<string, string> = {
  ACTIVE: '정상',
  SUSPENDED: '정지',
  CLOSED: '해지',
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
  const [passwordOpen, setPasswordOpen] = useState(false)
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [newPasswordConfirm, setNewPasswordConfirm] = useState('')
  const [passwordLoading, setPasswordLoading] = useState(false)

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
    setCloseLoading(true)
    try {
      await closeAccount(account.id)
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
                <Badge variant="secondary" className="text-xs">
                  {statusLabel[account.status] ?? account.status}
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
                  {account.accountType === 'DEPOSIT' ? '입출금' : account.accountType}
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
                <AlertDialogTrigger>
                  <Button
                    variant="ghost"
                    className="w-full justify-start text-destructive hover:text-destructive hover:bg-destructive/10"
                  >
                    <Trash2 data-icon="inline-start" />
                    계좌 해지
                  </Button>
                </AlertDialogTrigger>
                <AlertDialogContent>
                  <AlertDialogHeader>
                    <AlertDialogTitle>계좌를 해지하시겠습니까?</AlertDialogTitle>
                    <AlertDialogDescription>
                      이 작업은 되돌릴 수 없습니다. 계좌를 해지하면 더 이상 사용할 수 없습니다.
                    </AlertDialogDescription>
                  </AlertDialogHeader>
                  <AlertDialogFooter>
                    <AlertDialogCancel>취소</AlertDialogCancel>
                    <AlertDialogAction
                      onClick={handleClose}
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
