package com.orderpackager.data.auth

import android.content.Context
import com.google.gson.annotations.SerializedName
import com.orderpackager.BuildConfig
import com.orderpackager.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val email: String?) : AuthState
    data class Error(val message: String) : AuthState
}

data class PasswordLoginRequest(val email: String, val password: String)
data class RefreshRequest(@SerializedName("refresh_token") val refreshToken: String)
data class AuthUser(val email: String?)
data class AuthSession(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String,
    @SerializedName("expires_in") val expiresIn: Long,
    val user: AuthUser?
)

private interface SupabaseAuthApi {
    @POST("auth/v1/token")
    suspend fun signIn(
        @Query("grant_type") grantType: String = "password",
        @Body request: PasswordLoginRequest
    ): Response<AuthSession>

    @POST("auth/v1/token")
    suspend fun refresh(
        @Query("grant_type") grantType: String = "refresh_token",
        @Body request: RefreshRequest
    ): Response<AuthSession>
}

class SupabaseAuthManager private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = context.applicationContext.getSharedPreferences("supabase_auth", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private val api: SupabaseAuthApi by lazy {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder()
                .header("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
                .header("Content-Type", "application/json")
                .build())
        }.build()
        Retrofit.Builder()
            .baseUrl(BuildConfig.SUPABASE_URL.trimEnd('/') + "/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SupabaseAuthApi::class.java)
    }

    init { restore() }

    fun signIn(email: String, password: String) {
        scope.launch {
            _state.value = AuthState.Loading
            runCatching {
                val response = api.signIn(request = PasswordLoginRequest(email.trim(), password))
                val session = response.body()
                if (!response.isSuccessful || session == null) error("HTTP ${response.code()}")
                save(session)
                _state.value = AuthState.SignedIn(session.user?.email ?: email.trim())
            }.onFailure { _state.value = AuthState.Error(appContext.getString(R.string.login_failed)) }
        }
    }

    fun signOut() {
        prefs.edit().clear().apply()
        _state.value = AuthState.SignedOut
    }

    suspend fun getValidAccessToken(): String? {
        val token = prefs.getString(KEY_ACCESS, null) ?: return null
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0)
        if (System.currentTimeMillis() < expiresAt - 60_000) return token
        return refreshSession()
    }

    private fun restore() {
        scope.launch {
            if (prefs.getString(KEY_REFRESH, null).isNullOrBlank()) {
                _state.value = AuthState.SignedOut
            } else if (refreshSession() == null) {
                _state.value = AuthState.SignedOut
            }
        }
    }

    private suspend fun refreshSession(): String? = runCatching {
        val refresh = prefs.getString(KEY_REFRESH, null) ?: return null
        val response = api.refresh(request = RefreshRequest(refresh))
        val session = response.body()
        if (!response.isSuccessful || session == null) error("HTTP ${response.code()}")
        save(session)
        _state.value = AuthState.SignedIn(session.user?.email ?: prefs.getString(KEY_EMAIL, null))
        session.accessToken
    }.getOrElse {
        prefs.edit().clear().apply()
        null
    }

    private fun save(session: AuthSession) {
        prefs.edit()
            .putString(KEY_ACCESS, session.accessToken)
            .putString(KEY_REFRESH, session.refreshToken)
            .putString(KEY_EMAIL, session.user?.email)
            .putLong(KEY_EXPIRES_AT, System.currentTimeMillis() + session.expiresIn * 1000)
            .apply()
    }

    companion object {
        private const val KEY_ACCESS = "access_token"
        private const val KEY_REFRESH = "refresh_token"
        private const val KEY_EMAIL = "email"
        private const val KEY_EXPIRES_AT = "expires_at"
        @Volatile private var INSTANCE: SupabaseAuthManager? = null

        fun getInstance(context: Context): SupabaseAuthManager = INSTANCE ?: synchronized(this) {
            SupabaseAuthManager(context).also { INSTANCE = it }
        }
    }
}
