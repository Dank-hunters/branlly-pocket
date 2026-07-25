package com.branlly.pocket.domain.media

import org.json.JSONArray
import org.json.JSONObject

/** Versioned, Android-free payload for a suspended V3 media session. */
object MediaExecutionCheckpointCodec {
    const val VERSION = 1

    fun encode(checkpoint: MediaExecutionCheckpoint): String =
        JSONObject()
            .put("version", VERSION)
            .put("executionId", checkpoint.executionId)
            .put("routineId", checkpoint.routineId.value)
            .put("nodeId", checkpoint.nodeId.value)
            .put("startedAtMillis", checkpoint.startedAtMillis)
            .put("automaticDeadlineMillis", checkpoint.automaticDeadlineMillis)
            .put("globalDeadlineMillis", checkpoint.globalDeadlineMillis)
            .put("state", checkpoint.state.name)
            .put("stateVersion", checkpoint.stateVersion)
            .put("operationId", checkpoint.operationId)
            .put("continuationConsumed", checkpoint.continuationConsumed)
            .put("manualAssistanceShown", checkpoint.manualGuidanceShown)
            .put("baselinePlaying", JSONArray(checkpoint.baseline.playingSessionIds))
            .put("baselineKnown", JSONArray(checkpoint.baseline.knownSessionIds))
            .put(
                "plan",
                JSONArray().apply {
                    checkpoint.plan.operations.forEach { operation ->
                        put(
                            JSONObject()
                                .put(
                                    "id",
                                    operation.id,
                                ).put("type", operation.type.name)
                                .put("automatic", operation.automatic)
                                .put("status", operation.status.name),
                        )
                    }
                },
            ).toString()

    fun decode(raw: String): MediaExecutionCheckpoint? =
        runCatching {
            val value = JSONObject(raw)
            if (value.optInt("version") != VERSION) return null
            val state = MediaExecutionState.valueOf(value.getString("state"))
            if (state in
                setOf(
                    MediaExecutionState.COMPLETED,
                    MediaExecutionState.FAILED,
                    MediaExecutionState.CANCELLED,
                    MediaExecutionState.TIMED_OUT,
                )
            ) {
                return null
            }

            fun strings(key: String) =
                buildSet {
                    val array = value.optJSONArray(key) ?: return@buildSet
                    repeat(array.length()) { add(array.getString(it)) }
                }
            val operations =
                buildList {
                    val array = value.getJSONArray("plan")
                    repeat(array.length()) {
                        val item = array.getJSONObject(it)
                        add(
                            MediaOperation(
                                item.getString("id"),
                                MediaOperationType.valueOf(item.getString("type")),
                                item.getBoolean("automatic"),
                                MediaOperationStatus.valueOf(item.getString("status")),
                            ),
                        )
                    }
                }
            val executionId = value.getString("executionId").takeIf(String::isNotBlank) ?: return null
            val routineId =
                com.branlly.pocket.domain.model
                    .ShortcutId(value.getString("routineId").takeIf(String::isNotBlank) ?: return null)
            val nodeId =
                com.branlly.pocket.domain.model
                    .NodeId(value.getString("nodeId").takeIf(String::isNotBlank) ?: return null)
            val startedAt = value.getLong("startedAtMillis")
            val automaticDeadline = value.getLong("automaticDeadlineMillis")
            val globalDeadline = value.getLong("globalDeadlineMillis")
            if (globalDeadline < startedAt || automaticDeadline > globalDeadline) return null
            MediaExecutionCheckpoint(
                executionId = executionId,
                routineId = routineId,
                nodeId = nodeId,
                startedAtMillis = startedAt,
                automaticDeadlineMillis = automaticDeadline,
                globalDeadlineMillis = globalDeadline,
                state = state,
                stateVersion = value.getInt("stateVersion"),
                operationId =
                    value.optString("operationId").ifBlank {
                        null
                    },
                continuationConsumed = value.getBoolean("continuationConsumed"),
                manualGuidanceShown = value.getBoolean("manualAssistanceShown"),
                baseline = MediaSessionBaseline(strings("baselinePlaying"), strings("baselineKnown")),
                plan = MediaExecutionPlan(operations),
            )
        }.getOrNull()
}
