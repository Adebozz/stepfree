import { describe, expect, it } from 'vitest'

import { lineColour, modeLabel, textColourOn } from './lines'
import { isCutOffByTfl } from './notes'
import { formatDuration, formatFare, formatTime, londonNowForInput } from './time'

describe('time formatting', () => {
  it('formats instants in London time across BST and GMT', () => {
    expect(formatTime('2026-10-23T16:24:00Z')).toBe('17:24')
    expect(formatTime('2026-12-01T09:05:00Z')).toBe('09:05')
  })

  it('never renders "Invalid Date"', () => {
    expect(formatTime(null)).toBe('—')
    expect(formatTime('garbage')).toBe('—')
  })

  it('formats durations and fares', () => {
    expect(formatDuration(34)).toBe('34 min')
    expect(formatDuration(60)).toBe('1 hr')
    expect(formatDuration(75)).toBe('1 hr 15 min')
    expect(formatFare(360)).toBe('£3.60')
    expect(formatFare(null)).toBeNull()
  })

  it('gives London wall-clock time for datetime inputs', () => {
    expect(londonNowForInput(new Date('2026-10-23T23:30:00Z'))).toBe('2026-10-24T00:30')
  })
})

describe('line helpers', () => {
  it('picks readable text colour for each line colour', () => {
    expect(textColourOn(lineColour('northern'))).toBe('#ffffff')
    expect(textColourOn(lineColour('circle'))).toBe('#000000')
    expect(textColourOn(lineColour('unknown-line'))).toBe('#ffffff')
  })

  it('labels modes', () => {
    expect(modeLabel('tube')).toBe('Underground')
    expect(modeLabel('elizabeth-line')).toBe('Elizabeth line')
    expect(modeLabel('some-new-mode')).toBe('Some new mode')
  })
})

describe('TfL notes', () => {
  it('detects notes cut off at the 255-character field limit', () => {
    expect(isCutOffByTfl(`${'x'.repeat(240)} continue to Ki`)).toBe(true)
    expect(isCutOffByTfl(`${'x'.repeat(254)}.`)).toBe(false)
    expect(isCutOffByTfl('Short note')).toBe(false)
  })
})
