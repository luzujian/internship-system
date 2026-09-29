const escapeHtml = (value: string): string => value.replace(/[&<>"']/g, (character) => ({
  '&': '&amp;',
  '<': '&lt;',
  '>': '&gt;',
  '"': '&quot;',
  "'": '&#39;'
})[character] || character)

/** Render untrusted resource text with only a fixed highlight span as markup. */
export function highlightPlainText(text: string | null | undefined, keyword: string | null | undefined): string {
  const value = text ?? ''
  if (!keyword) return escapeHtml(value)

  const literalKeyword = keyword.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const matcher = new RegExp(literalKeyword, 'gi')
  let result = ''
  let offset = 0
  for (const match of value.matchAll(matcher)) {
    const start = match.index
    result += escapeHtml(value.slice(offset, start))
    result += `<span style="background-color: #ffeb3b;">${escapeHtml(match[0])}</span>`
    offset = start + match[0].length
  }
  return result + escapeHtml(value.slice(offset))
}
