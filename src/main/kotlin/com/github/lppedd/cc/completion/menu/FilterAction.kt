package com.github.lppedd.cc.completion.menu

import com.github.lppedd.cc.ICON_DISABLED
import com.github.lppedd.cc.api.CommitTokenProvider
import com.github.lppedd.cc.completion.FilterPrefixMatcher
import com.github.lppedd.cc.lookupElement.CommitLookupElement
import com.intellij.codeInsight.completion.PlainPrefixMatcher
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import java.util.*

/**
 * @author Edoardo Luppi
 */
internal class FilterAction(
    private val enhancer: MenuEnhancerLookupListener,
    private val lookup: LookupImpl,
    private val provider: CommitTokenProvider,
) : AnAction(provider.getPresentation().name) {
  private var isFiltered = false
  private var backupItems = emptyList<CommitLookupElement>()

  fun filterItems(doFilter: Boolean) {
    isFiltered = doFilter

    if (doFilter) {
      removeLookupItems()
    } else {
      reinstallLookupItems()
    }
  }

  fun reset() {
    if (isFiltered) {
      filterItems(false)
    }
  }

  private fun removeLookupItems() {
    val arranger = lookup.arranger

    backupItems = lookup.items
      .asSequence()
      .filterIsInstance<CommitLookupElement>()
      .filter { provider.getId() == it.provider.getId() }
      .onEach {
        val delegatePrefixMatcher = arranger.itemMatcher(it)
        val newPrefixMatcher = FilterPrefixMatcher(delegatePrefixMatcher)
        arranger.registerMatcher(it, newPrefixMatcher)
        it.valid = false
      }.toList()
  }

  private fun reinstallLookupItems() {
    val arranger = lookup.arranger

    arranger.matchingItems.firstOrNull()?.let {
      arranger.prefixReplaced(lookup, arranger.itemMatcher(it).prefix)
    }

    backupItems.forEach {
      it.valid = true
      val delegate = arranger.itemMatcher(it)
      val newPrefixMatcher = PlainPrefixMatcher(delegate.prefix)
      arranger.registerMatcher(it, newPrefixMatcher)
      lookup.addItem(it, newPrefixMatcher)
    }

    backupItems = emptyList()
  }

  override fun actionPerformed(ignored: AnActionEvent) {
    if (enhancer.filterSelected(this)) {
      filterItems(!isFiltered)
      lookup.resort(false)
    }
  }

  override fun update(e: AnActionEvent) {
    e.presentation.icon = if (isFiltered) {
      ICON_DISABLED
    } else {
      provider.getPresentation().icon
    }
  }

  override fun equals(other: Any?): Boolean =
    other is FilterAction && provider.getId() == other.provider.getId()

  override fun hashCode(): Int =
    Objects.hashCode(provider.getId())
}