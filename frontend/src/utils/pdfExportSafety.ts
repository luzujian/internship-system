/** Text and image URLs are untrusted when assembling the administrative PDF preview. */
export function escapePdfText(value: unknown): string {
  return String(value ?? '').replace(/[&<>"']/g, (character) => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;'
  })[character] || character)
}

export function safePdfImageSrc(value: unknown): string | null {
  if (typeof value !== 'string') return null
  const url = value.trim()
  if (/[<>"'`\u0000-\u001f\u007f]/.test(url)) return null
  if (url.startsWith('/api/')) return url
  if (/^data:image\/(?:png|jpeg|gif|webp);base64,[a-z0-9+/]+={0,2}$/i.test(url)) return url
  try {
    const parsed = new URL(url)
    if (parsed.protocol === 'https:' || parsed.protocol === 'http:') {
      return parsed.href
    }
  } catch {
    // Malformed and script/data URLs are not usable PDF images.
  }
  return null
}
