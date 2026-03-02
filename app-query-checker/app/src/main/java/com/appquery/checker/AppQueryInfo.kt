package com.appquery.checker

import android.graphics.drawable.Drawable

data class AppQueryInfo(
    val appName: String,
    val packageName: String,
    val icon: Drawable?,
    val queriesPackages: List<String>,
    val queriesIntentActions: List<String>,
    val hasQueryAllPackages: Boolean,
    val canSeeAllApps: Boolean,
    val riskLevel: RiskLevel
)

enum class RiskLevel {
    HIGH,   // Can see ALL installed apps (ACTION_MAIN or QUERY_ALL_PACKAGES)
    MEDIUM, // Queries specific packages
    LOW     // No queries
}
