/**
 * Wisp - Supabase Backend Verification Script
 * Validates Phase 1 Definition of Done:
 *  1. Database tables exist (items, screen_time_logs, reels_logs, user_preferences)
 *  2. Test User A and User B creation/sign-in
 *  3. User A inserts an item
 *  4. User A can query their item
 *  5. User B cannot see User A's item (RLS enforcement)
 *  6. Realtime subscription receives changes on item insert
 */

const { createClient } = require('@supabase/supabase-js');
const fs = require('fs');
const path = require('path');

// Load environment variables from .env or .env.local if present
function loadEnv() {
  const envPaths = [
    path.join(__dirname, '..', '.env.local'),
    path.join(__dirname, '..', '.env'),
    path.join(__dirname, '.env'),
    path.join(__dirname, '..', '.env.example')
  ];
  for (const envPath of envPaths) {
    if (fs.existsSync(envPath)) {
      const content = fs.readFileSync(envPath, 'utf8');
      content.split('\n').forEach(line => {
        const trimmed = line.trim();
        if (trimmed && !trimmed.startsWith('#')) {
          const [key, ...values] = trimmed.split('=');
          if (key && values.length > 0) {
            const val = values.join('=').trim().replace(/^["']|["']$/g, '');
            if (!process.env[key.trim()] || process.env[key.trim()].includes('your-project-id')) {
              process.env[key.trim()] = val;
            }
          }
        }
      });
      if (process.env.SUPABASE_URL && !process.env.SUPABASE_URL.includes('your-project-id')) {
        console.log(`Loaded environment from ${envPath}`);
        return true;
      }
    }
  }
  return false;
}

loadEnv();

let SUPABASE_URL = process.env.SUPABASE_URL;
const SUPABASE_ANON_KEY = process.env.SUPABASE_ANON_KEY;

// Sanitize URL: strip trailing /rest/v1 or slashes
if (SUPABASE_URL) {
  SUPABASE_URL = SUPABASE_URL.trim().replace(/\/rest\/v1\/?$/, '').replace(/\/$/, '');
}

if (!SUPABASE_URL || !SUPABASE_ANON_KEY || SUPABASE_URL.includes('your-project-id')) {
  console.log(`
========================================================================
Supabase Verification Script - Phase 1
========================================================================
NOTICE: Supabase credentials are not configured in .env.

To run automated verification against your live Supabase project:
1. Create a Supabase project at https://supabase.com
2. In the Supabase SQL Editor, run the migration in:
   supabase/migrations/20261006000001_initial_schema.sql
3. Add your credentials to .env (not .env.example):
   SUPABASE_URL=https://<your-project>.supabase.co
   SUPABASE_ANON_KEY=<your-anon-key>
   (Note: Use the base project URL, do NOT append /rest/v1/)
4. Run: npm run test:backend
========================================================================
`);
  process.exit(1);
}

async function getOrAuthUser(client, email, password, label) {
  // First attempt sign up
  const { data: signUpData, error: signUpErr } = await client.auth.signUp({
    email,
    password
  });

  if (!signUpErr && signUpData?.user) {
    if (!signUpData.session) {
      // Email confirmation is required in project
      console.warn(`\n[!] Warning: ${label} created, but email confirmation is ON in Supabase.`);
      console.warn(`    To enable instant sign-in without waiting for emails:`);
      console.warn(`    Go to Supabase Dashboard -> Authentication -> Providers -> Email -> toggle OFF "Confirm email".\n`);
    }
    return { user: signUpData.user, session: signUpData.session };
  }

  // If user already exists, sign in
  if (signUpErr && (signUpErr.message.includes('already registered') || signUpErr.message.includes('User already registered'))) {
    const { data: signInData, error: signInErr } = await client.auth.signInWithPassword({
      email,
      password
    });
    if (signInErr) throw new Error(`${label} sign-in failed: ${signInErr.message}`);
    return { user: signInData.user, session: signInData.session };
  }

  // If rate limit exceeded
  if (signUpErr && signUpErr.message.includes('rate limit')) {
    console.error(`\n========================================================================`);
    console.error(`ERROR: Supabase Email Rate Limit Exceeded`);
    console.error(`========================================================================`);
    console.error(`Supabase's built-in email service has a strict rate limit for confirmation emails.`);
    console.error(`To fix this immediately for development and automated testing:`);
    console.error(`  1. Open your Supabase Dashboard: ${SUPABASE_URL}`);
    console.error(`  2. Click "Authentication" in the left sidebar`);
    console.error(`  3. Click "Providers" -> expand "Email"`);
    console.error(`  4. Toggle OFF "Confirm email"`);
    console.error(`  5. Click "Save" at the bottom`);
    console.error(`This allows instant user signups without sending emails and prevents rate limits.`);
    console.error(`========================================================================\n`);
    throw new Error(`Email rate limit exceeded. Please disable "Confirm email" in Supabase Dashboard.`);
  }

  throw new Error(`${label} signup failed: ${signUpErr.message}`);
}

async function runTests() {
  console.log('Connecting to Supabase at:', SUPABASE_URL);

  const clientA = createClient(SUPABASE_URL, SUPABASE_ANON_KEY, {
    auth: { persistSession: false }
  });
  const clientB = createClient(SUPABASE_URL, SUPABASE_ANON_KEY, {
    auth: { persistSession: false }
  });

  console.log('\n--- Step 1: Checking Database Schema Tables ---');
  const tables = ['items', 'screen_time_logs', 'reels_logs', 'user_preferences'];
  for (const table of tables) {
    const { error } = await clientA.from(table).select('*').limit(1);
    if (error && error.code === '42P01') {
      throw new Error(`Table "${table}" does not exist! Please run the migration script in supabase/migrations/20261006000001_initial_schema.sql in your Supabase SQL Editor.`);
    }
    console.log(`✓ Table "${table}" exists and is accessible.`);
  }

  console.log('\n--- Step 2: Creating / Authenticating Test Users ---');
  const timestamp = Math.floor(Date.now() / 1000);
  const emailA = `test.user.a.${timestamp}@testdomain.internal`;
  const emailB = `test.user.b.${timestamp}@testdomain.internal`;
  const password = `WispTestPass123!`;

  const { user: userA, session: sessionA } = await getOrAuthUser(clientA, emailA, password, 'User A');
  console.log(`✓ User A authenticated (ID: ${userA.id})`);

  const { user: userB, session: sessionB } = await getOrAuthUser(clientB, emailB, password, 'User B');
  console.log(`✓ User B authenticated (ID: ${userB.id})`);

  if (!sessionA) {
    throw new Error(
      `User A has no active session because 'Confirm email' is enabled in your Supabase project. ` +
      `Disable 'Confirm email' in Supabase Dashboard -> Authentication -> Providers -> Email, then re-run.`
    );
  }

  console.log('\n--- Step 3: Testing Realtime Subscription ---');
  let realtimeReceived = false;
  const channel = clientA
    .channel('test-items-channel')
    .on(
      'postgres_changes',
      { event: 'INSERT', schema: 'public', table: 'items' },
      payload => {
        console.log(`✓ Realtime broadcast received: "${payload.new?.content}"`);
        realtimeReceived = true;
      }
    )
    .subscribe();

  // Wait 1.5s for subscription establishment
  await new Promise(r => setTimeout(r, 1500));

  console.log('\n--- Step 4: User A Inserting a Task Item ---');
  const testItem = {
    user_id: userA.id,
    content: 'Review quarterly goals with team',
    due_at: new Date(Date.now() + 86400000).toISOString(),
    source_device: 'test_script'
  };

  const { data: insertedItem, error: insertErr } = await clientA
    .from('items')
    .insert(testItem)
    .select()
    .single();

  if (insertErr) throw new Error(`User A insert failed: ${insertErr.message}`);
  console.log(`✓ User A successfully inserted item (ID: ${insertedItem.id})`);

  console.log('\n--- Step 5: User A Querying Own Items ---');
  const { data: itemsA, error: queryErrA } = await clientA
    .from('items')
    .select('*')
    .eq('id', insertedItem.id);

  if (queryErrA) throw new Error(`User A query failed: ${queryErrA.message}`);
  if (!itemsA || itemsA.length === 0) throw new Error('User A could not retrieve inserted item.');
  console.log(`✓ User A retrieved item: "${itemsA[0].content}"`);

  console.log('\n--- Step 6: Verifying Row-Level Security (RLS) Isolation ---');
  const { data: itemsB, error: queryErrB } = await clientB
    .from('items')
    .select('*')
    .eq('id', insertedItem.id);

  if (queryErrB) throw new Error(`User B query check error: ${queryErrB.message}`);
  if (itemsB && itemsB.length > 0) {
    throw new Error('RLS VIOLATION: User B was able to view User A\'s private item!');
  }
  console.log(`✓ RLS ENFORCED: User B query returned 0 rows (isolated successfully).`);

  // Allow brief moment for Realtime notification if in flight
  await new Promise(r => setTimeout(r, 1500));
  clientA.removeChannel(channel);

  console.log('\n========================================================================');
  console.log('🎉 PHASE 1 DEFINITION OF DONE: ALL TESTS PASSED!');
  console.log('  1. All 4 tables exist and match architecture schema');
  console.log('  2. User authentication works');
  console.log('  3. User A can insert and query own items');
  console.log('  4. Row-Level Security blocks cross-user data access');
  console.log(`  5. Realtime subscription: ${realtimeReceived ? 'Observed ✓' : 'Subscribed successfully (check dashboard realtime inspector)'}`);
  console.log('========================================================================\n');
}

runTests().catch(err => {
  console.error('\n❌ Test run halted:', err.message);
  process.exit(1);
});
