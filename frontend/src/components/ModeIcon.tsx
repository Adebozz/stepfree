import { Bus, CableCar, Footprints, Ship, TrainFront, TramFront, type LucideIcon } from 'lucide-react'

const ICONS: Record<string, LucideIcon> = {
  walking: Footprints,
  bus: Bus,
  'replacement-bus': Bus,
  coach: Bus,
  tram: TramFront,
  'river-bus': Ship,
  'cable-car': CableCar,
}

/** Decorative: always pair with a text label. */
export function ModeIcon({ mode, className = 'size-4' }: { mode: string | null; className?: string }) {
  const Icon = (mode && ICONS[mode]) || TrainFront
  return <Icon aria-hidden="true" className={className} />
}
