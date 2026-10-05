/**
 * Shared View and Reminder constants for Wisp.
 * Reference: PRD.md Section 6.1.3 & ARCHITECTURE.md Section 6
 */

export const VIEW_MODES = {
  PRIORITY: 'priority',
  OLDEST: 'oldest',
  NEWEST: 'newest',
} as const;

export const DEFAULT_VIEW_MODE = VIEW_MODES.PRIORITY;

export const REMINDER_WINDOWS = {
  DAY_BEFORE_HOURS: 24,
} as const;

export const APP_CHANNELS = {
  ITEMS: 'items',
  SCREEN_TIME: 'screen_time_logs',
  USER_PREFERENCES: 'user_preferences',
} as const;
