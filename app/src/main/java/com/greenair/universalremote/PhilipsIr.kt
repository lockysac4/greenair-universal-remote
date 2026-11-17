package com.greenair.universalremote

internal enum class PhilipsProtocol(val label: String) {
    RC6("RC6 · try first"),
    RC5("RC5 · alternative")
}

internal enum class PhilipsKey(val label: String, val rc6: Int, val rc5: Int = rc6) {
    POWER("Power", 0x0C),
    SOURCE("Source", 0x38),
    VOLUME_UP("Vol +", 0x10),
    VOLUME_DOWN("Vol −", 0x11),
    MUTE("Mute", 0x0D),
    CHANNEL_UP("Ch +", 0x20),
    CHANNEL_DOWN("Ch −", 0x21),
    MENU("Menu", 0x54, 0x52),
    BACK("Back", 0x0A),
    UP("↑", 0x58, 0x50),
    DOWN("↓", 0x59, 0x51),
    LEFT("←", 0x5A, 0x55),
    RIGHT("→", 0x5B, 0x56),
    OK("OK", 0x5C, 0x57)
}

internal object PhilipsIr {
    const val CARRIER_HZ = 36_000

    fun encode(protocol: PhilipsProtocol, command: Int, toggle: Boolean): IntArray {
        val levels = mutableListOf<Boolean>()

        fun bit(value: Boolean, markFirst: Boolean, width: Int = 1) {
            val first = value == markFirst
            repeat(width) { levels.add(first) }
            repeat(width) { levels.add(!first) }
        }

        fun bits(value: Int, count: Int, markFirst: Boolean) {
            for (shift in count - 1 downTo 0) {
                bit((value shr shift) and 1 != 0, markFirst)
            }
        }

        // Both profiles address the main TV device (address 0).
        val halfBit = when (protocol) {
            PhilipsProtocol.RC5 -> {
                require(command in 0..0x7F)
                bit(true, markFirst = false)
                bit(command < 0x40, markFirst = false)
                bit(toggle, markFirst = false)
                bits(0, 5, markFirst = false)
                bits(command and 0x3F, 6, markFirst = false)
                889
            }
            PhilipsProtocol.RC6 -> {
                require(command in 0..0xFF)
                repeat(6) { levels.add(true) }
                repeat(2) { levels.add(false) }
                bit(true, markFirst = true)
                bits(0, 3, markFirst = true)
                bit(toggle, markFirst = true, width = 2)
                bits(0, 8, markFirst = true)
                bits(command, 8, markFirst = true)
                444
            }
        }

        // Android takes alternating mark and space durations, starting with a mark.
        val pulses = mutableListOf<Int>()
        var previous = false
        for (level in levels.dropWhile { !it }) {
            if (level == previous) {
                pulses[pulses.lastIndex] += halfBit
            } else {
                pulses.add(halfBit)
                previous = level
            }
        }
        return pulses.toIntArray()
    }
}
