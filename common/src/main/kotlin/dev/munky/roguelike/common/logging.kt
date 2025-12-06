package dev.munky.roguelike.common

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.pattern.CompositeConverter
import ch.qos.logback.core.pattern.color.ANSIConstants
import org.slf4j.Logger
import org.slf4j.LoggerFactory


fun logger(f: ()->Unit) : Logger {
    return logger(f::class.simpleName!!.split("$").first())
}

fun logger(name: String) : Logger {
    return LoggerFactory.getLogger(name)
}

class LogbackLevelString : CompositeConverter<ILoggingEvent>() {
    override fun transform(event: ILoggingEvent, `in`: String): String {
        val str = when (event.level) {
            Level.ERROR -> " E "
            Level.WARN -> " W "
            Level.INFO -> " I "
            Level.DEBUG -> " D "
            Level.TRACE -> " T "
            else -> " ? "
        }
        return ANSIConstants.ESC_START + getAnsi(event) + ANSIConstants.ESC_END + str + SET_DEFAULT_COLOR
    }

    fun getAnsi(event: ILoggingEvent) : String = when (event.level) {
        Level.ERROR -> FG + ansiRgb(210, 210, 210) + ";$BG" + ansiRgb(180, 20, 20)
        Level.WARN  -> "$FG$BLACK;$BG" + ansiRgb(220, 200, 20)
        Level.INFO  -> "$FG$BLACK;$BG" + ansiRgb(0, 100, 180)
        Level.DEBUG -> "$FG$BLACK;$BG" + ansiRgb(0, 180, 100)
        Level.TRACE -> "$FG$BLACK;$BG" + ansiRgb(0, 0, 0)
        else -> ANSIConstants.RESET + ANSIConstants.DEFAULT_FG
    }

    companion object {
        const val FG = "38;"
        const val BG = "48;"
        const val BLACK = "5;0"
        const val SET_DEFAULT_COLOR: String = ANSIConstants.ESC_START + ANSIConstants.RESET + ANSIConstants.DEFAULT_FG + ANSIConstants.ESC_END

        fun ansiRgb(r: Int, g: Int, b: Int) = "2;$r;$g;$b"
    }
}