import { ChevronRight } from 'lucide-react'
import { Fragment } from 'react'

import type { JourneyLeg } from '../api/types'
import { modeLabel } from '../utils/lines'
import { LineBadge } from './LineBadge'
import { ModeIcon } from './ModeIcon'

/** Compact sequence of legs: "Walk 5 min › Metropolitan › Walk 7 min". */
export function RouteChain({ legs }: { legs: JourneyLeg[] }) {
  return (
    <ol aria-label="Route" className="flex flex-wrap items-center gap-1.5">
      {legs.map((leg, index) => (
        <Fragment key={index}>
          {index > 0 && <ChevronRight aria-hidden="true" className="size-3.5 text-slate-400" />}
          <li className="inline-flex items-center gap-1 text-xs text-slate-700">
            {leg.mode === 'walking' ? (
              <>
                <ModeIcon mode="walking" className="size-3.5" />
                <span>Walk {leg.durationMinutes} min</span>
              </>
            ) : (
              <>
                <ModeIcon mode={leg.mode} className="size-3.5" />
                <LineBadge lineId={leg.lineId} name={leg.lineName ?? modeLabel(leg.mode)} />
              </>
            )}
          </li>
        </Fragment>
      ))}
    </ol>
  )
}
