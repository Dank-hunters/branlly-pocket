package com.branlly.pocket.platform.android.actions

import android.net.Uri
import com.branlly.pocket.domain.execution.ActionExecutionContext
import com.branlly.pocket.domain.execution.ActionHandler
import com.branlly.pocket.domain.execution.ActionResult
import com.branlly.pocket.domain.execution.ActionValidationContext
import com.branlly.pocket.domain.execution.ActionValidationError
import com.branlly.pocket.domain.media.MediaCapabilitySnapshot
import com.branlly.pocket.domain.media.MediaPlaybackStrategy
import com.branlly.pocket.domain.media.PlayMediaWorkflow
import com.branlly.pocket.domain.model.ActionKind
import com.branlly.pocket.domain.model.ShortcutAction
import com.branlly.pocket.domain.workflow.ActionWorkflowContext
import com.branlly.pocket.domain.workflow.BoundedActionWorkflowRunner
import com.branlly.pocket.domain.workflow.CapabilityResolver

class PlayMediaHandler(
    private val capabilityResolver: CapabilityResolver<ShortcutAction.PlayMedia, MediaCapabilitySnapshot>,
    private val strategyFactory: (ShortcutAction.PlayMedia) -> List<MediaPlaybackStrategy>,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : ActionHandler<ShortcutAction.PlayMedia> {
    override val kind = ActionKind.PLAY_MEDIA

    override fun validate(
        action: ShortcutAction.PlayMedia,
        context: ActionValidationContext,
    ): List<ActionValidationError> = buildList {
        if (action.targetAppLabel.isBlank()) add(ActionValidationError("missing_app_label", "Choisissez une application multimédia."))
        if (!PACKAGE_NAME.matches(action.targetPackage)) {
            add(ActionValidationError("invalid_package", "Le package multimédia est invalide."))
        } else if (!context.isPackageInstalled(action.targetPackage)) {
            add(ActionValidationError("missing_package", "L’application multimédia n’est pas installée."))
        } else if (action.activityName == null && !context.isPackageLaunchable(action.targetPackage) && action.mediaUri == null) {
            add(ActionValidationError("package_not_launchable", "L’application multimédia n’est pas lançable."))
        }
        if (action.searchQuery.isBlank() && action.mediaUri.isNullOrBlank()) {
            add(ActionValidationError("missing_search", "Saisissez une recherche ou une URI exacte."))
        }
        action.mediaUri?.let { uri ->
            if (!safeMediaUri(uri)) add(ActionValidationError("invalid_media_uri", "Utilisez une URI HTTPS ou fournisseur valide."))
        }
        if (action.allowAdvancedAutomation) {
            add(ActionValidationError("automation_unavailable", "L’automatisation avancée n’est pas disponible dans cette phase."))
        }
    }

    override suspend fun execute(action: ShortcutAction.PlayMedia, context: ActionExecutionContext): ActionResult {
        val startedAt = context.workflowCheckpoint?.startedAtMillis ?: nowMillis()
        val expiresAt = context.workflowCheckpoint?.expiresAtMillis ?: (startedAt + action.timeoutMs)
        val capabilities = capabilityResolver.resolve(action)
        if (!capabilities.packageInstalled) return ActionResult.Failed("L’application multimédia n’est plus installée.")
        if (!capabilities.notificationListenerAuthorized) {
            return ActionResult.PermissionRequired("Autorisez l’accès aux notifications pour confirmer STATE_PLAYING.")
        }
        if (!capabilities.notificationListenerAvailable) {
            return ActionResult.Failed("Le NotificationListener multimédia est indisponible.", recoverable = true)
        }
        if (expiresAt <= nowMillis()) return ActionResult.TimedOut("Le workflow PLAY_MEDIA a expiré.")
        val workflowContext = ActionWorkflowContext(
            actionId = context.nodeId,
            executionId = context.executionId,
            routineId = context.routineId,
            actionKind = action.kind,
            startedAtMillis = startedAt,
            expiresAtMillis = expiresAt,
            logger = context.logger,
        )
        val workflow = PlayMediaWorkflow(
            action = action,
            executionContext = context,
            capabilities = capabilities,
            strategies = strategyFactory(action),
            restoredCheckpoint = context.workflowCheckpoint,
            nowMillis = nowMillis,
        )
        return BoundedActionWorkflowRunner(
            maxTransitions = PlayMediaWorkflow.MAX_TRANSITIONS,
            timeoutMillis = (expiresAt - nowMillis()).coerceIn(100L, action.timeoutMs),
        ).run(workflow, workflowContext).result
    }

    private fun safeMediaUri(raw: String): Boolean = runCatching {
        val uri = Uri.parse(raw)
        (uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() && uri.userInfo == null) ||
            (uri.scheme.equals("spotify", true) && uri.schemeSpecificPart.isNotBlank())
    }.getOrDefault(false)

    private companion object {
        val PACKAGE_NAME = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")
    }
}
