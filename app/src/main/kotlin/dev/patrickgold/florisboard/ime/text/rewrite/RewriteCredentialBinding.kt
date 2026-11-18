/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.ime.text.rewrite

import java.net.URI

/**
 * A single encrypted value carries both the origin and the rewrite credential.
 * An unbound legacy API key can never be forwarded to an arbitrary new provider.
 */
internal object RewriteCredentialBinding {
    fun origin(endpoint: String): String? {
        val uri = runCatching { URI(endpoint.trim()) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true) ||
            uri.host.isNullOrBlank() || uri.rawUserInfo != null || uri.rawFragment != null
        ) return null
        val port = if (uri.port == -1) 443 else uri.port
        if (port !in 1..65535) return null
        return "https://" + uri.host.lowercase() + ":" + port
    }

    fun bind(endpoint: String, key: String): String? {
        val host = origin(endpoint) ?: return null
        val normalized = key.trim()
        if (normalized.isEmpty()) return null
        return host + "\n" + normalized
    }

    fun keyFor(serialized: String?, endpoint: String): String {
        val expected = origin(endpoint) ?: return ""
        val value = serialized ?: return ""
        val delimiter = value.indexOf('\n')
        if (delimiter <= 0 || value.substring(0, delimiter) != expected) return ""
        return value.substring(delimiter + 1).trim()
    }
}
