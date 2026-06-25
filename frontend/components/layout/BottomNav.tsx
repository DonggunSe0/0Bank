'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'
import {
  Building2,
  BarChart3,
  ClipboardList,
  CreditCard,
  LayoutDashboard,
  PiggyBank,
  Send,
  TrendingUp,
} from 'lucide-react'
import { cn } from '@/lib/utils'

const navItems = [
  { href: '/dashboard', label: '홈', icon: LayoutDashboard },
  { href: '/accounts', label: '계좌', icon: CreditCard },
  { href: '/transfer', label: '송금', icon: Send },
  { href: '/transactions', label: '거래', icon: ClipboardList },
  { href: '/savings', label: '예적금', icon: PiggyBank },
  { href: '/investment-accounts', label: '투자', icon: BarChart3 },
  { href: '/stocks', label: '주식', icon: TrendingUp },
]

export function BottomNav() {
  const pathname = usePathname()

  return (
    <nav
      className="fixed bottom-0 left-0 right-0 bg-card border-t border-border md:hidden z-40"
      aria-label="하단 네비게이션"
    >
      <ul className="flex">
        {navItems.map((item) => {
          const Icon = item.icon
          const active = pathname === item.href || pathname.startsWith(item.href + '/')
          return (
            <li key={item.href} className="flex-1">
              <Link
                href={item.href}
                className={cn(
                  'flex flex-col items-center justify-center py-2.5 gap-0.5 text-xs font-medium transition-colors',
                  active ? 'text-primary' : 'text-muted-foreground',
                )}
                aria-current={active ? 'page' : undefined}
              >
                <Icon className={cn('size-5', active ? 'text-primary' : 'text-muted-foreground')} />
                {item.label}
              </Link>
            </li>
          )
        })}
      </ul>
    </nav>
  )
}
