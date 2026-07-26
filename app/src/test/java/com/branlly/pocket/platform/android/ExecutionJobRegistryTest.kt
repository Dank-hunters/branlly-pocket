package com.branlly.pocket.platform.android

import kotlinx.coroutines.Job
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExecutionJobRegistryTest {
    @Test
    fun `duplicate command cannot replace or remove active execution job`() {
        val registry = ExecutionJobRegistry()
        val activeJob = Job()
        val duplicateJob = Job()

        assertTrue(registry.register("execution-1", activeJob))
        assertFalse(registry.register("execution-1", duplicateJob))

        registry.complete("execution-1", duplicateJob)

        assertTrue(registry.cancel("execution-1"))
        assertTrue(activeJob.isCancelled)
        assertFalse(duplicateJob.isCancelled)
    }

    @Test
    fun `completed owner is no longer cancellable`() {
        val registry = ExecutionJobRegistry()
        val activeJob = Job()

        assertTrue(registry.register("execution-1", activeJob))
        registry.complete("execution-1", activeJob)

        assertFalse(registry.cancel("execution-1"))
        assertFalse(activeJob.isCancelled)
    }
}
