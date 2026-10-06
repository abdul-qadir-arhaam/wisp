/**
 * Wisp - Reminder Scanner Engine
 * Reference: PRD.md Section 6.1.6 & ARCHITECTURE.md Section 2.3, 2.4
 * 
 * Implements the 3 reminder scenarios:
 *  1. Day-Before (~24h before due_at)
 *  2. Same-Day Fallback (<24h remaining, due today)
 *  3. Overdue Follow-Up (due_at < now, completed = false, exactly one push)
 *  
 * Strictly visual push notifications. No voice/TTS delivery.
 */

class ReminderScanner {
  constructor(supabaseClient, fcmKey = null) {
    this.supabase = supabaseClient;
    this.fcmKey = fcmKey;
  }

  /**
   * Scans items table and dispatches due notifications.
   * @param {Date} [referenceDate] Optional reference date for testing/simulation
   * @returns {Promise<{dispatched: Array, errors: Array}>}
   */
  async runScan(referenceDate = new Date()) {
    const nowIso = referenceDate.toISOString();
    const nowTime = referenceDate.getTime();
    const dayAndHalfAhead = new Date(nowTime + 26 * 3600 * 1000).toISOString();

    const dispatched = [];
    const errors = [];

    // -------------------------------------------------------------------------
    // Scenario 1 & 2: Day-Before & Same-Day Fallback Reminders
    // Target: Incomplete items with due_at in the future, where reminder_sent_day_before = false
    // -------------------------------------------------------------------------
    const { data: upcomingItems, error: upcomingErr } = await this.supabase
      .from('items')
      .select('*')
      .eq('completed', false)
      .eq('reminder_sent_day_before', false)
      .gt('due_at', nowIso)
      .lte('due_at', dayAndHalfAhead);

    if (upcomingErr) {
      errors.push(`Query upcoming items failed: ${upcomingErr.message}`);
    } else if (upcomingItems) {
      for (const item of upcomingItems) {
        const dueTime = new Date(item.due_at).getTime();
        const hoursRemaining = (dueTime - nowTime) / (3600 * 1000);

        let scenario = 'DAY_BEFORE';
        let title = 'Upcoming Task Tomorrow';
        let body = `Remember: ${item.content}`;

        if (hoursRemaining <= 12) {
          // Scenario 2: Same-day fallback (created with < 24h remaining, due today)
          scenario = 'SAME_DAY_FALLBACK';
          title = 'Task Due Today';
          body = `Due today: ${item.content}`;
        }

        const payload = {
          itemId: item.id,
          userId: item.user_id,
          scenario,
          title,
          body,
          dueAt: item.due_at,
          content: item.content
        };

        // Dispatch notification
        await this.dispatchNotification(payload);

        // Mark flag immediately to prevent duplicate sends
        const { error: updateErr } = await this.supabase
          .from('items')
          .update({ reminder_sent_day_before: true })
          .eq('id', item.id);

        if (updateErr) {
          errors.push(`Update reminder_sent_day_before failed for ${item.id}: ${updateErr.message}`);
        } else {
          dispatched.push(payload);
        }
      }
    }

    // -------------------------------------------------------------------------
    // Scenario 3: One-Time Overdue Follow-Up
    // Target: Incomplete items where due_at < now, and reminder_sent_overdue = false
    // -------------------------------------------------------------------------
    const { data: overdueItems, error: overdueErr } = await this.supabase
      .from('items')
      .select('*')
      .eq('completed', false)
      .eq('reminder_sent_overdue', false)
      .lt('due_at', nowIso);

    if (overdueErr) {
      errors.push(`Query overdue items failed: ${overdueErr.message}`);
    } else if (overdueItems) {
      for (const item of overdueItems) {
        const payload = {
          itemId: item.id,
          userId: item.user_id,
          scenario: 'OVERDUE',
          title: 'Task Overdue',
          body: `Overdue: ${item.content}`,
          dueAt: item.due_at,
          content: item.content
        };

        // Dispatch notification
        await this.dispatchNotification(payload);

        // Mark flag immediately to guarantee exactly one overdue push
        const { error: updateErr } = await this.supabase
          .from('items')
          .update({ reminder_sent_overdue: true })
          .eq('id', item.id);

        if (updateErr) {
          errors.push(`Update reminder_sent_overdue failed for ${item.id}: ${updateErr.message}`);
        } else {
          dispatched.push(payload);
        }
      }
    }

    return {
      timestamp: nowIso,
      totalDispatched: dispatched.length,
      dispatched,
      errors
    };
  }

  /**
   * Delivers visual push notification payload
   */
  async dispatchNotification(payload) {
    // 1. Verify push is strictly visual (no voice delivery permitted per PRD 6.1.6)
    if (payload.speak || payload.voice) {
      throw new Error('PRD Violation: Voice/spoken delivery of reminders is prohibited.');
    }

    // 2. Query registered device tokens if device_tokens table is present
    try {
      const { data: tokens, error } = await this.supabase
        .from('device_tokens')
        .select('token, device_type')
        .eq('user_id', payload.userId);

      if (!error && tokens && tokens.length > 0 && this.fcmKey) {
        for (const t of tokens) {
          await fetch('https://fcm.googleapis.com/fcm/send', {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json',
              Authorization: `key=${this.fcmKey}`
            },
            body: JSON.stringify({
              to: t.token,
              notification: {
                title: payload.title,
                body: payload.body,
                sound: 'default'
              },
              data: {
                itemId: payload.itemId,
                scenario: payload.scenario,
                dueAt: payload.dueAt
              }
            })
          });
        }
      }
    } catch (e) {
      // Graceful fallback if device_tokens not yet configured in database
    }

    return true;
  }
}

module.exports = { ReminderScanner };
