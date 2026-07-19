package com.branlly.pocket.platform.android.actions

import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Process
import com.branlly.pocket.domain.execution.ActionExecutionContext
import com.branlly.pocket.domain.execution.ActionResult

interface ExternalActivityGateway {
    fun canResolve(intent: Intent): Boolean

    suspend fun launch(
        intent: Intent,
        label: String,
        executionContext: ActionExecutionContext,
    ): ActionResult
}

class AndroidExternalActivityLauncher(
    private val context: Context,
) : ExternalActivityGateway {
    override fun canResolve(intent: Intent): Boolean = intent.resolveActivity(context.packageManager) != null

    override suspend fun launch(
        intent: Intent,
        label: String,
        executionContext: ActionExecutionContext,
    ): ActionResult {
        val launchIntent = Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!canResolve(launchIntent)) return ActionResult.Failed("Aucune application ne peut exécuter cette action.")
        executionContext.logger.log(
            "EXTERNAL_ACTIVITY_REQUESTED",
            mapOf("nodeId" to executionContext.nodeId.value, "targetPackage" to launchIntent.`package`, "label" to label),
        )
        if (!executionContext.userInitiated && !isProcessVisible()) {
            executionContext.logger.log(
                "USER_ACTION_REQUIRED",
                mapOf("nodeId" to executionContext.nodeId.value, "reason" to "background_launch"),
            )
            return ActionResult.UserActionRequired(
                reason = "Android exige une action utilisateur pour ouvrir $label depuis l’arrière-plan.",
            )
        }
        return try {
            context.startActivity(launchIntent)
            ActionResult.Completed
        } catch (_: ActivityNotFoundException) {
            ActionResult.Failed("L’application cible n’est pas disponible.")
        } catch (_: SecurityException) {
            ActionResult.UserActionRequired(
                reason = "Android a refusé l’ouverture directe de $label.",
            )
        }
    }

    private fun isProcessVisible(): Boolean {
        val manager = context.getSystemService(ActivityManager::class.java)
        val process = manager.runningAppProcesses?.firstOrNull { it.pid == Process.myPid() } ?: return false
        return process.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }

}
