'use client'

import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'

export default function TransactionsPage() {
  return (
    <div className="flex flex-col gap-4">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">거래내역</h1>
        <p className="text-sm text-muted-foreground">
          계좌 상세 화면에서 계좌별 거래내역을 확인할 수 있습니다.
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>거래내역 안내</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-muted-foreground">
            전체 거래내역 화면은 준비 중입니다. 계좌 목록에서 계좌를 선택해 거래내역을 확인해 주세요.
          </p>
        </CardContent>
      </Card>
    </div>
  )
}
