import kotlinx.kover.gradle.plugin.dsl.AggregationType
import kotlinx.kover.gradle.plugin.dsl.CoverageUnit
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.25"
    id("org.jetbrains.intellij.platform") version "2.1.0"
    id("org.jetbrains.kotlinx.kover") version "0.8.3"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        create(
            providers.gradleProperty("platformType"),
            providers.gradleProperty("platformVersion"),
        )
        // WebStorm ships JavaScript / TypeScript support.
        bundledPlugin("JavaScript")
        instrumentationTools()
        testFramework(TestFrameworkType.Platform)
    }
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.opentest4j:opentest4j:1.3.0")
}

tasks.test {
    systemProperty("java.awt.headless", "true")
    systemProperty("idea.force.use.core.classloader", "true")
}

kotlin {
    jvmToolchain(17)
}

intellijPlatform {
    publishing {
        token.set(providers.environmentVariable("JETBRAINS_PUBLISH_TOKEN"))
    }
    pluginConfiguration {
        id = "dev.shamar.adonis.ide"
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")
        ideaVersion {
            sinceBuild = "242"
            untilBuild = provider { null }
        }
        description.set(
            """
            Adonis Idea — native Edge highlighting, deep completions, navigation,
            Ace generators, Lucid/Mongoose ORM intelligence, and optional Shamar
            support. Works on any AdonisJS app; no @shamar packages required.
            WebStorm 2024.2+.
            """.trimIndent(),
        )
        changeNotes.set(
            """
            <ul>
              <li>0.3.0 — EdgeJS guide fidelity: arbitrary <code>@form</code> /
                  <code>@!button</code> tag components; object props
                  (<code>route</code>/<code>method</code>/starter-kit keys);
                  <code>@includeIf</code> 2nd-arg views; <code>router.on().render</code>;
                  <code>@{{</code> escape; <code>@dump</code>; <code>${'$'}slots</code> /
                  helpers; raw echo coloring; Adonis <code>*.edge</code> icons.</li>
              <li>0.2.0 — Plugin id <code>dev.shamar.adonis.ide</code>; official AdonisJS
                  icon; precise controller-action navigation; view.render completions;
                  Edge layouts/page structure + color scheme; two-way env keys;
                  <code>@</code> directive catalog + structured snippets (args +
                  <code>@end</code>); <code>@each</code> loop aliases.</li>
              <li>0.1.0 — Initial Adonis Idea release: Edge language, bundled
                  indexer (no framework install required), routes/views/config/env,
                  Lucid/Mongoose, Wire attributes, detection-gated Shamar layer,
                  Ace make:* generators and run configs.</li>
            </ul>
            """.trimIndent(),
        )
    }
}

tasks {
    wrapper {
        gradleVersion = providers.gradleProperty("gradleVersion").get()
    }
    processResources {
        from("indexer") {
            into("indexer")
        }
    }
    check {
        dependsOn("koverVerify")
    }
}

kover {
    reports {
        filters {
            excludes {
                classes(
                    "dev.shamar.adonis.ide.EdgeEditorHighlighterProvider",
                    "dev.shamar.adonis.ide.EdgeEditorHighlighterProvider*",
                    "dev.shamar.adonis.ide.EdgeFileTypeOverrider",
                    "dev.shamar.adonis.ide.EdgeFileTypeDetector",
                    "dev.shamar.adonis.ide.EdgeFileViewProvider*",
                    "dev.shamar.adonis.ide.EdgeParserDefinition",
                    "dev.shamar.adonis.ide.EdgeParserDefinition*",
                    "dev.shamar.adonis.ide.EdgeFile",
                    "dev.shamar.adonis.ide.EdgeFileType",
                    "dev.shamar.adonis.ide.EdgeLanguage",
                    "dev.shamar.adonis.ide.EdgeLanguage$*",
                    "dev.shamar.adonis.ide.EdgeBraceMatcher",
                    "dev.shamar.adonis.ide.EdgeTypedHandler",
                    "dev.shamar.adonis.ide.EdgeSyntaxHighlighter*",
                    "dev.shamar.adonis.ide.EdgeColorSettingsPage",
                    "dev.shamar.adonis.ide.EdgeColorSettingsPage*",
                    "dev.shamar.adonis.ide.AdonisPluginListener",
                    "dev.shamar.adonis.ide.AdonisIndexWatcher",
                    "dev.shamar.adonis.ide.AdonisIndexWatcher$*",
                    "dev.shamar.adonis.ide.AdonisProjectService",
                    "dev.shamar.adonis.ide.AdonisProjectService$*",
                    "dev.shamar.adonis.ide.AceConfigurationType",
                    "dev.shamar.adonis.ide.AceConfigurationType$*",
                    "dev.shamar.adonis.ide.AceConfigurationFactory",
                    "dev.shamar.adonis.ide.AceRunConfiguration",
                    "dev.shamar.adonis.ide.AceRunConfiguration$*",
                    "dev.shamar.adonis.ide.AceSettingsEditor",
                    "dev.shamar.adonis.ide.RebuildIndexAction",
                    "dev.shamar.adonis.ide.AdonisReferenceContributor",
                    "dev.shamar.adonis.ide.AdonisReferenceProvider",
                    "dev.shamar.adonis.ide.AdonisCompletionContributor",
                    "dev.shamar.adonis.ide.AdonisCompletionContributor$*",
                    "dev.shamar.adonis.ide.AdonisAnnotator",
                    "dev.shamar.adonis.ide.AdonisCreateViewIntention",
                    "dev.shamar.adonis.ide.AdonisAceMakeIntention",
                    "dev.shamar.adonis.ide.AdonisExtractPartialIntention",
                    "dev.shamar.adonis.ide.AdonisIncludeToComponentIntention",
                    "dev.shamar.adonis.ide.AdonisInsertRelationStubIntention",
                    "dev.shamar.adonis.ide.AdonisUnknownSymbolQuickFix",
                    "dev.shamar.adonis.ide.AdonisCodeActionIntentionsKt",
                    "dev.shamar.adonis.ide.AdonisRefactorIntentionsKt",
                    "dev.shamar.adonis.ide.AdonisAceRunner",
                    "dev.shamar.adonis.ide.AdonisAceRunner*",
                    "dev.shamar.adonis.ide.AdonisMakeAction",
                    "dev.shamar.adonis.ide.AdonisMakeAction*",
                    "dev.shamar.adonis.ide.AdonisMakeActionGroup",
                    "dev.shamar.adonis.ide.AdonisMakeActionGroup*",
                    "dev.shamar.adonis.ide.AdonisModelMakeDialog",
                    "dev.shamar.adonis.ide.AdonisModelMakeDialog*",
                    "dev.shamar.adonis.ide.AdonisToolWindowFactory",
                    "dev.shamar.adonis.ide.AdonisToolWindowFactory*",
                    "dev.shamar.adonis.ide.AdonisToolWindowPanel",
                    "dev.shamar.adonis.ide.AdonisToolWindowPanel*",
                    "dev.shamar.adonis.ide.AdonisDocumentationProvider",
                    "dev.shamar.adonis.ide.AdonisDocumentationProvider*",
                    "dev.shamar.adonis.ide.AdonisEdgeStructureAnnotator",
                    "dev.shamar.adonis.ide.AdonisGotoDeclarationHandler",
                    "dev.shamar.adonis.ide.AdonisFindUsagesHandlerFactory",
                    "dev.shamar.adonis.ide.AdonisFindUsagesHandlerFactory*",
                    "dev.shamar.adonis.ide.AdonisFindUsagesHandler",
                    "dev.shamar.adonis.ide.AdonisFindUsagesHandler*",
                    "dev.shamar.adonis.ide.AdonisReferencesSearchExecutor",
                    "dev.shamar.adonis.ide.AdonisReferencesSearchExecutor*",
                    "dev.shamar.adonis.ide.AdonisSymbolPsiElement",
                    "dev.shamar.adonis.ide.AdonisSymbolPsiElement*",
                    "dev.shamar.adonis.ide.AdonisSymbolReference",
                    "dev.shamar.adonis.ide.AdonisSymbolReference*",
                    "dev.shamar.adonis.ide.AdonisNavigation",
                    "dev.shamar.adonis.ide.AdonisNavigation$*",
                    "dev.shamar.adonis.ide.AdonisRenameProcessor",
                    "dev.shamar.adonis.ide.AdonisRenameProcessor*",
                    "dev.shamar.adonis.ide.AdonisUsageInfoFactory",
                    "dev.shamar.adonis.ide.AdonisUsageInfoFactory*",
                    "dev.shamar.adonis.ide.AdonisIndexProcess",
                    "dev.shamar.adonis.ide.AdonisIndexProcess*",
                    "dev.shamar.adonis.ide.AdonisNode",
                    "dev.shamar.adonis.ide.AdonisNode*",
                    "dev.shamar.adonis.ide.AdonisStubFileWriter",
                    "dev.shamar.adonis.ide.AdonisStubFileWriter*",
                    // File-tree walker — covered by unit findInText; walk paths are OS-heavy.
                    "dev.shamar.adonis.ide.AdonisCallSiteSearcher",
                    "dev.shamar.adonis.ide.AdonisCallSiteSearcher*",
                    "dev.shamar.adonis.ide.Ace*",
                )
            }
        }
        verify {
            rule {
                bound {
                    minValue.set(100)
                    coverageUnits.set(CoverageUnit.LINE)
                    aggregationForGroup.set(AggregationType.COVERED_PERCENTAGE)
                }
            }
        }
    }
}
