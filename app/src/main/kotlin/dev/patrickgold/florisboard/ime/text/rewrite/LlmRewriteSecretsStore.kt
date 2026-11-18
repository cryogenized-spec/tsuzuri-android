/*
 * Copyright (C) 2026 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.ime.text.rewrite

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class LlmRewriteSecretsStore(
    context: Context,
) {
    companion object {
        private const val FILE_NAME = "llm-rewrite-secrets"
        private const val KEY_API_KEY = "llm_rewrite_api_key" // legacy, deliberately not used for requests
        private const val KEY_BOUND_API_KEY = "llm_rewrite_bound_api_key_v1"
    }

    private val appContext = context.applicationContext

    private val encryptedPrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /** A key saved without an associated host must not be reused for any provider. */
    fun getApiKeyForEndpoint(endpoint: String): String =
        RewriteCredentialBinding.keyFor(
            encryptedPrefs.getString(KEY_BOUND_API_KEY, null), endpoint,
        )

    fun hasApiKeyForEndpoint(endpoint: String): Boolean =
        getApiKeyForEndpoint(endpoint).isNotBlank()

    /** Save the key and its trusted origin as ONE encrypted preference value. */
    fun setApiKeyForEndpoint(apiKey: String, endpoint: String): Boolean {
        val serialized = RewriteCredentialBinding.bind(endpoint, apiKey) ?: return false
        return encryptedPrefs.edit()
            .putString(KEY_BOUND_API_KEY, serialized)
            .remove(KEY_API_KEY)
            .commit()
    }

    /** Do not report removal until the encrypted preference has been committed. */
    fun clearApiKeyDurably(): Boolean =
        encryptedPrefs.edit().remove(KEY_BOUND_API_KEY).remove(KEY_API_KEY).commit()
}
