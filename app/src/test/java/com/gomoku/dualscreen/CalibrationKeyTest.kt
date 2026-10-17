package com.gomoku.dualscreen

import org.junit.Assert.*
import org.junit.Test

/** Regression cover for the 20261015 firmware that stopped exposing vendor.nl.screen_mode. */
class CalibrationKeyTest {
    @Test fun missingScreenModeStillProducesCalibratableState() {
        val key = CalibrationKey.of("0", null, listOf(0 to 0, 2 to 2))
        assertEquals("0:?:0=0,2=2", key)
        // The old implementation rejected any key containing "null", which blocked calibration
        // entirely on firmware builds without the shape property.
        assertFalse(key.contains("null"))
        assertTrue(CalibrationKey.saveable(key))
    }

    @Test fun emptyOrUnknownScreenModeIsTreatedAsUnknownShape() {
        assertEquals("?", CalibrationKey.shapeOf(null))
        assertEquals("?", CalibrationKey.shapeOf(""))
        assertEquals("?", CalibrationKey.shapeOf("garbage"))
        assertFalse(CalibrationKey.shapeKnown(""))
        assertEquals("0", CalibrationKey.shapeOf("0"))
        assertEquals("-1", CalibrationKey.shapeOf("-1"))
        assertEquals("1", CalibrationKey.shapeOf("1"))
        assertTrue(CalibrationKey.shapeKnown("1"))
    }

    @Test fun knownShapeKeepsItsOwnStateKey() {
        assertEquals("1:0:0=0,2=2", CalibrationKey.of("1", "0", listOf(0 to 0, 2 to 2)))
        // Unknown shape must not collide with a known shape entry.
        assertNotEquals(
            CalibrationKey.of("0", null, listOf(0 to 0, 2 to 2)),
            CalibrationKey.of("0", "-1", listOf(0 to 0, 2 to 2))
        )
    }

    @Test fun unreadableMasterIsNotSaveable() {
        assertFalse(CalibrationKey.saveable(""))
        assertFalse(CalibrationKey.saveable("?:?:0=0,2=2"))
        assertFalse(CalibrationKey.saveable("null:?:0=0,2=2"))
        assertTrue(CalibrationKey.saveable("1:?:0=0,2=2"))
    }

    @Test fun rotationChangeProducesDifferentState() {
        assertNotEquals(
            CalibrationKey.of("0", null, listOf(0 to 0, 2 to 2)),
            CalibrationKey.of("0", null, listOf(0 to 0, 2 to 0))
        )
    }
}
