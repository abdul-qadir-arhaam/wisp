/**
 * Shared Item definition for Wisp (Task / Note unified model).
 * Reference: PRD.md Section 6.1.1 & ARCHITECTURE.md Section 4
 */
export interface Item {
  id: string; // UUID primary key
  user_id: string; // UUID foreign key to auth.users
  content: string; // Cleaned thought/task text
  created_at: string; // ISO-8601 timestamp
  due_at: string | null; // Optional ISO-8601 timestamp parsed from natural language
  completed: boolean; // Completion status (default false)
  completed_at: string | null; // ISO-8601 timestamp when marked complete
  source_device: 'android' | 'windows' | string; // Device that captured the item
  reminder_sent_day_before: boolean; // Flag for ~24h reminder
  reminder_sent_overdue: boolean; // Flag for one-time overdue reminder
}

export interface ScreenTimeLog {
  id: string;
  user_id: string;
  device: 'android' | 'windows' | string;
  app_name: string;
  duration_seconds: number;
  date: string; // YYYY-MM-DD
  category?: string; // Optional post-MVP
}

export interface ReelsLog {
  id: string;
  user_id: string;
  date: string; // YYYY-MM-DD
  scroll_count: number;
  app_name: string;
}

export interface UserPreferences {
  user_id: string;
  selected_view: ViewMode;
}

export type ViewMode = 'priority' | 'oldest' | 'newest';
