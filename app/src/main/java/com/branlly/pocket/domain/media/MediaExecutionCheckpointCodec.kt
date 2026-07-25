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
            .put("continuationCreated", checkpoint.continuationCreated)
            .put("continuationConsumed", checkpoint.continuationConsumed)
            .put("continuationKey", checkpoint.continuationKey)
            .put("manualAssistanceShown", checkpoint.manualGuidanceShown)
            .put("baselinePlaying", JSONArray(checkpoint.baseline.playingSessionIds))
            .put("baselineKnown", JSONArray(checkpoint.baseline.knownSessionIds))
            .put(
                "baseline",
                JSONObject()
                    .put(
                        "present",
                        checkpoint.baseline.sessionPresent,
                    ).put(
                        "package",
                        checkpoint.baseline.packageName,
                    ).put(
                        "state",
                        checkpoint.baseline.playbackState.name,
                    ).put(
                        "title",
                        checkpoint.baseline.title,
                    ).put(
                        "artist",
                        checkpoint.baseline.artist,
                    ).put(
                        "album",
                        checkpoint.baseline.album,
                    ).put(
                        "uri",
                        checkpoint.baseline.mediaUri,
                    ).put(
                        "sessionId",
                        checkpoint.baseline.sessionId,
                    ).put(
                        "position",
                        checkpoint.baseline.positionMillis,
                    ).put("capturedAt", checkpoint.baseline.capturedAtMillis)
                    .put("metadata", checkpoint.baseline.metadataState.name),
            ).put(
                "plan",
                JSONArray().apply {
                    checkpoint.plan.operations.forEachIndexed { position, operation ->
                        put(
                            JSONObject()
                                .put(
                                    "id",
                                    operation.id,
                                ).put("position", position)
                                .put("type", operation.type.name)
                                .put("automatic", operation.automatic)
                                .put("available", operation.available)
                                .put("status", operation.status.name)
                                .put("effectApplied", operation.effectApplied)
                                .put("executionCount", operation.executionCount)
                                .put("reason", operation.reason),
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

            fun optionalString(
                source: JSONObject,
                key: String,
            ): String? {
                if (!source.has(key) || source.isNull(key)) return null
                return source.getString(key).also { require(it.isNotBlank()) }
            }

            fun strings(key: String) =
                buildSet {
                    val array = value.optJSONArray(key) ?: return@buildSet
                    repeat(array.length()) { add(array.getString(it)) }
                }
            val operations =
                buildList {
                    val array = value.getJSONArray("plan")
                    repeat(array.length()) { position ->
                        val item = array.getJSONObject(position)
                        require(item.getInt("position") == position)
                        add(
                            MediaOperation(
                                id = item.getString("id"),
                                type = MediaOperationType.valueOf(item.getString("type")),
                                automatic = item.getBoolean("automatic"),
                                status = MediaOperationStatus.valueOf(item.getString("status")),
                                available = item.optBoolean("available", true),
                                effectApplied = item.optBoolean("effectApplied", false),
                                executionCount = item.optInt("executionCount", 0),
                                reason = optionalString(item, "reason"),
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
            val baseline =
                value.optJSONObject("baseline")?.let { baseline ->
                    MediaSessionBaseline(
                        strings(
                            "baselinePlaying",
                        ),
                        strings("baselineKnown"),
                        baseline.getBoolean("present"),
                        optionalString(baseline, "package"),
                        MediaBaselinePlaybackState.valueOf(baseline.getString("state")),
                        optionalString(baseline, "title"),
                        optionalString(baseline, "artist"),
                        optionalString(baseline, "album"),
                        optionalString(baseline, "uri"),
                        optionalString(baseline, "sessionId"),
                        if (baseline.has("position") && !baseline.isNull("position")) baseline.getLong("position") else null,
                        baseline.getLong("capturedAt"),
                        MediaBaselineMetadataState.valueOf(baseline.getString("metadata")),
                    )
                } ?: MediaSessionBaseline(strings("baselinePlaying"), strings("baselineKnown"))
            if (baseline.capturedAtMillis < 0 || baseline.positionMillis?.let { it < 0 } == true ||
                (baseline.sessionPresent && baseline.packageName.isNullOrBlank()) ||
                (
                    !baseline.sessionPresent &&
                        (
                            baseline.packageName != null || baseline.sessionId != null || baseline.positionMillis != null ||
                                baseline.title != null ||
                                baseline.artist != null ||
                                baseline.album != null ||
                                baseline.mediaUri != null
                        )
                ) ||
                (!baseline.sessionPresent && baseline.playbackState != MediaBaselinePlaybackState.NONE) ||
                (!baseline.sessionPresent && baseline.metadataState != MediaBaselineMetadataState.ABSENT) ||
                (
                    baseline.sessionPresent && baseline.playbackState == MediaBaselinePlaybackState.PLAYING &&
                        baseline.sessionId.isNullOrBlank()
                )
            ) {
                return null
            }
            val stateVersion = value.getInt("stateVersion")
            if (stateVersion < 0) return null
            val operationId = optionalString(value, "operationId")
            val continuationCreated = value.optBoolean("continuationCreated", false)
            val continuationConsumed = value.getBoolean("continuationConsumed")
            val continuationKey = optionalString(value, "continuationKey")
            val active = operations.filter { it.status in ACTIVE_OPERATION_STATUSES }
            if (operations.map(MediaOperation::id).any(String::isBlank) ||
                operations.map(MediaOperation::id).distinct().size != operations.size ||
                operations.any {
                    it.executionCount < 0 || (it.effectApplied && it.executionCount == 0) ||
                        (!it.available && it.status != MediaOperationStatus.SKIPPED) ||
                        (
                            it.status in
                                setOf(
                                    MediaOperationStatus.EFFECT_APPLIED,
                                    MediaOperationStatus.AWAITING_OUTCOME,
                                ) && !it.effectApplied
                        )
                } || active.size > 1 ||
                (operationId != null && operations.none { it.id == operationId }) ||
                (active.isNotEmpty() && operationId != active.single().id) ||
                (continuationConsumed && !continuationCreated) ||
                (continuationCreated && continuationKey == null) ||
                (!continuationCreated && continuationKey != null) ||
                (state == MediaExecutionState.AWAIT_USER_LAUNCH && operationId == null) ||
                (state == MediaExecutionState.AWAIT_MANUAL_PLAY && !value.getBoolean("manualAssistanceShown")) ||
                (state == MediaExecutionState.AWAIT_MANUAL_PLAY && operations.none { it.type == MediaOperationType.MANUAL_ASSISTANCE })
            ) {
                return null
            }
            MediaExecutionCheckpoint(
                executionId = executionId,
                routineId = routineId,
                nodeId = nodeId,
                startedAtMillis = startedAt,
                automaticDeadlineMillis = automaticDeadline,
                globalDeadlineMillis = globalDeadline,
                state = state,
                stateVersion = stateVersion,
                operationId = operationId,
                continuationCreated = continuationCreated,
                continuationConsumed = continuationConsumed,
                continuationKey = continuationKey,
                manualGuidanceShown = value.getBoolean("manualAssistanceShown"),
                baseline = baseline,
                plan = MediaExecutionPlan(operations),
            )
        }.getOrNull()

    private val ACTIVE_OPERATION_STATUSES =
        setOf(
            MediaOperationStatus.RUNNING,
            MediaOperationStatus.EFFECT_APPLIED,
            MediaOperationStatus.AWAITING_OUTCOME,
        )
}
