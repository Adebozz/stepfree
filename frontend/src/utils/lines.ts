/**
 * Line colours used as visual identifiers. Colour is never the only cue: every badge also shows the
 * line name. Values approximate TfL's published line colours.
 */
const LINE_COLOURS: Record<string, string> = {
  bakerloo: '#B36305',
  central: '#E32017',
  circle: '#FFD300',
  district: '#00782A',
  'hammersmith-city': '#F3A9BB',
  jubilee: '#A0A5A9',
  metropolitan: '#9B0056',
  northern: '#000000',
  piccadilly: '#003688',
  victoria: '#0098D4',
  'waterloo-city': '#95CDBA',
  elizabeth: '#6950A1',
  dlr: '#00A4A7',
  tram: '#5FB526',
  liberty: '#61686B',
  lioness: '#FAA61A',
  mildmay: '#0077AD',
  suffragette: '#18A95D',
  weaver: '#9B0058',
  windrush: '#DC241F',
}

const MODE_LABELS: Record<string, string> = {
  tube: 'Underground',
  'elizabeth-line': 'Elizabeth line',
  overground: 'Overground',
  dlr: 'DLR',
  tram: 'Tram',
  'national-rail': 'National Rail',
  bus: 'Bus',
  walking: 'Walk',
  'replacement-bus': 'Replacement bus',
  coach: 'Coach',
  'river-bus': 'River bus',
  'cable-car': 'Cable car',
}

export function lineColour(lineId: string | null | undefined): string {
  return (lineId && LINE_COLOURS[lineId]) || '#475569'
}

/** Black or white, whichever contrasts more with the background (WCAG relative luminance). */
export function textColourOn(hex: string): '#000000' | '#ffffff' {
  const channel = (offset: number) => {
    const value = parseInt(hex.slice(offset, offset + 2), 16) / 255
    return value <= 0.03928 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4
  }
  const luminance = 0.2126 * channel(1) + 0.7152 * channel(3) + 0.0722 * channel(5)
  return luminance > 0.179 ? '#000000' : '#ffffff'
}

export function modeLabel(mode: string | null | undefined): string {
  if (!mode) return 'Unknown'
  return MODE_LABELS[mode] ?? mode.replace(/-/g, ' ').replace(/^\w/, (c) => c.toUpperCase())
}
