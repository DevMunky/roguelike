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
    }
}

inline fun <S, F, T> Result<S, F>.mapSuccess(transform: (S) -> T): Result<T, F> = xmap(transform) { it }

inline fun <S, F, T> Result<S, F>.mapFailure(transform: (F) -> T): Result<S, T> = xmap({ it }, transform)

inline fun <S, F> Result<S, F>.handleFailure(handle: (F) -> Nothing): S = when (this) {
    is Success -> value
    is Failure -> handle(reason)
}

inline fun <S, F, T> Result<S, F>.fold(success: (S) -> T, failure: (F) -> T) = when (this) {
    is Success -> success(value)
    is Failure -> failure(reason)
}

inline fun <S, F, SS, FF> Result<S, F>.xmap(
    success: (S) -> SS,
    failure: (F) -> FF
): Result<SS, FF> = when (this) {
    is Success -> Success(success(value))
    is Failure -> Failure(failure(reason))
}

fun <S, F> S.toSuccess() = Result.success<S, F>(this)
fun <S, F> F.toFailure() = Result.failure<S, F>(this)