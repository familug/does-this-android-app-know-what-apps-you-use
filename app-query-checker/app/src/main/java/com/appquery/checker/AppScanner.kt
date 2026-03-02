package com.appquery.checker

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import java.util.zip.ZipFile

class AppScanner(private val context: Context) {

    private val parser = BinaryXmlParser()

    private val systemPrefixes = listOf("android.", "androidx.", "com.android")

    fun scanAllApps(onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }): List<AppQueryInfo> {
        val pm = context.packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val results = mutableListOf<AppQueryInfo>()

        packages.forEachIndexed { index, appInfo ->
            onProgress(index + 1, packages.size)
            try {
                val result = scanApp(pm, appInfo)
                results.add(result)
            } catch (_: Exception) {
                // Skip apps that can't be scanned
            }
        }

        return results.sortedWith(
            compareBy<AppQueryInfo> { it.riskLevel.ordinal }
                .thenByDescending { it.queriesPackages.size }
                .thenBy { it.appName.lowercase() }
        )
    }

    private fun scanApp(pm: PackageManager, appInfo: ApplicationInfo): AppQueryInfo {
        val appName = pm.getApplicationLabel(appInfo).toString()
        val icon = try { pm.getApplicationIcon(appInfo) } catch (_: Exception) { null }
        val apkPath = appInfo.sourceDir

        val queriesResult = try {
            ZipFile(apkPath).use { zip ->
                val entry = zip.getEntry("AndroidManifest.xml") ?: return@use null
                zip.getInputStream(entry).use { input ->
                    parser.parseManifestQueries(input)
                }
            }
        } catch (_: Exception) {
            null
        }

        val packages = queriesResult?.queriesPackages?.filter { pkg ->
            systemPrefixes.none { prefix -> pkg.startsWith(prefix) }
        } ?: emptyList()

        val intentActions = queriesResult?.queriesIntentActions ?: emptyList()
        val hasQueryAll = queriesResult?.hasQueryAllPackages ?: false
        val canSeeAll = hasQueryAll || intentActions.contains("android.intent.action.MAIN")

        val riskLevel = when {
            canSeeAll -> RiskLevel.HIGH
            packages.isNotEmpty() || intentActions.isNotEmpty() -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }

        return AppQueryInfo(
            appName = appName,
            packageName = appInfo.packageName,
            icon = icon,
            queriesPackages = packages.sorted(),
            queriesIntentActions = intentActions.sorted(),
            hasQueryAllPackages = hasQueryAll,
            canSeeAllApps = canSeeAll,
            riskLevel = riskLevel
        )
    }
}
