package com.github.lppedd.cc.whatsnew

import com.github.lppedd.cc.CCBundle
import com.github.lppedd.cc.api.WHATS_NEW_EP
import com.github.lppedd.cc.api.WhatsNewProvider
import com.github.lppedd.cc.setFocused
import com.github.lppedd.cc.setName
import com.github.lppedd.cc.ui.NoContentTabbedPaneWrapper
import com.intellij.CommonBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper.DialogStyle.COMPACT
import com.intellij.util.ui.JBUI.Borders
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JComponent

/**
 * A dialog which display changelogs using [WhatsNewProvider]s,
 * each rendered as a separate tab.
 *
 * @author Edoardo Luppi
 */
internal class WhatsNewDialog(project: Project) : CCDialogWrapper(project) {
  private val whatsNewPanel = WhatsNewPanel()
  private val olderAction = OlderAction()
  private val newerAction = NewerAction()
  private val tabSelectedHandlers = mutableMapOf<Int, () -> Unit>()

  init {
    isModal = false
    title = CCBundle["cc.whatsnew.title"]
    setCancelButtonText(CommonBundle.getCloseButtonText())
    setDoNotAskOption(whatsNewPanel)
    init()
  }

  override fun createNorthPanel(): JComponent {
    val tabbedPane = NoContentTabbedPaneWrapper(myDisposable).also {
      it.addChangeListener { _ -> tabSelectedHandlers[it.selectedIndex]?.invoke() }
    }

    WHATS_NEW_EP.extensions
      .filter { it.files.fileDescriptions.isNotEmpty() }
      .forEach { provider ->
        tabSelectedHandlers[tabbedPane.tabCount] = {
          whatsNewPanel.setProvider(provider)
          updateActions()
        }

        tabbedPane.addTab(provider.displayName())
      }

    return tabbedPane.component
  }

  override fun createCenterPanel(): JComponent =
    whatsNewPanel

  override fun createSouthPanel(): JComponent =
    super.createSouthPanel().also {
      it.border = Borders.empty(8, 12)
    }

  override fun createActions(): Array<Action> =
    arrayOf(olderAction, newerAction, cancelAction)

  override fun getStyle(): DialogStyle =
    COMPACT

  override fun getDimensionServiceKey(): String =
    "#com.github.lppedd.cc.WhatsNewDialog"

  override fun getPreferredFocusedComponent(): JComponent? =
    myPreferredFocusedComponent

  private fun updateActions() {
    val hasNewer = whatsNewPanel.hasNewer()
    newerAction.isEnabled = hasNewer

    if (hasNewer) {
      val newerVersion = whatsNewPanel.newerVersion()

      if (newerVersion == null) {
        newerAction.setName(CCBundle["cc.whatsnew.dialog.newer"])
      } else {
        newerAction.setName("${CCBundle["cc.whatsnew.dialog.newer"]} - $newerVersion")
      }
    } else {
      whatsNewPanel.currentVersion()?.let {
        newerAction.setName("${CCBundle["cc.whatsnew.dialog.newer"]} - $it")
      }
    }

    val hasOlder = whatsNewPanel.hasOlder()
    olderAction.isEnabled = hasOlder

    if (hasOlder) {
      val olderVersion = whatsNewPanel.olderVersion()

      if (olderVersion == null) {
        olderAction.setName(CCBundle["cc.whatsnew.dialog.older"])
      } else {
        olderAction.setName("${CCBundle["cc.whatsnew.dialog.older"]} - $olderVersion")
      }
    }
  }

  companion object {
    private var sharedDialog: WhatsNewDialog? = null

    fun showForProject(project: Project) {
      var dialog = sharedDialog

      if (dialog != null && dialog.isVisible) {
        dialog.dispose()
      }


      dialog = WhatsNewDialog(project)
      sharedDialog = dialog
      dialog.show()
    }
  }

  private inner class OlderAction : AbstractAction(CCBundle["cc.whatsnew.dialog.older"]) {
    init {
      setFocused()
    }

    override fun actionPerformed(actionEvent: ActionEvent) {
      whatsNewPanel.olderChangelog()
      updateActions()
    }
  }

  private inner class NewerAction : AbstractAction(CCBundle["cc.whatsnew.dialog.newer"]) {
    override fun actionPerformed(actionEvent: ActionEvent) {
      whatsNewPanel.newerChangelog()
      updateActions()
    }
  }
}