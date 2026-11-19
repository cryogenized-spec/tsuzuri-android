/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.ime.text.network

import java.net.URI

/** Remote AI endpoints carry BYOK credentials and therefore must be HTTPS. */
internal object SecureEndpointPolicy {
    fun isAllowed(address: String): Boolean {
        val uri = runCatching { URI(address.trim()) }.getOrNull() ?: return false
        return uri.scheme.equals("https", ignoreCase = true) &&
            !uri.host.isNullOrBlank() &&
            uri.userInfo == null &&
            uri.fragment == null
    }
}
