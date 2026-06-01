/**
 * Client-side polling cadence for the two header inboxes — bell notifications
 * ({@link useNotifications}) and the "Demandes reçues" follow-request inbox
 * ({@link useMyFollowRequests}). They refresh in lockstep so their badges stay
 * consistent without a manual page reload.
 */
export const INBOX_POLL_INTERVAL_MS = 30_000
