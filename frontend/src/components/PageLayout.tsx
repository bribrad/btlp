import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '@/features/auth/useAuth'
import {
  Truck,
  Package,
  Briefcase,
  LayoutDashboard,
  Clock,
  LogOut,
} from 'lucide-react'
import { cn } from '@/lib/utils'

const navItems = [
  { to: '/loads', label: 'Loads', icon: Package },
  { to: '/jobs', label: 'Jobs', icon: Briefcase },
  { to: '/dispatch', label: 'Dispatch Board', icon: LayoutDashboard },
  { to: '/timeline', label: 'Activity', icon: Clock },
]

export function PageLayout() {
  const { credentials, logout } = useAuth()

  return (
    <div className="flex h-screen bg-background">
      {/* Sidebar */}
      <aside className="flex w-56 flex-col border-r bg-card">
        <div className="flex h-14 items-center gap-2 border-b px-4">
          <div className="flex items-center justify-center w-7 h-7 rounded-lg bg-primary text-primary-foreground">
            <Truck className="w-4 h-4" />
          </div>
          <span className="font-semibold text-sm">BTLP Ops</span>
        </div>

        <nav className="flex-1 space-y-0.5 p-2" aria-label="Main navigation">
          {navItems.map(({ to, label, icon: Icon }) => (
            <NavLink
              key={to}
              to={to}
              className={({ isActive }) =>
                cn(
                  'flex items-center gap-3 rounded-md px-3 py-2 text-sm transition-colors',
                  isActive
                    ? 'bg-primary/10 text-primary font-medium'
                    : 'text-muted-foreground hover:bg-accent hover:text-accent-foreground',
                )
              }
            >
              <Icon className="w-4 h-4 shrink-0" />
              {label}
            </NavLink>
          ))}
        </nav>

        <div className="border-t p-3">
          <div className="flex items-center gap-2 rounded-md px-2 py-1.5">
            <div className="flex-1 min-w-0">
              <p className="truncate text-xs font-medium">{credentials?.username}</p>
              <p className="truncate text-xs text-muted-foreground capitalize">
                {credentials?.role.replace('ROLE_', '').toLowerCase()}
              </p>
            </div>
            <button
              onClick={logout}
              aria-label="Sign out"
              className="shrink-0 rounded p-1 text-muted-foreground hover:bg-accent hover:text-accent-foreground transition-colors"
            >
              <LogOut className="w-4 h-4" />
            </button>
          </div>
        </div>
      </aside>

      {/* Main content */}
      <main className="flex-1 overflow-auto">
        <Outlet />
      </main>
    </div>
  )
}
