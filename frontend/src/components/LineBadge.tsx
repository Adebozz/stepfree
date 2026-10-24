import { lineColour, textColourOn } from '../utils/lines'

interface LineBadgeProps {
  lineId: string | null
  name: string
  size?: 'sm' | 'md'
}

/** A line's name on its line colour. The name is always visible, so colour is never the only cue. */
export function LineBadge({ lineId, name, size = 'sm' }: LineBadgeProps) {
  const background = lineColour(lineId)
  return (
    <span
      className={`inline-flex max-w-full items-center truncate rounded-md font-semibold ${size === 'sm' ? 'px-2 py-0.5 text-xs' : 'px-2.5 py-1 text-sm'}`}
      style={{ backgroundColor: background, color: textColourOn(background) }}
    >
      {name}
    </span>
  )
}
