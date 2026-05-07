export const REPORT_REASONS = {
  SPAM: 'Spam',
  INAPPROPRIATE: 'Contenu inapproprié',
  FAKE: 'Faux événement',
  OTHER: 'Autre',
} as const

export type ReportReason = keyof typeof REPORT_REASONS
