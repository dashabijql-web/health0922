export function normalizeMineEntryStatus(value) {
  return ['pass', 'fail'].includes(value) ? value : ''
}
