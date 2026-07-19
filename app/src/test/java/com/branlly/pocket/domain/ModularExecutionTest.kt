package com.branlly.pocket.domain

import com.branlly.pocket.domain.execution.ActionEditorKey
import com.branlly.pocket.domain.execution.ActionExecutionContext
import com.branlly.pocket.domain.execution.ActionHandler
import com.branlly.pocket.domain.execution.ActionRegistry
import com.branlly.pocket.domain.execution.ActionResult
import com.branlly.pocket.domain.execution.ActionValidationContext
import com.branlly.pocket.domain.execution.ActionValidationError
import com.branlly.pocket.domain.execution.RegisteredAction
import com.branlly.pocket.domain.execution.RoutineValidator
import com.branlly.pocket.domain.model.ActionCategory
import com.branlly.pocket.domain.model.ActionKind
import com.branlly.pocket.domain.model.ActionNode
import com.branlly.pocket.domain.model.InputValue
import com.branlly.pocket.domain.model.ShortcutAction
import com.branlly.pocket.domain.model.ShortcutDefinition
import com.branlly.pocket.domain.model.Trigger
import com.branlly.pocket.platform.android.RoutineExecutionResult
import com.branlly.pocket.platform.android.ShortcutExecutor
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModularExecutionTest {
    private val validationContext = object : ActionValidationContext {
        override fun isPackageInstalled(packageName: String) = true
        override fun isPackageLaunchable(packageName: String) = true
    }

    @Test
    fun `three registered handlers execute strictly in node order`() = runBlocking {
        val calls = mutableListOf<String>()
        val registry = registry(
            registration(ActionKind.OPEN_APPLICATION, ShortcutAction.OpenApplication::class.java, { app() }) { calls += "application" },
            registration(ActionKind.WAIT, ShortcutAction.Wait::class.java, { wait() }) { calls += "wait" },
            registration(ActionKind.OPEN_ROUTE, ShortcutAction.OpenRoute::class.java, { route() }) { calls += "route" },
        )
        val routine = routine(app(), wait(), route())

        assertEquals(RoutineExecutionResult.Completed, ShortcutExecutor(registry).execute(routine, "execution"))
        assertEquals(listOf("application", "wait", "route"), calls)
    }

    @Test
    fun `new registered handler executes without changing ShortcutExecutor`() = runBlocking {
        var called = false
        val registry = registry(
            registration(ActionKind.NOTIFICATION, ShortcutAction.Notification::class.java, { notification() }) { called = true },
        )

        assertEquals(RoutineExecutionResult.Completed, ShortcutExecutor(registry).execute(routine(notification()), "execution"))
        assertTrue(called)
    }

    @Test
    fun `action without handler is refused before execution`() {
        val issues = RoutineValidator(registry(), validationContext).validate(routine(notification()))
        assertTrue(issues.any { it.code == "missing_handler" })
    }

    @Test
    fun `invisible registered action does not appear in editor list`() {
        val registration = registration(
            ActionKind.NOTIFICATION,
            ShortcutAction.Notification::class.java,
            { notification() },
            visible = false,
        ) {}
        assertFalse(registry(registration).visibleActions().any { it.kind == ActionKind.NOTIFICATION })
    }

    @Test
    fun `two identical actions remain two executions`() = runBlocking {
        var calls = 0
        val registry = registry(registration(ActionKind.WAIT, ShortcutAction.Wait::class.java, { wait() }) { calls++ })
        val first = ActionNode(action = wait())
        val second = ActionNode(action = wait())

        ShortcutExecutor(registry).execute(
            ShortcutDefinition(name = "identical", trigger = Trigger.ManualButton, nodes = listOf(first, second)),
            "execution",
        )

        assertEquals(2, calls)
        assertTrue(first.id != second.id)
    }

    @Test
    fun `two media waits keep independent packages`() = runBlocking {
        val packages = mutableListOf<String>()
        val handler = object : ActionHandler<ShortcutAction.WaitForMediaPlayback> {
            override val kind = ActionKind.WAIT_FOR_MEDIA_PLAYBACK
            override fun validate(action: ShortcutAction.WaitForMediaPlayback, context: ActionValidationContext) = emptyList<ActionValidationError>()
            override suspend fun execute(action: ShortcutAction.WaitForMediaPlayback, context: ActionExecutionContext): ActionResult {
                packages += (action.packageName as InputValue.Fixed<String>).value
                return ActionResult.Completed
            }
        }
        val registry = registry(
            RegisteredAction(
                ActionKind.WAIT_FOR_MEDIA_PLAYBACK,
                ShortcutAction.WaitForMediaPlayback::class.java,
                "wait",
                "wait",
                ActionCategory.ORGANIZE,
                ActionEditorKey.MEDIA_WAIT,
                { ShortcutAction.WaitForMediaPlayback(InputValue.AskAtRuntime) },
                handler = handler,
            ),
        )

        ShortcutExecutor(registry).execute(
            routine(
                ShortcutAction.WaitForMediaPlayback(InputValue.Fixed("reader.one")),
                ShortcutAction.WaitForMediaPlayback(InputValue.Fixed("reader.two")),
            ),
            "execution",
        )

        assertEquals(listOf("reader.one", "reader.two"), packages)
    }

    @Test
    fun `routine without media executes`() = runBlocking {
        var calls = 0
        val registry = registry(registration(ActionKind.WAIT, ShortcutAction.Wait::class.java, { wait() }) { calls++ })
        assertEquals(RoutineExecutionResult.Completed, ShortcutExecutor(registry).execute(routine(wait()), "execution"))
        assertEquals(1, calls)
    }

    @Test
    fun `routine without navigation executes`() = runBlocking {
        var calls = 0
        val registry = registry(registration(ActionKind.OPEN_APPLICATION, ShortcutAction.OpenApplication::class.java, { app() }) { calls++ })
        assertEquals(RoutineExecutionResult.Completed, ShortcutExecutor(registry).execute(routine(app()), "execution"))
        assertEquals(1, calls)
    }

    @Test
    fun `user action required stops generic engine without launching another node`() = runBlocking {
        val calls = mutableListOf<String>()
        val blocked = resultRegistration(
            ActionKind.OPEN_APPLICATION,
            ShortcutAction.OpenApplication::class.java,
            { app() },
            ActionResult.UserActionRequired("background launch"),
        ) { calls += "blocked" }
        val next = registration(ActionKind.WAIT, ShortcutAction.Wait::class.java, { wait() }) { calls += "next" }

        val result = ShortcutExecutor(registry(blocked, next)).execute(routine(app(), wait()), "execution")

        assertTrue(result is RoutineExecutionResult.WaitingUserAction)
        assertEquals(listOf("blocked"), calls)
    }

    @Test
    fun `node timeout is a generic result`() = runBlocking {
        val handler = object : ActionHandler<ShortcutAction.Wait> {
            override val kind = ActionKind.WAIT
            override fun validate(action: ShortcutAction.Wait, context: ActionValidationContext) = emptyList<ActionValidationError>()
            override suspend fun execute(action: ShortcutAction.Wait, context: ActionExecutionContext): ActionResult {
                delay(250)
                return ActionResult.Completed
            }
        }
        val registry = registry(
            RegisteredAction(ActionKind.WAIT, ShortcutAction.Wait::class.java, "wait", "wait", ActionCategory.ORGANIZE, ActionEditorKey.WAIT, { wait() }, handler = handler),
        )
        val routine = ShortcutDefinition(
            name = "timeout",
            trigger = Trigger.ManualButton,
            nodes = listOf(ActionNode(action = wait(), timeoutMillis = 100)),
        )

        val result = ShortcutExecutor(registry).execute(routine, "execution") as RoutineExecutionResult.Stopped
        assertTrue(result.result is ActionResult.TimedOut)
    }

    private fun registry(vararg registrations: RegisteredAction<out ShortcutAction>) = ActionRegistry(registrations.toList())

    private fun <A : ShortcutAction> registration(
        kind: ActionKind,
        type: Class<A>,
        default: () -> A,
        visible: Boolean = true,
        call: suspend (A) -> Unit,
    ): RegisteredAction<A> = resultRegistration(kind, type, default, ActionResult.Completed, visible, call)

    private fun <A : ShortcutAction> resultRegistration(
        kind: ActionKind,
        type: Class<A>,
        default: () -> A,
        result: ActionResult,
        visible: Boolean = true,
        call: suspend (A) -> Unit,
    ): RegisteredAction<A> = RegisteredAction(
        kind = kind,
        actionClass = type,
        title = kind.name,
        description = kind.name,
        category = ActionCategory.ORGANIZE,
        editorKey = ActionEditorKey.WAIT,
        createDefault = default,
        handler = object : ActionHandler<A> {
            override val kind = kind
            override fun validate(action: A, context: ActionValidationContext) = emptyList<ActionValidationError>()
            override suspend fun execute(action: A, context: ActionExecutionContext): ActionResult {
                call(action)
                return result
            }
        },
        visibleInEditor = visible,
    )

    private fun routine(vararg actions: ShortcutAction) = ShortcutDefinition(
        name = "test",
        trigger = Trigger.ManualButton,
        nodes = actions.map { ActionNode(action = it) },
    )

    private fun app() = ShortcutAction.OpenApplication(InputValue.Fixed("example.app"))
    private fun wait() = ShortcutAction.Wait(100)
    private fun route() = ShortcutAction.OpenRoute(InputValue.Fixed("maps.app"), InputValue.Fixed("Paris"))
    private fun notification() = ShortcutAction.Notification("title", "message")
}
