package com.varabyte.kobweb.gradle.core.ksp

import com.google.devtools.ksp.gradle.KspExtension
import com.google.devtools.ksp.gradle.KspGradleSubplugin
import com.varabyte.kobweb.ProcessorMode
import com.varabyte.kobweb.frontendFile
import com.varabyte.kobweb.gradle.core.extensions.KobwebBlock
import com.varabyte.kobweb.gradle.core.extensions.kobwebBlock
import com.varabyte.kobweb.gradle.core.kmp.JsTarget
import com.varabyte.kobweb.gradle.core.kmp.JvmTarget
import com.varabyte.kobweb.gradle.core.kmp.TargetPlatform
import com.varabyte.kobweb.gradle.core.kmp.jsTarget
import com.varabyte.kobweb.ksp.KSP_API_PACKAGE_KEY
import com.varabyte.kobweb.ksp.KSP_PAGES_PACKAGE_KEY
import com.varabyte.kobweb.ksp.KSP_PROCESSOR_MODE_KEY
import com.varabyte.kobweb.project.common.PackageUtils
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.language.jvm.tasks.ProcessResources

fun Project.applyKspPlugin() = pluginManager.apply(KspGradleSubplugin::class.java)

fun Project.setKspMode(mode: ProcessorMode) = addKspArguments(KSP_PROCESSOR_MODE_KEY to mode.name)

// TODO: we currently set KSP_*_PACKAGE_KEY using task.project.group since the project.group of where the plugin is
//  applied may be different. However, task.project is not recommended to be used, what are our alternatives?

/** Add & configure the Kobweb KSP processor for JS sources. */
fun Project.setupKspJs(target: JsTarget, mode: ProcessorMode) {
    addKspDependency(target)

    project.tasks.matching { it.name == target.kspKotlin }.configureEach {
        addKspArguments(
            KSP_PAGES_PACKAGE_KEY to PackageUtils.resolvePackageShortcut(
                this@configureEach.project.group.toString(),
                kobwebBlock.pagesPackage.get()
            )
        )
    }

    // js resources are not automatically hooked up to processResources, see: https://github.com/google/ksp/issues/1539
    project.tasks.named<ProcessResources>(jsTarget.processResources) {
        val kspFrontendOutput = project.tasks.named(jsTarget.kspKotlin).get()
            .outputs.files.asFileTree.matching { include(mode.frontendFile) }
        from(kspFrontendOutput)
    }
}

/** Add & configure the Kobweb KSP processor for JVM sources. */
fun Project.setupKspJvm(target: JvmTarget) {
    addKspDependency(target)

    project.tasks.matching { it.name == target.kspKotlin }.configureEach {
        val apiPackage = PackageUtils.resolvePackageShortcut(this.project.group.toString(), kobwebBlock.apiPackage.get())
        addKspArguments(KSP_API_PACKAGE_KEY to apiPackage)
    }
}

private val Project.kspExtension: KspExtension
    get() = extensions.getByType<KspExtension>()

/**
 * Convenience method for registering key/value parameters that can be read by KSP.
 *
 * This method assumes that this project has already applied the KSP plugin.
 */
fun Project.addKspArguments(vararg keyValues: Pair<String, String>) {
    kspExtension.apply {
        keyValues.forEach { (key, value) -> arg(key, value) }
    }
}

/**
 * Add a KSP dependency to the given target.
 *
 * Once done, this means that KSP will process this project, using the KSP processor whose coordinates are set in the
 * project's [KobwebBlock.kspProcessorDependency] property.
 *
 * In order for this to work, you must first call [applyKspPlugin] for the project.
 *
 * You do not need to call this method if you already called [setupKspJs] or [setupKspJvm], as those will call this as
 * a side effect.
 */
fun Project.addKspDependency(target: TargetPlatform<*>) {
    val configurationName = "ksp${target.capitalizedName}"

    configurations.matching { it.name == configurationName }.configureEach {
        dependencies {
            add(this@configureEach.name, kobwebBlock.kspProcessorDependency.get())
        }
    }
}
