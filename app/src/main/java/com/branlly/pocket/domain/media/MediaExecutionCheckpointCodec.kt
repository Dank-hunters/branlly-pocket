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
            .put("attemptGeneration", checkpoint.attemptGeneration)
            .put("operationId", checkpoint.operationId)
            .put("continuationCreated", checkpoint.continuationCreated)
            .put("continuationConsumed", checkpoint.continuationConsumed)
            .put("continuationKey", checkpoint.continuationKey)
            .put("manualAssistanceShown", checkpoint.manualGuidanceShown)
            .put("directFailureNoticeShown", checkpoint.directFailureNoticeShown)
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
                    .put("metadata", checkpoint.baseline.metadataState.name)
                    .put(
                        "sessions",
                        JSONArray().apply {
                            checkpoint.baseline.sessions.forEach { session ->
                                put(
                                    JSONObject()
                                        .put("id", session.sessionId)
                                        .put("state", session.playbackState.name)
                                        .put("mediaId", session.content.mediaId)
                                        .put("queueItemId", session.content.activeQueueItemId)
                                        .put("title", session.content.title)
                                        .put("artist", session.content.artist)
                                        .put("album", session.content.album)
                                        .put("duration", session.content.durationMillis)
                                        .put("uri", session.content.mediaUri),
                                )
                            }
                        },
                    ),
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
                                .put("reason", operation.reason)
                                .put("commandedSessionId", operation.commandedSessionId)
                                .put("dispatchReserved", operation.dispatchReserved)
                                .put("dispatchFence", operation.dispatchFence.name)
                                .put("effectKey", operation.effectKey),
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
                        val type = MediaOperationType.valueOf(item.getString("type"))
                        val status = MediaOperationStatus.valueOf(item.getString("status"))
                        val effectApplied = item.optBoolean("effectApplied", false)
                        val executionCount = item.optInt("executionCount", 0)
                        val legacyPotentialDispatch =
                            !item.has("dispatchFence") && type == MediaOperationType.MEDIA_SESSION && executionCount > 0 &&
                                (effectApplied || status in ACTIVE_OPERATION_STATUSES)
                        val fence =
                            runCatching { MediaDispatchFence.valueOf(item.optString("dispatchFence")) }
                                .getOrElse {
                                    when {
                                        legacyPotentialDispatch -> MediaDispatchFence.OBSERVING
                                        item.optBoolean("dispatchReserved", false) -> MediaDispatchFence.RESERVED
                                        else -> MediaDispatchFence.OPEN
                                    }
                                }
                        add(
                            MediaOperation(
                                id = item.getString("id"),
                                type = type,
                                automatic = item.getBoolean("automatic"),
                                status = status,
                                available = item.optBoolean("available", true),
                                effectApplied = effectApplied || legacyPotentialDispatch,
                                executionCount = executionCount,
                                reason = optionalString(item, "reason"),
                                commandedSessionId = optionalString(item, "commandedSessionId"),
                                dispatchReserved = fence != MediaDispatchFence.OPEN,
                                dispatchFence = fence,
                                effectKey = optionalString(item, "effectKey"),
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
            val hasSessionEntries = value.optJSONObject("baseline")?.has("sessions") == true
            val decodedBaseline =
                value.optJSONObject("baseline")?.let { baseline ->
                    val sessions =
                        buildList {
                            val array = baseline.optJSONArray("sessions") ?: return@buildList
                            repeat(array.length()) { index ->
                                val item = array.getJSONObject(index)
                                add(
                                    MediaBaselineSession(
                                        sessionId = item.getString("id").takeIf(String::isNotBlank) ?: error("Blank session ID"),
                                        playbackState = MediaBaselinePlaybackState.valueOf(item.getString("state")),
                                        content =
                                            MediaContentFingerprint(
                                                mediaId = optionalString(item, "mediaId"),
                                                activeQueueItemId =
                                                    if (item.has("queueItemId") &&
                                                        !item.isNull("queueItemId")
                                                    ) {
                                                        item.getLong("queueItemId")
                                                    } else {
                                                        null
                                                    },
                                                title = optionalString(item, "title"),
                                                artist = optionalString(item, "artist"),
                                                album = optionalString(item, "album"),
                                                durationMillis =
                                                    if (item.has("duration") &&
                                                        !item.isNull("duration")
                                                    ) {
                                                        item.getLong("duration")
                                                    } else {
                                                        null
                                                    },
                                                mediaUri = optionalString(item, "uri"),
                                            ),
                                    ),
                                )
                            }
                        }
                    MediaSessionBaseline(
                        playingSessionIds = strings("baselinePlaying"),
                        knownSessionIds = strings("baselineKnown"),
                        sessionPresent = baseline.getBoolean("present"),
                        packageName = optionalString(baseline, "package"),
                        playbackState = MediaBaselinePlaybackState.valueOf(baseline.getString("state")),
                        title = optionalString(baseline, "title"),
                        artist = optionalString(baseline, "artist"),
                        album = optionalString(baseline, "album"),
                        mediaUri = optionalString(baseline, "uri"),
                        sessionId = optionalString(baseline, "sessionId"),
                        positionMillis =
                            if (baseline.has("position") &&
                                !baseline.isNull("position")
                            ) {
                                baseline.getLong("position")
                            } else {
                                null
                            },
                        capturedAtMillis = baseline.getLong("capturedAt"),
                        metadataState = MediaBaselineMetadataState.valueOf(baseline.getString("metadata")),
                        sessions = sessions,
                    )
                } ?: MediaSessionBaseline(strings("baselinePlaying"), strings("baselineKnown"))
            val baseline = decodedBaseline.withLegacySessionEntries(hasSessionEntries)
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
            val attemptGeneration = value.optInt("attemptGeneration", 0)
            if (stateVersion < 0 || attemptGeneration < 0) return null
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
                        (it.dispatchFence != MediaDispatchFence.OPEN && !it.dispatchReserved) ||
                        (
                            it.dispatchFence in setOf(MediaDispatchFence.DISPATCHED, MediaDispatchFence.OBSERVING) &&
                                it.status !in
                                setOf(
                                    MediaOperationStatus.EFFECT_APPLIED,
                                    MediaOperationStatus.AWAITING_OUTCOME,
                                    MediaOperationStatus.RUNNING,
                                )
                        ) ||
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
                attemptGeneration = attemptGeneration,
                operationId = operationId,
                continuationCreated = continuationCreated,
                continuationConsumed = continuationConsumed,
                continuationKey = continuationKey,
                manualGuidanceShown = value.getBoolean("manualAssistanceShown"),
                directFailureNoticeShown = value.optBoolean("directFailureNoticeShown", false),
                baseline = baseline,
                plan = MediaExecutionPlan(operations),
            )
        }.getOrNull()

    private fun MediaSessionBaseline.withLegacySessionEntries(hasSessionEntries: Boolean): MediaSessionBaseline {
        if (hasSessionEntries || sessions.isNotEmpty() || knownSessionIds.isEmpty()) return this
        return copy(
            sessions =
                knownSessionIds.map { id ->
                    MediaBaselineSession(
                        sessionId = id,
                        playbackState =
                            when {
                                id == sessionId -> playbackState
                                id in playingSessionIds -> MediaBaselinePlaybackState.PLAYING
                                else -> MediaBaselinePlaybackState.UNKNOWN
                            },
                        content =
                            if (id == sessionId) {
                                MediaContentFingerprint(
                                    title = title,
                                    artist = artist,
                                    album = album,
                                    mediaUri = mediaUri,
                                )
                            } else {
                                MediaContentFingerprint()
                            },
                    )
                },
        )
    }

    private val ACTIVE_OPERATION_STATUSES =
        setOf(
            MediaOperationStatus.RUNNING,
            MediaOperationStatus.EFFECT_APPLIED,
            MediaOperationStatus.AWAITING_OUTCOME,
        )
}
