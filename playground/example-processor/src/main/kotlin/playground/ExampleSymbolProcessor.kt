package playground

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.visitor.KSDefaultVisitor
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import com.squareup.kotlinpoet.ksp.writeTo

/**
 * Generates a Kotlin file for each @Page that we've manually declared.
 * Each generated file contains a @Page annotated function.
 * When running koweb, we should be able to visit both the new generated functions and the original ones.
 */
class ExampleSymbolProcessor(private val codeGenerator: CodeGenerator) :
    SymbolProcessor {

    /** Prevents an infinite loop, our example only needs one round. */
    private var isFirstround = true
    private val pageAnnotation = ClassName("com.varabyte.kobweb.core", "Page")

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (isFirstround) {
            val visitor = PageVisitor()
            resolver.getSymbolsWithAnnotation(pageAnnotation.canonicalName).forEach { it.accept(visitor, "") }
            isFirstround = false
        }

        return emptyList()
    }

    private inner class PageVisitor : KSDefaultVisitor<String, Unit>() {
        override fun visitFunctionDeclaration(function: KSFunctionDeclaration, data: String) {
            val pageCopy = MemberName(function.packageName.asString(), "${function.simpleName.asString()}Copy")
            val composeText = MemberName("org.jetbrains.compose.web.dom", "Text")
            FileSpec.builder(pageCopy)
                .addFunction(
                    FunSpec.builder(pageCopy)
                        // Example of interop between KSP and KotlinPoet.
                        // https://square.github.io/kotlinpoet/interop-ksp/#incremental-processing
                        .addOriginatingKSFile(function.containingFile!!)
                        .addAnnotation(pageAnnotation)
                        .addAnnotation(
                            AnnotationSpec.builder(ClassName("androidx.compose.runtime", "Composable")).build()
                        )
                        // KotlinPoet formats: https://square.github.io/kotlinpoet/m-for-members/
                        .addStatement("%M(%S)", composeText, "Copy of ${function.simpleName.asString()}")
                        .build()
                ).build().writeTo(codeGenerator, aggregating = true)
        }

        override fun defaultHandler(node: KSNode, data: String) {
            // ignored
        }
    }
}
