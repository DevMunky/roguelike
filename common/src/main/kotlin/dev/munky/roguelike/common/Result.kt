package dev.munky.roguelike.common

import dev.munky.roguelike.common.Result.Failure
import dev.munky.roguelike.common.Result.Success

sealed interface Result<S, F> {
    @JvmInline
    value class Success<S, F>(val value: S) : Result<S, F>
    @JvmInline
    value class Failure<S, F>(val reason: F) : Result<S, F>

    fun asFailure() = this as? Failure<S, F>
    fun asSuccess() = this as? Success<S, F>

    companion object {
        fun <S, F> success(value: S) = Success<S, F>(value)
        fun <S, F> failure(reason: F) = Failure<S, F>(reason)

        /**
         * Inlined to reduce lambda allocations.
         */
        private inline fun <S, SS, F, FF> xmap(result: Result<S, F>, success: (S) -> SS, failure: (F) -> FF): Result<SS, FF> = when (result) {
            is Success -> Success(success(result.value))
            is Failure -> Failure(failure(result.reason))
        }
    }
}

inline fun <S, F, T> Result<S, F>.mapSuccess(transform: (S) -> T): Result<T, F> = xmap(transform) { it }

inline fun <S, F, T> Result<S, F>.mapFailure(transform: (F) -> T): Result<S, T> = xmap({ it }, transform)

inline fun <S, F, SS, FF> Result<S, F>.xmap(
    success: (S) -> SS,
    failure: (F) -> FF
): Result<SS, FF> = when (this) {
    is Success -> Success(success(value))
    is Failure -> Failure(failure(reason))
}

fun <S, F> S.toSuccess() = Result.success<S, F>(this)
fun <S, F> F.toFailure() = Result.failure<S, F>(this)