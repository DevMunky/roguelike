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

/**
 * From [https://rosettacode.org/wiki/Levenshtein_distance#Kotlin][A]
 */
fun levenshtein(s: String, t: String): Int {
    // degenerate cases
    if (s == t)  return 0
    if (s == "") return t.length
    if (t == "") return s.length

    // create two integer arrays of distances and initialize the first one
    val v0 = IntArray(t.length + 1) { it }  // previous
    val v1 = IntArray(t.length + 1)         // current

    var cost: Int
    for (i in 0 until s.length) {
        // calculate v1 from v0
        v1[0] = i + 1
        for (j in 0 until t.length) {
            cost = if (s[i] == t[j]) 0 else 1
            v1[j + 1] = (v1[j] + 1)
                .coerceAtMost(
                    (v0[j + 1] + 1)
                        .coerceAtMost(v0[j] + cost)
                )
        }
        // copy v1 to v0 for next iteration
        for (j in 0 .. t.length) v0[j] = v1[j]
    }
    return v1[t.length]
}