package dev.munky.roguelike.common


fun String.snakeCase(): String {
    var str = ""
    var previous = '\u0000'
    for (c in this) {
        str += when {
            c.isUpperCase() && !previous.isLetter() -> c.lowercase()
            c.isUpperCase() -> "_${c.lowercase()}"
            c.isWhitespace() -> '_'
            else -> c
        }
        previous = c
    }
    return str
}

fun String.sentenceCase(): String {
    var str = ""
    var previous = '\u0000'
    for (c in this) {
        str += when {
            c.isLowerCase() && !previous.isLetter() -> c.uppercase()
            c in arrayOf('-', '.', '_') -> ' '
            c.isUpperCase() && previous.isLetter() -> " $c"
            else -> c
        }
        previous = c
    }
    return str
}