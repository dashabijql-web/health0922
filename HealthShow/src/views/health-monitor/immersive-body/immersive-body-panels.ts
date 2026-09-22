export const fmtTime = (t: unknown) => !t ? '' : String(t).length > 16 ? String(t).substring(5, 16) : String(t)

export const calcAge = (birthDate: unknown) => {
  if (!birthDate) return '--'
  const age = new Date().getFullYear() - new Date(String(birthDate)).getFullYear()
  return age > 0 && age < 100 ? `${age}岁` : '--'
}

export function normalizeEmployeeWarnings(payload: unknown) {
  const data = payload as { records?: unknown[], list?: unknown[] } | unknown[] | null | undefined
  return Array.isArray(data) ? data : (data?.records || data?.list || [])
}

export async function loadEmployeeAiReport(
  empCode: string,
  generateEmployeeReport: (code: string) => Promise<{ code: number, data?: { report?: string } }>,
  renderMarkdown: (content: string) => Promise<string>
) {
  const empty = { content: '', html: '' }
  if (!empCode) return empty

  try {
    const res = await generateEmployeeReport(empCode)
    if (res.code !== 200) return empty
    const content = res.data?.report || ''
    const html = content ? await renderMarkdown(content) : ''
    return { content, html }
  } catch {
    return empty
  }
}

export function buildEmployeeProfilePrintHtml(empName: string, reportHtml: string) {
  return `<!DOCTYPE html><html lang="zh-CN"><head><meta charset="UTF-8">
<title>AI健康报告 - ${empName}</title>
<style>body{font-family:'Microsoft YaHei',sans-serif;max-width:800px;margin:0 auto;padding:24px;color:#1a1a2e}
h2{color:#0066cc;border-bottom:1px solid #dde;padding-bottom:6px;margin-top:24px}
table{border-collapse:collapse;width:100%;margin:12px 0}
th{background:#e8f0ff;padding:8px 12px;text-align:left}td{padding:7px 12px;border-bottom:1px solid #eee}
blockquote{border-left:4px solid #0066cc;margin:12px 0;padding:8px 16px;background:#f5f8ff}</style>
</head><body>${reportHtml}</body></html>`
}
