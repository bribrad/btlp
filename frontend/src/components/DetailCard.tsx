import { cn } from '@/lib/utils'

export function DetailCard({
  title,
  children,
  className,
}: {
  title: string
  children: React.ReactNode
  className?: string
}) {
  return (
    <section className={cn('rounded-lg border bg-card', className)}>
      <h2 className="border-b px-4 py-3 text-sm font-medium">{title}</h2>
      <div className="p-4">{children}</div>
    </section>
  )
}

export function DetailList({ children }: { children: React.ReactNode }) {
  return <dl className="grid gap-x-6 gap-y-4 sm:grid-cols-2">{children}</dl>
}

export function DetailField({
  label,
  children,
  wide,
}: {
  label: string
  children: React.ReactNode
  wide?: boolean
}) {
  return (
    <div className={cn('space-y-1', wide && 'sm:col-span-2')}>
      <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
        {label}
      </dt>
      <dd className="text-sm">{children}</dd>
    </div>
  )
}
