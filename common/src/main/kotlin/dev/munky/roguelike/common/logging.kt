package dev.munky.roguelike.common

import org.slf4j.Logger
import org.slf4j.LoggerFactory

fun logger(f: ()->Unit) : Logger {
    return logger(f::class.simpleName!!.sentenceCase())
}

fun logger(name: String) : Logger {
    return LoggerFactory.getLogger(name)
}