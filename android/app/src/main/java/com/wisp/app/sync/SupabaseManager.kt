package com.wisp.app.sync

import com.wisp.app.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeOldRecord
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext

/**
 * Manages Supabase Auth, Postgrest operations, and Realtime WebSocket connections.
 */
object SupabaseManager {

    private val supabaseUrl: String = BuildConfig.SUPABASE_URL
    private val supabaseKey: String = BuildConfig.SUPABASE_ANON_KEY

    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = supabaseUrl,
            supabaseKey = supabaseKey
        ) {
            install(Auth)
            install(Postgrest)
            install(Realtime)
        }
    }

    private var itemsChannel: RealtimeChannel? = null

    val currentUserId: String?
        get() = client.auth.currentUserOrNull()?.id

    val isAuthenticated: Boolean
        get() = client.auth.currentSessionOrNull() != null

    /**
     * Authenticate user with Email and Password
     */
    suspend fun signIn(email: String, password: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            client.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            val userId = client.auth.currentUserOrNull()?.id
                ?: return@withContext Result.failure(Exception("Login succeeded but User ID was null"))
            Result.success(userId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Register a new user with Email and Password
     */
    suspend fun signUp(email: String, password: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            client.auth.signUpWith(Email) {
                this.email = email
                this.password = password
            }
            val userId = client.auth.currentUserOrNull()?.id
                ?: return@withContext Result.failure(Exception("Registration succeeded but User ID was null"))
            Result.success(userId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sign out current user
     */
    suspend fun signOut(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            client.auth.signOut()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Insert a new task/note item into Supabase `items` table
     */
    suspend fun insertItem(item: Item): Result<Item> = withContext(Dispatchers.IO) {
        try {
            val inserted = client.postgrest["items"]
                .insert(item) {
                    select()
                }
                .decodeSingle<Item>()
            Result.success(inserted)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Retrieve all active items for the authenticated user
     */
    suspend fun getItems(): Result<List<Item>> = withContext(Dispatchers.IO) {
        try {
            val list = client.postgrest["items"]
                .select()
                .decodeList<Item>()
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Updates completion state of a task item
     */
    suspend fun updateItemCompletion(itemId: String, completed: Boolean, completedAt: String?): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            client.postgrest["items"]
                .update({
                    set("completed", completed)
                    set("completed_at", completedAt)
                }) {
                    filter { eq("id", itemId) }
                }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Deletes a task item by ID
     */
    suspend fun deleteItem(itemId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            client.postgrest["items"]
                .delete {
                    filter { eq("id", itemId) }
                }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Subscribe to Realtime Postgres changes on the `items` table.
     * Invokes [onEvent] whenever an INSERT, UPDATE, or DELETE occurs.
     */
    suspend fun subscribeToItems(
        scope: CoroutineScope,
        onEvent: (eventType: String, item: Item?) -> Unit,
        onStatusChange: (status: String) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            try {
                // Ensure Realtime client is connected
                client.realtime.connect()
                onStatusChange("Connecting to Realtime...")

                val channel = client.channel("public:items")
                itemsChannel = channel

                val changeFlow = channel.postgresChangeFlow<io.github.jan.supabase.realtime.PostgresAction>(schema = "public") {
                    table = "items"
                }

                changeFlow.onEach { action ->
                    when (action) {
                        is io.github.jan.supabase.realtime.PostgresAction.Insert -> {
                            val item = try { action.decodeRecord<Item>() } catch (_: Exception) { null }
                            withContext(Dispatchers.Main) { onEvent("INSERT", item) }
                        }
                        is io.github.jan.supabase.realtime.PostgresAction.Update -> {
                            val item = try { action.decodeRecord<Item>() } catch (_: Exception) { null }
                            withContext(Dispatchers.Main) { onEvent("UPDATE", item) }
                        }
                        is io.github.jan.supabase.realtime.PostgresAction.Delete -> {
                            val item = try { action.decodeOldRecord<Item>() } catch (_: Exception) { null }
                            withContext(Dispatchers.Main) { onEvent("DELETE", item) }
                        }
                        else -> Unit
                    }
                }.launchIn(scope)

                channel.subscribe()
                withContext(Dispatchers.Main) { onStatusChange("Connected (channel: items)") }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onStatusChange("Connection error: ${e.message}") }
            }
        }
    }

    /**
     * Disconnect Realtime channel
     */
    suspend fun unsubscribe() {
        withContext(Dispatchers.IO) {
            itemsChannel?.unsubscribe()
            itemsChannel = null
        }
    }
}
