package de.westnordost.streetcomplete.ui.common.quest

import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuestFormKeyboardShortcutsTest {
    @Test fun `digit mapping accepts num pad`() {
        assertEquals(0, digitIndexFromKey(Key.One))
        assertEquals(0, digitIndexFromKey(Key.NumPad1))
        assertEquals(4, digitIndexFromKey(Key.Five))
        assertEquals(4, digitIndexFromKey(Key.NumPad5))
    }

    @Test fun `numbered options use visible order only`() {
        val picked = mutableListOf<Int>()
        val layers = listOf(
            QuestFormKeyboardLayerState(
                numberedOptions = { listOf({ picked += 1 }, { picked += 2 }, { picked += 3 }) },
            ),
        )
        assertTrue(dispatchQuestFormKeyboardShortcut(layers, hasTextInputFocus = false, Key.NumPad3))
        assertEquals(listOf(3), picked)
    }

    @Test fun `top layer wins for digits`() {
        val picked = mutableListOf<String>()
        val layers = listOf(
            QuestFormKeyboardLayerState(numberedOptions = { listOf({ picked += "bottom" }) }),
            QuestFormKeyboardLayerState(numberedOptions = { listOf({ picked += "top" }) }),
        )
        assertTrue(dispatchQuestFormKeyboardShortcut(layers, false, Key.One))
        assertEquals(listOf("top"), picked)
    }

    @Test fun `L invokes last picked when offered`() {
        var lastPickedCalled = false
        val layers = listOf(
            QuestFormKeyboardLayerState(onLastPicked = { lastPickedCalled = true }),
        )
        assertTrue(dispatchQuestFormKeyboardShortcut(layers, false, Key.L))
        assertTrue(lastPickedCalled)
    }

    @Test fun `L noop when no handler`() {
        assertFalse(dispatchQuestFormKeyboardShortcut(emptyList(), false, Key.L))
    }

    @Test fun `Y and N only for binary layer`() {
        var yes = false
        var no = false
        val layers = listOf(
            QuestFormKeyboardLayerState(
                binaryYesNo = QuestFormBinaryYesNo(onYes = { yes = true }, onNo = { no = true }),
            ),
        )
        assertTrue(dispatchQuestFormKeyboardShortcut(layers, false, Key.Y))
        assertTrue(yes)
        assertFalse(no)
        assertTrue(dispatchQuestFormKeyboardShortcut(layers, false, Key.N))
        assertTrue(no)
    }

    @Test fun `enter submits only when allowed`() {
        var submits = 0
        val layers = listOf(
            QuestFormKeyboardLayerState(
                onSubmit = { submits++ },
                canSubmit = { submits == 0 },
            ),
        )
        assertTrue(dispatchQuestFormKeyboardShortcut(layers, false, Key.Enter))
        assertEquals(1, submits)
        assertFalse(dispatchQuestFormKeyboardShortcut(layers, false, Key.Enter))
    }

    @Test fun `enter blocked while text input focused`() {
        var submits = 0
        val layers = listOf(
            QuestFormKeyboardLayerState(onSubmit = { submits++ }, canSubmit = { true }),
        )
        assertFalse(dispatchQuestFormKeyboardShortcut(layers, hasTextInputFocus = true, Key.Enter))
        assertEquals(0, submits)
    }

    @Test fun `digits blocked while text input focused`() {
        val layers = listOf(
            QuestFormKeyboardLayerState(numberedOptions = { listOf({}) }),
        )
        assertFalse(dispatchQuestFormKeyboardShortcut(layers, hasTextInputFocus = true, Key.One))
    }

    @Test fun `layer suppress flag blocks shortcuts`() {
        val layers = listOf(
            QuestFormKeyboardLayerState(
                numberedOptions = { listOf({}) },
                suppressShortcuts = { true },
            ),
        )
        assertFalse(dispatchQuestFormKeyboardShortcut(layers, false, Key.One))
    }

    @Test fun `escape on dialog layer does not fall through`() {
        var dialogClosed = false
        val layers = listOf(
            QuestFormKeyboardLayerState(),
            QuestFormKeyboardLayerState(onEscape = { dialogClosed = true }),
        )
        assertTrue(dispatchQuestFormKeyboardShortcut(layers, false, Key.Escape))
        assertTrue(dialogClosed)
    }
}