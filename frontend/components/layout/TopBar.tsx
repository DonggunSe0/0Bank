'use client'

import { useState } from 'react'
import { Menu } from 'lucide-react'
import { Sheet, SheetContent, SheetTitle } from '@/components/ui/sheet'
import { Sidebar } from './Sidebar'

export function TopBar() {
  const [open, setOpen] = useState(false)

  return (
    <>
      <header className="md:hidden fixed top-0 left-0 right-0 h-14 bg-sidebar flex items-center px-4 z-40 border-b border-sidebar-border">
        <button
          onClick={() => setOpen(true)}
          className="text-sidebar-foreground"
          aria-label="메뉴 열기"
        >
          <Menu className="size-5" />
        </button>
        <div className="flex-1 flex items-center justify-center">
          <span className="font-semibold text-sidebar-foreground text-base">청년은행</span>
        </div>
      </header>

      <Sheet open={open} onOpenChange={setOpen}>
        <SheetContent side="left" className="p-0 w-60">
          <SheetTitle className="sr-only">메뉴</SheetTitle>
          <Sidebar onClose={() => setOpen(false)} />
        </SheetContent>
      </Sheet>
    </>
  )
}
