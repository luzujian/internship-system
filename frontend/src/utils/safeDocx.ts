import { renderAsync, type Options } from 'docx-preview'
import DOMPurify from 'dompurify'

/** Uploaded DOCX files must not introduce active HTML or executable links. */
export async function renderSafeDocx(
  data: Blob | ArrayBuffer | Uint8Array,
  body: HTMLElement,
  styles?: HTMLElement,
  options: Partial<Options> = {}
) {
  const staging = document.createElement('div')
  const stagingStyles = styles ? document.createElement('div') : undefined
  const result = await renderAsync(data, staging, stagingStyles, {
    ...options,
    renderAltChunks: false,
    // Keep embedded images visible without allowing arbitrary blob URLs through the sanitizer.
    useBase64URL: true
  })
  // Sanitize before attaching to the page; preserve document formatting and safe links.
  const sanitize = (element: HTMLElement) => DOMPurify.sanitize(element, {
    IN_PLACE: true,
    ADD_TAGS: ['style'],
    FORBID_TAGS: ['iframe', 'object', 'embed', 'form']
  })
  sanitize(staging)
  if (stagingStyles) {
    sanitize(stagingStyles)
    styles!.replaceChildren(...Array.from(stagingStyles.childNodes))
  }
  body.replaceChildren(...Array.from(staging.childNodes))
  return result
}
