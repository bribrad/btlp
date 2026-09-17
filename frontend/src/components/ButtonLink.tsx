import { Link } from 'react-router-dom'
import { cn } from '@/lib/utils'

const VARIANTS = {
  primary:
    'bg-primary text-primary-foreground shadow hover:bg-primary/90',
  secondary: 'border bg-background shadow-sm hover:bg-accent',
}

interface ButtonLinkProps {
  to: string
  variant?: keyof typeof VARIANTS
  children: React.ReactNode
  className?: string
}

export function ButtonLink({ to, variant = 'primary', children, className }: ButtonLinkProps) {
  return (
    <Link
      to={to}
      className={cn(
        'inline-flex items-center gap-1.5 rounded-md px-3 py-1.5 text-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring',
        VARIANTS[variant],
        className,
      )}
    >
      {children}
    </Link>
  )
}
