import axe from 'axe-core'

/**
 * Runs axe-core on rendered markup and returns readable violations. jsdom has no layout or colours,
 * so colour-contrast is checked in the browser audit instead (see docs/testing.md).
 */
export async function axeViolations(container: Element): Promise<string[]> {
  const results = await axe.run(container, { rules: { 'color-contrast': { enabled: false } } })
  return results.violations.map((v) => `${v.id}: ${v.help} (${v.nodes.map((n) => n.target.join(' ')).join(', ')})`)
}
