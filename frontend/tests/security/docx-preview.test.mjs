import test from 'node:test'
import assert from 'node:assert/strict'
import { fileURLToPath } from 'node:url'
import path from 'node:path'
import { mkdtemp, readFile, rm } from 'node:fs/promises'
import os from 'node:os'
import JSZip from 'jszip'
import { chromium } from '@playwright/test'
import { createServer } from 'vite'

async function fixture() {
  const zip = new JSZip()
  zip.file('[Content_Types].xml', `<?xml version="1.0"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Default Extension="html" ContentType="text/html"/><Default Extension="png" ContentType="image/png"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>`)
  zip.file('_rels/.rels', `<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rDoc" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>`)
  zip.file('word/document.xml', `<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture"><w:body><w:p><w:r><w:t>正常鉴定表正文</w:t></w:r></w:p><w:tbl><w:tr><w:tc><w:p><w:r><w:t>正常表格</w:t></w:r></w:p></w:tc></w:tr></w:tbl><w:p><w:r><w:drawing><wp:inline><wp:extent cx="9525" cy="9525"/><a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:pic><pic:blipFill><a:blip r:embed="rImage"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="9525" cy="9525"/></a:xfrm><a:prstGeom prst="rect"/></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p><w:altChunk r:id="rScript"/><w:altChunk r:id="rEvent"/><w:sectPr><w:pgSz w:w="11906" w:h="16838"/></w:sectPr></w:body></w:document>`)
  zip.file('word/_rels/document.xml.rels', `<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rScript" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/aFChunk" Target="script.html"/><Relationship Id="rEvent" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/aFChunk" Target="event.html"/><Relationship Id="rImage" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/pixel.png"/></Relationships>`)
  zip.file('word/script.html', '<script>parent.__docxScript = true</script>')
  zip.file('word/event.html', '<img src="data:image/png;base64,broken" onerror="parent.__docxEvent = true">')
  zip.file('word/media/pixel.png', 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aNJkAAAAASUVORK5CYII=', { base64: true })
  const documentXml = await zip.file('word/document.xml').async('string')
  zip.file('word/document.xml', documentXml.replace('<w:sectPr>', '<w:p><w:hyperlink r:id="rLink"><w:r><w:t>危险链接</w:t></w:r></w:hyperlink><w:hyperlink r:id="rEncoded"><w:r><w:t>编码链接</w:t></w:r></w:hyperlink><w:hyperlink r:id="rSafe"><w:r><w:t>正常链接</w:t></w:r></w:hyperlink></w:p><w:sectPr>'))
  const relationships = await zip.file('word/_rels/document.xml.rels').async('string')
  const linkType = 'http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink'
  zip.file('word/_rels/document.xml.rels', relationships.replace('</Relationships>', `<Relationship Id="rLink" Type="${linkType}" TargetMode="External" Target="javascript:window.__docxLink=true;void(0)"/><Relationship Id="rEncoded" Type="${linkType}" TargetMode="External" Target="java&#x09;script:window.__docxEncoded=true;void(0)"/><Relationship Id="rSafe" Type="${linkType}" TargetMode="External" Target="https://example.com/"/></Relationships>`))
  return zip.generateAsync({ type: 'base64' })
}

test('DOCX preview blocks active HTML and links while preserving normal document content', async () => {
  const root = fileURLToPath(new URL('../../', import.meta.url))
  const cacheDir = await mkdtemp(path.join(process.env.SECURITY_TEST_TEMP || os.tmpdir(), 'docx-vite-'))
  const server = await createServer({ configFile: false, root, cacheDir, logLevel: 'error',
    optimizeDeps: { noDiscovery: true, include: ['docx-preview', 'jszip', 'dompurify'] },
    server: { host: '127.0.0.1', port: 0 },
    plugins: [{ name: 'security-fixture',
      resolveId(id) { if (id === 'virtual:docx-library') return id },
      load(id) { if (id === 'virtual:docx-library') return "export { renderAsync } from 'docx-preview'" },
      configureServer(server) {
      server.middlewares.use('/security-fixture', (_req, res) => { res.setHeader('Content-Type', 'text/html'); res.end('<!doctype html><div id="preview"></div>') })
    } }]
  })
  let browser
  try {
    await server.listen()
    browser = await chromium.launch({ headless: true })
    const page = await browser.newPage()
    await page.goto(`http://127.0.0.1:${server.httpServer.address().port}/security-fixture`)
    const data = await fixture()
    // Positive reproduction control uses the installed library's original default.
    await page.evaluate(async base64 => {
      const { renderAsync } = await import('/@id/virtual:docx-library')
      await renderAsync(Uint8Array.from(atob(base64), c => c.charCodeAt(0)), document.querySelector('#preview'))
    }, data)
    await page.waitForFunction(() => window.__docxScript && window.__docxEvent)
    await page.getByText('危险链接', { exact: true }).click()
    await page.waitForFunction(() => window.__docxLink)
    await page.reload()
    await page.evaluate(async base64 => {
      const { renderSafeDocx } = await import('/src/utils/safeDocx.ts')
      await renderSafeDocx(Uint8Array.from(atob(base64), c => c.charCodeAt(0)), document.querySelector('#preview'), undefined, { renderAltChunks: true })
    }, data)
    assert.equal(await page.locator('#preview iframe').count(), 0)
    assert.equal(await page.evaluate(() => !!window.__docxScript || !!window.__docxEvent), false)
    await page.getByText('危险链接', { exact: true }).click()
    await page.getByText('编码链接', { exact: true }).click()
    assert.equal(await page.evaluate(() => !!window.__docxLink || !!window.__docxEncoded), false)
    assert.equal(await page.getByText('危险链接', { exact: true }).locator('..').getAttribute('href'), null)
    assert.equal(await page.locator('a').filter({ hasText: '正常链接' }).getAttribute('href'), 'https://example.com/')
    assert.match(await page.locator('#preview').innerText(), /正常鉴定表正文/)
    assert.match(await page.locator('#preview table').innerText(), /正常表格/)
    await page.waitForFunction(() => [...document.querySelectorAll('#preview img')].some(img => img.complete && img.naturalWidth > 0))
    for (const relative of ['src/views/teacher/InternshipAppraisalCollection.vue', 'src/components/FilePreviewDialog.vue']) {
      const source = await readFile(path.join(root, relative), 'utf8')
      assert.match(source, /await renderSafeDocx\(/)
      assert.doesNotMatch(source, /await renderAsync\(/)
    }
  } finally {
    await browser?.close()
    await server.close()
    await rm(cacheDir, { recursive: true, force: true })
  }
})
