package com.nshiftui

class NShiftRuntimePreconditionFailure(message: String) : IllegalStateException(message)

internal object NShiftRuntimePrecondition {
    @Volatile
    private var handler: (String) -> Nothing = { message ->
        throw NShiftRuntimePreconditionFailure(message)
    }

    fun replace(replacement: (String) -> Nothing) {
        handler = replacement
    }

    fun restore() {
        handler = { message ->
            throw NShiftRuntimePreconditionFailure(message)
        }
    }

    fun raise(message: String): Nothing = handler(message)

    fun check(condition: Boolean, message: String) {
        if (!condition) {
            raise(message)
        }
    }
}
