'use client'

import { useCallback, useEffect, useState } from 'react'
import Link from 'next/link'
import { BarChart3, Search, Star, TrendingUp } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import {
  addWatchlist,
  getStocks,
  getWatchlists,
  removeWatchlist,
  searchStocks,
} from '@/lib/api/stocks'
import { ApiRequestError } from '@/lib/api'
import { formatNumber } from '@/lib/format'
import type { PageResponse, StockSummary } from '@/lib/types'

const SEARCH_DEBOUNCE_MS = 400

export default function StocksPage() {
  const [keyword, setKeyword] = useState('')
  const [debouncedKeyword, setDebouncedKeyword] = useState('')
  const [searchResults, setSearchResults] = useState<StockSummary[]>([])
  const [searchLoading, setSearchLoading] = useState(false)
  const [searchError, setSearchError] = useState('')

  const [rankingPage, setRankingPage] = useState<PageResponse<StockSummary> | null>(null)
  const [rankingLoading, setRankingLoading] = useState(true)
  const [rankingError, setRankingError] = useState('')
  const [rankingCurrentPage, setRankingCurrentPage] = useState(0)

  const [watchlists, setWatchlists] = useState<StockSummary[]>([])
  const [watchlistLoading, setWatchlistLoading] = useState(true)
  const [watchlistError, setWatchlistError] = useState('')

  useEffect(() => {
    const timer = window.setTimeout(() => setDebouncedKeyword(keyword.trim()), SEARCH_DEBOUNCE_MS)
    return () => window.clearTimeout(timer)
  }, [keyword])

  useEffect(() => {
    if (debouncedKeyword.length < 2) {
      setSearchResults([])
      setSearchError('')
      setSearchLoading(false)
      return
    }

    setSearchLoading(true)
    setSearchError('')
    searchStocks(debouncedKeyword)
      .then(setSearchResults)
      .catch(() => setSearchError('종목 검색에 실패했습니다.'))
      .finally(() => setSearchLoading(false))
  }, [debouncedKeyword])

  const loadRanking = useCallback((page: number) => {
    setRankingLoading(true)
    setRankingError('')
    getStocks({ page, size: 20, sort: 'MARKET_CAP', direction: 'DESC' })
      .then((response) => {
        setRankingPage(response)
        setRankingCurrentPage(page)
      })
      .catch(() => setRankingError('종목 목록을 불러오지 못했습니다.'))
      .finally(() => setRankingLoading(false))
  }, [])

  const loadWatchlists = useCallback(() => {
    setWatchlistLoading(true)
    setWatchlistError('')
    getWatchlists()
      .then(setWatchlists)
      .catch(() => setWatchlistError('관심 종목을 불러오지 못했습니다.'))
      .finally(() => setWatchlistLoading(false))
  }, [])

  useEffect(() => {
    loadRanking(0)
    loadWatchlists()
  }, [loadRanking, loadWatchlists])

  async function handleToggleWatchlist(stock: StockSummary, isWatchlisted: boolean) {
    try {
      if (isWatchlisted) {
        await removeWatchlist(stock.id)
        setWatchlists((prev) => prev.filter((item) => item.id !== stock.id))
      } else {
        const added = await addWatchlist(stock.id)
        setWatchlists((prev) => [...prev, added])
      }
    } catch (err) {
      const message = err instanceof ApiRequestError ? err.message : '관심 종목 처리에 실패했습니다.'
      setWatchlistError(message)
    }
  }

  const watchlistIds = new Set(watchlists.map((stock) => stock.id))

  return (
    <div className="flex flex-col gap-5">
      <div>
        <h1 className="text-xl font-bold text-foreground">주식</h1>
        <p className="text-sm text-muted-foreground mt-0.5">
          KOSPI 종목을 검색하고 실시간 호가로 모의 투자하세요.
        </p>
      </div>

      <Tabs defaultValue="search">
        <TabsList className="w-full">
          <TabsTrigger value="search" className="flex-1">
            <Search className="size-4 mr-1.5" />
            검색
          </TabsTrigger>
          <TabsTrigger value="ranking" className="flex-1">
            <TrendingUp className="size-4 mr-1.5" />
            시가총액
          </TabsTrigger>
          <TabsTrigger value="watchlist" className="flex-1">
            <Star className="size-4 mr-1.5" />
            관심
          </TabsTrigger>
        </TabsList>

        <TabsContent value="search" className="flex flex-col gap-4 mt-4">
          <Input
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            placeholder="종목명 또는 종목코드 (2글자 이상)"
            aria-label="종목 검색"
          />

          {searchError && (
            <Alert variant="destructive">
              <AlertDescription>{searchError}</AlertDescription>
            </Alert>
          )}

          {debouncedKeyword.length > 0 && debouncedKeyword.length < 2 && (
            <p className="text-sm text-muted-foreground text-center py-6">2글자 이상 입력해 주세요.</p>
          )}

          {searchLoading ? (
            <div className="flex flex-col gap-3">
              {[1, 2, 3].map((i) => (
                <Skeleton key={i} className="h-20 w-full rounded-lg" />
              ))}
            </div>
          ) : debouncedKeyword.length >= 2 && searchResults.length === 0 ? (
            <p className="text-sm text-muted-foreground text-center py-8">검색 결과가 없습니다.</p>
          ) : (
            <StockList
              stocks={searchResults}
              watchlistIds={watchlistIds}
              onToggleWatchlist={handleToggleWatchlist}
            />
          )}
        </TabsContent>

        <TabsContent value="ranking" className="flex flex-col gap-4 mt-4">
          {rankingError && (
            <Alert variant="destructive">
              <AlertDescription>{rankingError}</AlertDescription>
            </Alert>
          )}

          {rankingLoading ? (
            <div className="flex flex-col gap-3">
              {[1, 2, 3].map((i) => (
                <Skeleton key={i} className="h-20 w-full rounded-lg" />
              ))}
            </div>
          ) : (
            <>
              <StockList
                stocks={rankingPage?.content ?? []}
                watchlistIds={watchlistIds}
                onToggleWatchlist={handleToggleWatchlist}
                showMarketCap
              />
              {rankingPage && rankingPage.totalPages > 1 && (
                <div className="flex items-center justify-center gap-3 pt-2">
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={rankingCurrentPage <= 0}
                    onClick={() => loadRanking(rankingCurrentPage - 1)}
                  >
                    이전
                  </Button>
                  <span className="text-sm text-muted-foreground">
                    {rankingCurrentPage + 1} / {rankingPage.totalPages}
                  </span>
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={rankingCurrentPage >= rankingPage.totalPages - 1}
                    onClick={() => loadRanking(rankingCurrentPage + 1)}
                  >
                    다음
                  </Button>
                </div>
              )}
            </>
          )}
        </TabsContent>

        <TabsContent value="watchlist" className="flex flex-col gap-4 mt-4">
          {watchlistError && (
            <Alert variant="destructive">
              <AlertDescription>{watchlistError}</AlertDescription>
            </Alert>
          )}

          {watchlistLoading ? (
            <div className="flex flex-col gap-3">
              {[1, 2].map((i) => (
                <Skeleton key={i} className="h-20 w-full rounded-lg" />
              ))}
            </div>
          ) : watchlists.length === 0 ? (
            <Card className="border-border">
              <CardContent className="py-12 text-center">
                <Star className="size-10 text-muted-foreground mx-auto mb-3" />
                <p className="text-sm font-medium text-foreground mb-1">관심 종목이 없습니다</p>
                <p className="text-xs text-muted-foreground">종목 검색에서 별 아이콘을 눌러 등록하세요.</p>
              </CardContent>
            </Card>
          ) : (
            <StockList
              stocks={watchlists}
              watchlistIds={watchlistIds}
              onToggleWatchlist={handleToggleWatchlist}
              showMarketCap
            />
          )}
        </TabsContent>
      </Tabs>
    </div>
  )
}

function StockList({
  stocks,
  watchlistIds,
  onToggleWatchlist,
  showMarketCap = false,
}: {
  stocks: StockSummary[]
  watchlistIds: Set<number>
  onToggleWatchlist: (stock: StockSummary, isWatchlisted: boolean) => void
  showMarketCap?: boolean
}) {
  return (
    <div className="flex flex-col gap-3">
      {stocks.map((stock) => {
        const isWatchlisted = watchlistIds.has(stock.id)
        return (
          <Card key={stock.id} className="border-border">
            <CardContent className="py-4">
              <div className="flex items-center gap-3">
                <Link href={`/stocks/${stock.stockCode}`} className="flex-1 min-w-0">
                  <div className="flex items-center gap-3">
                    <div className="size-10 rounded-full bg-primary/10 flex items-center justify-center shrink-0">
                      <BarChart3 className="size-5 text-primary" />
                    </div>
                    <div className="min-w-0">
                      <div className="flex items-center gap-2">
                        <p className="text-sm font-semibold text-foreground truncate">{stock.stockName}</p>
                        <Badge variant="secondary" className="text-xs h-5 shrink-0">
                          {stock.stockCode}
                        </Badge>
                      </div>
                      {showMarketCap && stock.marketCap != null && (
                        <p className="text-xs text-muted-foreground mt-0.5">
                          시가총액 {formatNumber(stock.marketCap)}
                        </p>
                      )}
                    </div>
                  </div>
                </Link>
                <Button
                  variant="ghost"
                  size="icon-sm"
                  aria-label={isWatchlisted ? '관심 종목 해제' : '관심 종목 등록'}
                  onClick={() => onToggleWatchlist(stock, isWatchlisted)}
                >
                  <Star
                    className={`size-4 ${isWatchlisted ? 'fill-amber-400 text-amber-400' : 'text-muted-foreground'}`}
                  />
                </Button>
              </div>
            </CardContent>
          </Card>
        )
      })}
    </div>
  )
}
