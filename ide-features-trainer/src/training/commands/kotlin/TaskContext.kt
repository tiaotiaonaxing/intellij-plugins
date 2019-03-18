package training.commands.kotlin

import com.intellij.find.FindManager
import com.intellij.find.FindResult
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.undo.BasicUndoableAction
import com.intellij.openapi.command.undo.DocumentReferenceManager
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ex.EditorGutterComponentEx
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.testGuiFramework.framework.GuiTestUtil
import com.intellij.util.DocumentUtil
import org.jdom.input.SAXBuilder
import training.check.Check
import training.learn.ActionsRecorder
import training.learn.lesson.LessonManager
import training.learn.lesson.kimpl.KLesson
import training.ui.Message
import java.util.concurrent.CompletableFuture

class TaskContext(val lesson: KLesson, val editor: Editor, val project: Project,
                  private val recorder: ActionsRecorder) {
  val steps : MutableList<CompletableFuture<Boolean>> = mutableListOf()

  val testActions: MutableList<Runnable> = mutableListOf()

  /**
   * Write a text to the learn panel (panel with a learning tasks).
   */
  fun text(text: String) {
    val wrappedText = "<root><text>$text</text></root>"
    val textAsElement = SAXBuilder().build(wrappedText.byteInputStream()).rootElement.getChild("text")
    LessonManager.getInstance(this.lesson).addMessages(Message.convert(textAsElement)) //support old format
  }

  fun setDocumentCode(code: String) {
    ApplicationManager.getApplication().invokeAndWait {
      val document = editor.document
      DocumentUtil.writeInRunUndoTransparentAction {
        val documentReference = DocumentReferenceManager.getInstance().create(document)
        UndoManager.getInstance(project).nonundoableActionPerformed(documentReference, false)
        document.replaceString(0, document.textLength, code)
      }
      PsiDocumentManager.getInstance(project).commitDocument(document)
      doUndoableAction(project)
      updateGutter(editor)
    }
  }

  fun caret(offset: Int) {
    runInEdt { editor.caretModel.moveToOffset(offset) }
  }

  fun caret(line: Int, column: Int) {
    runInEdt { editor.caretModel.moveToLogicalPosition(LogicalPosition(line - 1, column - 1)) }
  }

  fun caret(text: String) {
    runInEdt {
      val start = getStartOffsetForText(text, editor, project)
      editor.caretModel.moveToOffset(start.startOffset)
    }
  }

  /** Simply wait until an user perform particular action */
  fun trigger(actionId: String) {
    steps.add(recorder.futureAction(actionId))
    testActions(actionId)
  }

  /** An user need to rice an action which leads to necessary state change */
  fun <T : Any> trigger(actionId: String, calculateState: () -> T, checkState: (T, T) -> Boolean) {
    val recorder = ActionsRecorder(project, editor.document)
    LessonManager.getInstance(lesson).registerActionsRecorder(recorder)
    val check = getCheck(calculateState, checkState)
    steps.add(recorder.futureActionAndCheckAround(actionId, check))
    testActions(actionId)
  }

  /** An user need to rice an action which leads to appropriate end state */
  fun trigger(actionId: String, checkState: () -> Boolean) {
    trigger(actionId, { Unit }, { _, _ -> checkState() })
  }

  fun testActions(vararg actionIds: String) {
    testActions.add(Runnable {
      val app = ApplicationManager.getApplication()
      for (actionId in actionIds) {
        val action = ActionManager.getInstance().getAction(actionId) ?: error("Action $actionId is non found")
        DataManager.getInstance().dataContextFromFocusAsync.onSuccess { dataContext ->
          app.invokeAndWait {
            val event = AnActionEvent.createFromAnAction(action, null, ActionPlaces.UNKNOWN, dataContext)
            @Suppress("MissingRecentApi") // used for debug
            ActionUtil.performActionDumbAwareWithCallbacks(action, event, dataContext)
          }
        }
      }
    })
  }

  /** Check that IDE state have been changed by single action as expected */
  fun <T : Any> changeCheck(calculateState: () -> T, checkState: (T, T) -> Boolean) {
    val check = getCheck(calculateState, checkState)
    steps.add(recorder.futureCheck { check.check() })
  }

  /** Check that IDE state is as expected */
  fun stateCheck(checkState: () -> Boolean) {
    steps.add(recorder.futureCheck { checkState() })
  }

  private fun <T : Any> getCheck(calculateState: () -> T, checkState: (T, T) -> Boolean) : Check {
    return object : Check {
      var state: T? = null

      override fun before() {
        state = calculateAction()
      }

      override fun check(): Boolean = checkState(state!!, calculateAction())

      override fun set(project: Project?, editor: Editor?) {
        // do nothing
      }

      override fun listenAllKeys(): Boolean = false

      // Some checks are needed to be performed in EDT thread
      // For example, selection information  could not be got (for some magic reason) from another thread
      // Also we need to commit document
      private fun calculateAction() = WriteAction.computeAndWait<T, RuntimeException> {
        PsiDocumentManager.getInstance(project).commitDocument(editor.document)
        calculateState()
      }
    }
  }

  fun typeForTest(text : String) {
    testAction { GuiTestUtil.typeText(text) }
  }

  fun testAction(action: () -> Unit) {
    testActions.add(Runnable {
      action()
    })
  }

  fun action(id: String): String {
    return "<action>$id</action>"
  }

  private fun getStartOffsetForText(text: String, editor: Editor, project: Project): FindResult {
    val document = editor.document

    val findManager = FindManager.getInstance(project)
    val model = findManager.findInFileModel.clone()
    model.isGlobal = false
    model.isReplaceState = false
    model.stringToFind = text
    return FindManager.getInstance(project).findString(document.charsSequence, 0, model)
  }

  private fun doUndoableAction(project: Project) {
    CommandProcessor.getInstance().executeCommand(project, {
      UndoManager.getInstance(project).undoableActionPerformed(object : BasicUndoableAction() {
        override fun undo() {}
        override fun redo() {}
      })
    }, null, null)
  }

  private fun updateGutter(editor: Editor) {
    val editorGutterComponentEx = editor.gutter as EditorGutterComponentEx
    editorGutterComponentEx.revalidateMarkup()
  }

  private inline fun runInEdt(crossinline runnable: () -> Unit) {
    val app = ApplicationManager.getApplication()
    if (app.isDispatchThread) {
      runnable()
    } else {
      app.invokeLater({ runnable() }, ModalityState.defaultModalityState())
    }
  }

  companion object {
    @Volatile var inTestMode : Boolean = false
  }
}