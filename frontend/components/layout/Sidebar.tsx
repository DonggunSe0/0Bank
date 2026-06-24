'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'
import {
  BarChart3,
  Building2,
  ClipboardList,
  CreditCard,
  LayoutDashboard,
  LogOut,
  PiggyBank,
  Send,
  ShieldCheck,
  User,
  X,
} from 'lucide-react'
import { cn } from '@/lib/utils'
import { useAuth } from '@/contexts/AuthContext'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import { Button } from '@/components/ui/button'

interface NavItem {
  href: string
  label: string
  icon: React.ElementType
}

const navItems: NavItem[] = [
  { href: '/dashboard', label: '대시보드', icon: LayoutDashboard },
  { href: '/identity', label: '본인인증', icon: ShieldCheck },
  { href: '/accounts', label: '계좌', icon: CreditCard },
  { href: '/transfer', label: '송금', icon: Send },
  { href: '/transactions', label: '거래내역', icon: ClipboardList },
  { href: '/savings', label: '예적금', icon: PiggyBank },
  { href: '/investment-accounts', label: '투자계좌', icon: BarChart3 },
  { href: '/youth-policies', label: '청년정책', icon: Building2 },
  { href: '/mypage', label: '마이페이지', icon: User },
]

interface SidebarProps {
  onClose?: () => void
}

export function Sidebar({ onClose }: SidebarProps) {
  const pathname = usePathname()
  const { user, logout } = useAuth()

  const initials = user?.name
    ? user.name.slice(0, 2)
    : user?.email?.slice(0, 2).toUpperCase() ?? 'YB'

  return (
    <aside className="flex flex-col h-full bg-sidebar text-sidebar-foreground w-60">
      {/* Header */}
      <div className="flex items-center justify-between px-5 py-4 border-b border-sidebar-border">
        <Link href="/dashboard" className="flex items-center gap-2.5" onClick={onClose}>
          <div className="size-8 rounded-md bg-sidebar-primary flex items-center justify-center">
            <span className="text-sidebar-primary-foreground font-bold text-sm">청</span>
          </div>
          <span className="font-semibold text-sidebar-foreground text-base">청년은행</span>
        </Link>
        {onClose && (
          <button
            onClick={onClose}
            className="text-sidebar-foreground/60 hover:text-sidebar-foreground md:hidden"
            aria-label="메뉴 닫기"
          >
            <X className="size-5" />
          </button>
        )}
      </div>

      {/* Nav */}
      <nav className="flex-1 overflow-y-auto px-3 py-4" aria-label="주 메뉴">
        <ul className="flex flex-col gap-0.5">
          {navItems.map((item) => {
            const Icon = item.icon
            const active = pathname === item.href || pathname.startsWith(item.href + '/')
            return (
              <li key={item.href}>
                <Link
                  href={item.href}
                  onClick={onClose}
                  className={cn(
                    'flex items-center gap-3 px-3 py-2.5 rounded-md text-sm font-medium transition-colors',
                    active
                      ? 'bg-sidebar-accent text-sidebar-accent-foreground'
                      : 'text-sidebar-foreground/70 hover:bg-sidebar-accent/60 hover:text-sidebar-foreground',
                  )}
                  aria-current={active ? 'page' : undefined}
                >
                  <Icon className="size-4 shrink-0" />
                  {item.label}
                </Link>
              </li>
            )
          })}
        </ul>
      </nav>

      {/* User / Logout */}
      <div className="px-4 py-4 border-t border-sidebar-border">
        <div className="flex items-center gap-3 mb-3">
          <Avatar className="size-8">
            <AvatarFallback className="bg-sidebar-primary text-sidebar-primary-foreground text-xs">
              {initials}
            </AvatarFallback>
          </Avatar>
          <div className="flex-1 min-w-0">
            <p className="text-sm font-medium text-sidebar-foreground truncate">
              {user?.name ?? '사용자'}
            </p>
            <p className="text-xs text-sidebar-foreground/50 truncate">{user?.email}</p>
          </div>
        </div>
        <Button
          variant="ghost"
          size="sm"
          className="w-full justify-start text-sidebar-foreground/70 hover:text-sidebar-foreground hover:bg-sidebar-accent/60"
          onClick={logout}
        >
          <LogOut data-icon="inline-start" />
          로그아웃
        </Button>
      </div>
    </aside>
  )
}
