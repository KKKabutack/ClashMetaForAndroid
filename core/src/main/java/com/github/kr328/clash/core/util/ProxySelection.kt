package com.github.kr328.clash.core.util

import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.core.model.ProxyGroup

/**
 * Helpers for resolving the currently selected member of Selector / nested groups.
 *
 * Clash exposes only one level of `ProxyGroup.now` per query. Nested groups therefore need
 * to be looked up in the sibling group map (or walked via repeated queries) so the UI can
 * show e.g. `Selector(HK-01)` instead of the bare type string `Selector`.
 */
object ProxySelection {
    /** Format a proxy card subtitle, restoring the legacy `Type(now)` link display. */
    fun subtitle(proxy: Proxy, groups: Map<String, ProxyGroup>): String {
        if (!proxy.isGroup) return proxy.subtitle

        val linkedNow = groups[proxy.name]?.now?.takeIf { it.isNotBlank() }
        return if (linkedNow != null) {
            "${proxy.type.ifBlank { proxy.subtitle }}($linkedNow)"
        } else {
            proxy.type.ifBlank { proxy.subtitle }
        }
    }

    /**
     * Walk `now` through nested groups until a leaf proxy name is found.
     * Returns [start] when it is already a leaf or the chain cannot continue.
     */
    fun resolveLeafName(
        groups: Map<String, ProxyGroup>,
        start: String,
        maxDepth: Int = 8,
    ): String {
        if (start.isBlank()) return start

        var current = start
        repeat(maxDepth) {
            val group = groups[current] ?: return current
            val now = group.now
            if (now.isBlank()) return current
            current = now
        }
        return current
    }
}
