package com.github.lppedd.cc.configuration

import com.github.lppedd.cc.CC
import com.github.lppedd.cc.api.*
import com.github.lppedd.cc.configuration.CCConfigService.PresentableNameGetter
import com.github.lppedd.cc.vcs.RecentCommitTokenProvider
import com.github.lppedd.cc.vcs.VcsCommitTokenProvider
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Transient
import com.intellij.util.xmlb.annotations.XMap
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.Map.Entry
import kotlin.math.min

/**
 * @author Edoardo Luppi
 */
@State(
  name = "general",
  storages = [Storage(CC.Settings.File)],
  presentableName = PresentableNameGetter::class,
)
internal class CCConfigService : PersistentStateComponent<CCConfigService> {
  @Attribute
  private var version: Int = 0

  var completionType: CompletionType = CompletionType.POPUP
  var providerFilterType: ProviderFilterType = ProviderFilterType.HIDE_SELECTED
  var customFilePath: String? = null
  var customCoAuthorsFilePath: String? = null
  var scopeReplaceChar: String = "-"

  @XMap(
    propertyElementName = "commitTypes",
    keyAttributeName = "providerId",
    valueAttributeName = "order"
  )
  private var typeProvidersMap: MutableMap<String, Int> = ConcurrentHashMap<String, Int>()

  @XMap(
    propertyElementName = "commitScopes",
    keyAttributeName = "providerId",
    valueAttributeName = "order"
  )
  private var scopeProvidersMap: MutableMap<String, Int> = ConcurrentHashMap<String, Int>()

  @XMap(
    propertyElementName = "commitSubjects",
    keyAttributeName = "providerId",
    valueAttributeName = "order"
  )
  private var subjectProvidersMap: MutableMap<String, Int> = ConcurrentHashMap<String, Int>()

  @XMap(
    propertyElementName = "commitBodies",
    keyAttributeName = "providerId",
    valueAttributeName = "order"
  )
  private var bodyProvidersMap: MutableMap<String, Int> = ConcurrentHashMap<String, Int>()

  @XMap(
    propertyElementName = "commitFooterTypes",
    keyAttributeName = "providerId",
    valueAttributeName = "order"
  )
  private var footerTypeProvidersMap: MutableMap<String, Int> = ConcurrentHashMap<String, Int>()

  @XMap(
    propertyElementName = "commitFooterValues",
    keyAttributeName = "providerId",
    valueAttributeName = "order"
  )
  private var footerValueProvidersMap: MutableMap<String, Int> = ConcurrentHashMap<String, Int>()

  init {
    noStateLoaded()
  }

  fun getProviderOrder(provider: CommitTypeProvider) =
    typeProvidersMap.computeIfAbsent(provider.getId()) { typeProvidersMap.size }

  fun getProviderOrder(provider: CommitScopeProvider) =
    scopeProvidersMap.computeIfAbsent(provider.getId()) { scopeProvidersMap.size }

  fun getProviderOrder(provider: CommitSubjectProvider) =
    subjectProvidersMap.computeIfAbsent(provider.getId()) { subjectProvidersMap.size }

  fun getProviderOrder(provider: CommitBodyProvider) =
    bodyProvidersMap.computeIfAbsent(provider.getId()) { bodyProvidersMap.size }

  fun getProviderOrder(provider: CommitFooterTypeProvider) =
    footerTypeProvidersMap.computeIfAbsent(provider.getId()) { footerTypeProvidersMap.size }

  fun getProviderOrder(provider: CommitFooterValueProvider) =
    footerValueProvidersMap.computeIfAbsent(provider.getId()) { footerValueProvidersMap.size }

  fun setTypeProvidersOrder(typeProvidersMap: Map<String, Int>) {
    this.typeProvidersMap = ConcurrentHashMap(typeProvidersMap)
  }

  fun setScopeProvidersOrder(scopeProvidersMap: Map<String, Int>) {
    this.scopeProvidersMap = ConcurrentHashMap(scopeProvidersMap)
  }

  fun setSubjectProvidersOrder(subjectProvidersMap: Map<String, Int>) {
    this.subjectProvidersMap = ConcurrentHashMap(subjectProvidersMap)
  }

  fun setBodyProvidersOrder(bodyProvidersMap: Map<String, Int>) {
    this.bodyProvidersMap = ConcurrentHashMap(bodyProvidersMap)
  }

  fun setFooterTypeProvidersOrder(footerTypeProvidersMap: Map<String, Int>) {
    this.footerTypeProvidersMap = ConcurrentHashMap(footerTypeProvidersMap)
  }

  fun setFooterValueProvidersOrder(footerValueProvidersMap: Map<String, Int>) {
    this.footerValueProvidersMap = ConcurrentHashMap(footerValueProvidersMap)
  }

  override fun getState() =
    this

  override fun loadState(state: CCConfigService) {
    XmlSerializerUtil.copyBean(state, this)
    noStateLoaded()
  }

  override fun noStateLoaded() {
    typeProvidersMap.putIfAbsent(RecentCommitTokenProvider.ID, 0)
    typeProvidersMap.putIfAbsent(DefaultCommitTokenProvider.ID, 1)
    typeProvidersMap.putIfAbsent(VcsCommitTokenProvider.ID, 2)

    scopeProvidersMap.putIfAbsent(RecentCommitTokenProvider.ID, 0)
    scopeProvidersMap.putIfAbsent(DefaultCommitTokenProvider.ID, 1)
    scopeProvidersMap.putIfAbsent(VcsCommitTokenProvider.ID, 2)

    subjectProvidersMap.putIfAbsent(RecentCommitTokenProvider.ID, 0)
    subjectProvidersMap.putIfAbsent(VcsCommitTokenProvider.ID, 1)

    footerTypeProvidersMap.putIfAbsent(DefaultCommitTokenProvider.ID, 0)

    footerValueProvidersMap.putIfAbsent(RecentCommitTokenProvider.ID, 0)
    footerValueProvidersMap.putIfAbsent(DefaultCommitTokenProvider.ID, 1)
    footerValueProvidersMap.putIfAbsent(VcsCommitTokenProvider.ID, 2)
  }

  override fun initializeComponent() {
    // For reference this method is called only when the service
    // is requested the first time. There is no eager loading (well,
    // in newer IDEA versions "preload" exists but it's better to
    // not rely on it) so the updates below are applied only if a user
    // actually uses completion or opens the settings panel.
    // TODO: think about a better strategy to update settings as soon
    //  as the IDE starts but without nagging/scaring the user with
    //  the ConverterProvider's popup. And maybe without having to deal
    //  with XML itself
    if (version < 1) {
      // 0.17.0
      updateProvidersOrdering()
      version++
    }

    check(version <= CURRENT_VERSION) {
      "Configuration version can't be $version. Latest is $CURRENT_VERSION"
    }

    version = CURRENT_VERSION
  }

  private fun updateProvidersOrdering() {
    updateProviderOrder(typeProvidersMap, RecentCommitTokenProvider.ID, 0)
    updateProviderOrder(scopeProvidersMap, RecentCommitTokenProvider.ID, 0)
    updateProviderOrder(subjectProvidersMap, RecentCommitTokenProvider.ID, 0)
    updateProviderOrder(footerValueProvidersMap, RecentCommitTokenProvider.ID, 0)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is CCConfigService) return false
    return Objects.equals(version, other.version) &&
           Objects.equals(completionType, other.completionType) &&
           Objects.equals(customFilePath, other.customFilePath) &&
           Objects.equals(customCoAuthorsFilePath, other.customCoAuthorsFilePath) &&
           Objects.equals(typeProvidersMap, other.typeProvidersMap) &&
           Objects.equals(scopeProvidersMap, other.scopeProvidersMap) &&
           Objects.equals(subjectProvidersMap, other.subjectProvidersMap) &&
           Objects.equals(bodyProvidersMap, other.bodyProvidersMap) &&
           Objects.equals(footerTypeProvidersMap, other.footerTypeProvidersMap) &&
           Objects.equals(footerValueProvidersMap, other.footerValueProvidersMap)
  }

  override fun hashCode() =
    Objects.hash(
      version,
      completionType,
      customFilePath,
      customCoAuthorsFilePath,
      typeProvidersMap,
      scopeProvidersMap,
      subjectProvidersMap,
      bodyProvidersMap,
      footerTypeProvidersMap,
      footerValueProvidersMap,
    )

  enum class CompletionType {
    TEMPLATE,
    POPUP
  }

  enum class ProviderFilterType {
    KEEP_SELECTED,
    HIDE_SELECTED
  }

  class PresentableNameGetter : State.NameGetter() {
    override fun get() = "Conventional Commit Configuration"
  }

  @Transient
  private companion object {
    private const val CURRENT_VERSION = 1

    @Suppress("SameParameterValue")
    private fun updateProviderOrder(
        providers: MutableMap<String, Int>,
        providerId: String,
        newPosition: Int,
    ) {
      // Set a decent ordering without gaps
      val map = providers.asSequence()
        .filterNot { it.key == providerId }
        .sortedBy(Entry<String, Int>::value)
        // i + 1 to leave the position to the updating Provider
        .mapIndexed { i, e -> e.key to if (i < newPosition) i else i + 1 }
        .associateBy(Pair<String, Int>::first, Pair<String, Int>::second)
        .toMutableMap()

      // Insert new provider
      map[providerId] = min(newPosition, map.size)
      providers.putAll(map)
    }
  }
}
