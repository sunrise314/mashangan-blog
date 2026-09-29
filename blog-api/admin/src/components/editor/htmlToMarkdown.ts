import TurndownService from 'turndown'

const td = new TurndownService({
  headingStyle: 'atx',
  codeBlockStyle: 'fenced',
  bulletListMarker: '-',
  emDelimiter: '*',
})

td.addRule('codeblock', {
  filter: ['pre'],
  replacement: (_content: string, node: any) => {
    const code = node.querySelector('code')
    const text = code?.textContent || node.textContent || ''
    const langMatch = code?.className?.match(/language-([\w-]+)/)
    const lang = langMatch ? langMatch[1] : ''
    return `\n\n\`\`\`${lang}\n${text}\n\`\`\`\n\n`
  },
})

td.addRule('image', {
  filter: 'img',
  replacement: (_content: string, node: any) => {
    const src = node.getAttribute('src') || ''
    const alt = node.getAttribute('alt') || ''
    if (!src) return ''
    return `![${alt}](${src})`
  },
})

td.addRule('table', {
  filter: 'table',
  replacement: (content: string) => `\n\n${content}\n\n`,
})

export function htmlToMarkdown(html: string): string {
  return td.turndown(html).trim()
}
