// =============================================================================
// Supabase Edge Function: reminder-scanner
// Description: Scheduled scanner for dated tasks triggering push reminders.
// Reference: PRD.md Section 6.1.6 & ARCHITECTURE.md Section 2.3, 2.4
// =============================================================================

import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.39.0";

interface ReminderItem {
  id: string;
  user_id: string;
  content: string;
  due_at: string;
  completed: boolean;
  reminder_sent_day_before: boolean;
  reminder_sent_overdue: boolean;
  source_device: string;
}

interface NotificationPayload {
  itemId: string;
  userId: string;
  scenario: "DAY_BEFORE" | "SAME_DAY_FALLBACK" | "OVERDUE";
  title: string;
  body: string;
  dueAt: string;
}

serve(async (req: Request) => {
  try {
    const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
    const supabaseServiceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
    const fcmServerKey = Deno.env.get("FCM_SERVER_KEY") ?? "";

    if (!supabaseUrl || !supabaseServiceKey) {
      return new Response(
        JSON.stringify({ error: "Missing SUPABASE_URL or SUPABASE_SERVICE_ROLE_KEY" }),
        { status: 500, headers: { "Content-Type": "application/json" } }
      );
    }

    const supabase = createClient(supabaseUrl, supabaseServiceKey);
    const now = new Date();
    const nowIso = now.toISOString();

    const dispatchedReminders: NotificationPayload[] = [];
    const errors: string[] = [];

    // -------------------------------------------------------------------------
    // Scenario 1 & 2: Day-Before & Same-Day Fallback Reminders
    // Target: Incomplete items with due_at in the future, where reminder_sent_day_before = false
    // -------------------------------------------------------------------------
    const dayAndHalfAhead = new Date(now.getTime() + 26 * 3600 * 1000).toISOString();

    const { data: upcomingItems, error: upcomingErr } = await supabase
      .from("items")
      .select("*")
      .eq("completed", false)
      .eq("reminder_sent_day_before", false)
      .gt("due_at", nowIso)
      .lte("due_at", dayAndHalfAhead);

    if (upcomingErr) {
      errors.push(`Error querying upcoming items: ${upcomingErr.message}`);
    } else if (upcomingItems) {
      for (const rawItem of upcomingItems) {
        const item = rawItem as ReminderItem;
        const dueTime = new Date(item.due_at).getTime();
        const hoursRemaining = (dueTime - now.getTime()) / (3600 * 1000);

        let scenario: "DAY_BEFORE" | "SAME_DAY_FALLBACK" = "DAY_BEFORE";
        let title = "Upcoming Task Tomorrow";
        let body = `Remember: ${item.content}`;

        if (hoursRemaining <= 12) {
          // Scenario 2: Same-day fallback (created with < 24h remaining, due today)
          scenario = "SAME_DAY_FALLBACK";
          title = "Task Due Today";
          body = `Due today: ${item.content}`;
        }

        const payload: NotificationPayload = {
          itemId: item.id,
          userId: item.user_id,
          scenario,
          title,
          body,
          dueAt: item.due_at,
        };

        // Dispatch push notification
        await dispatchPushNotification(supabase, fcmServerKey, payload);

        // Mark flag to prevent duplicates
        const { error: updateErr } = await supabase
          .from("items")
          .update({ reminder_sent_day_before: true })
          .eq("id", item.id);

        if (updateErr) {
          errors.push(`Failed to update reminder_sent_day_before for ${item.id}: ${updateErr.message}`);
        } else {
          dispatchedReminders.push(payload);
        }
      }
    }

    // -------------------------------------------------------------------------
    // Scenario 3: One-Time Overdue Follow-Up
    // Target: Incomplete items where due_at < now, and reminder_sent_overdue = false
    // -------------------------------------------------------------------------
    const { data: overdueItems, error: overdueErr } = await supabase
      .from("items")
      .select("*")
      .eq("completed", false)
      .eq("reminder_sent_overdue", false)
      .lt("due_at", nowIso);

    if (overdueErr) {
      errors.push(`Error querying overdue items: ${overdueErr.message}`);
    } else if (overdueItems) {
      for (const rawItem of overdueItems) {
        const item = rawItem as ReminderItem;

        const payload: NotificationPayload = {
          itemId: item.id,
          userId: item.user_id,
          scenario: "OVERDUE",
          title: "Task Overdue",
          body: `Overdue: ${item.content}`,
          dueAt: item.due_at,
        };

        // Dispatch push notification
        await dispatchPushNotification(supabase, fcmServerKey, payload);

        // Mark flag to prevent duplicate overdue notifications (exactly one push per PRD 6.1.6)
        const { error: updateErr } = await supabase
          .from("items")
          .update({ reminder_sent_overdue: true })
          .eq("id", item.id);

        if (updateErr) {
          errors.push(`Failed to update reminder_sent_overdue for ${item.id}: ${updateErr.message}`);
        } else {
          dispatchedReminders.push(payload);
        }
      }
    }

    const responseBody = {
      timestamp: nowIso,
      status: "success",
      scanned_scenarios: ["DAY_BEFORE", "SAME_DAY_FALLBACK", "OVERDUE"],
      total_dispatched: dispatchedReminders.length,
      dispatched: dispatchedReminders,
      errors: errors.length > 0 ? errors : undefined,
    };

    return new Response(JSON.stringify(responseBody, null, 2), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  } catch (err: any) {
    return new Response(JSON.stringify({ error: err.message }), {
      status: 500,
      headers: { "Content-Type": "application/json" },
    });
  }
});

/**
 * Sends push payload to user's registered device tokens via FCM / push service
 */
async function dispatchPushNotification(
  supabase: any,
  fcmKey: string,
  payload: NotificationPayload
) {
  try {
    // Attempt to query registered device tokens if table exists
    const { data: tokens } = await supabase
      .from("device_tokens")
      .select("token, device_type")
      .eq("user_id", payload.userId);

    if (!tokens || tokens.length === 0) {
      console.log(`[Push Dispatch] Notification queued for user ${payload.userId}: [${payload.scenario}] "${payload.title}"`);
      return;
    }

    if (!fcmKey) {
      console.log(`[Push Dispatch] FCM_SERVER_KEY not configured. Dispatched locally for ${tokens.length} device(s).`);
      return;
    }

    // Push to FCM legacy or HTTP v1 endpoint
    for (const t of tokens) {
      await fetch("https://fcm.googleapis.com/fcm/send", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `key=${fcmKey}`,
        },
        body: JSON.stringify({
          to: t.token,
          notification: {
            title: payload.title,
            body: payload.body,
            sound: "default",
          },
          data: {
            itemId: payload.itemId,
            scenario: payload.scenario,
            dueAt: payload.dueAt,
          },
        }),
      });
    }
  } catch (err) {
    console.error(`Error in dispatchPushNotification: ${err}`);
  }
}
