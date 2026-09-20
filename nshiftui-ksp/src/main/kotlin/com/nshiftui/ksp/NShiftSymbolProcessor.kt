package com.nshiftui.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier

internal const val PLUGIN_ANNOTATION = "com.nshiftui.macro.NShiftPlugin"
internal const val EVENT_ANNOTATION = "com.nshiftui.macro.NShiftEvent"
internal const val PLUGIN_BASE = "com.nshiftui.NShiftPlugin"
internal const val EVENT_BASE = "com.nshiftui.NShiftEvent"
internal const val EMPTY_METADATA = "com.nshiftui.NShiftEmptyMetadata"
internal const val COMPOSABLE = "androidx.compose.runtime.Composable"

class NShiftSymbolProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {
    private var generatedInstaller = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (generatedInstaller) {
            return emptyList()
        }
        val pluginSymbols = resolver.getSymbolsWithAnnotation(PLUGIN_ANNOTATION).toList()
        val eventSymbols = resolver.getSymbolsWithAnnotation(EVENT_ANNOTATION).toList()
        if (pluginSymbols.isEmpty() && eventSymbols.isEmpty()) {
            return emptyList()
        }

        val registrations = mutableListOf<String>()
        val originating = mutableListOf<KSFile>()

        for (symbol in pluginSymbols) {
            when (symbol) {
                is KSClassDeclaration -> {
                    val className = symbol.qualifiedName?.asString()
                    if (className == null) {
                        logger.error("@NShiftPlugin class is missing a qualified name", symbol)
                        continue
                    }
                    if (!symbol.extends(PLUGIN_BASE)) {
                        logger.error(
                            "@NShiftPlugin on a class requires a subclass of $PLUGIN_BASE (or annotate a @Composable function)",
                            symbol,
                        )
                        continue
                    }
                    symbol.containingFile?.let(originating::add)
                    registrations += "    registerPlugin<$className>()"
                }
                is KSFunctionDeclaration -> {
                    val generated = generatePluginFunction(symbol) ?: continue
                    symbol.containingFile?.let(originating::add)
                    registrations += "    registerPlugin<${generated.qualifiedName}>()"
                }
                else -> logger.error("@NShiftPlugin must target a class or function", symbol)
            }
        }

        for (symbol in eventSymbols) {
            when (symbol) {
                is KSClassDeclaration -> {
                    val className = symbol.qualifiedName?.asString()
                    if (className == null) {
                        logger.error("@NShiftEvent class is missing a qualified name", symbol)
                        continue
                    }
                    if (!symbol.extends(EVENT_BASE)) {
                        logger.error(
                            "@NShiftEvent on a class requires a subclass of $EVENT_BASE (or annotate a suspend function)",
                            symbol,
                        )
                        continue
                    }
                    symbol.containingFile?.let(originating::add)
                    registrations += "    registerEvent<$className>()"
                }
                is KSFunctionDeclaration -> {
                    val generated = generateEventFunction(symbol) ?: continue
                    symbol.containingFile?.let(originating::add)
                    registrations += "    registerEvent<${generated.qualifiedName}>()"
                }
                else -> logger.error("@NShiftEvent must target a class or function", symbol)
            }
        }

        val sources = originating.distinct().toTypedArray()
        val dependencies = if (sources.isEmpty()) {
            Dependencies(aggregating = true)
        } else {
            Dependencies(aggregating = true, *sources)
        }
        codeGenerator.createNewFile(
            dependencies = dependencies,
            packageName = "com.nshiftui.generated",
            fileName = "NShiftGenerated",
        ).bufferedWriter().use { writer ->
            writer.appendLine("package com.nshiftui.generated")
            writer.appendLine()
            writer.appendLine("import com.nshiftui.NShiftDependencyContainer")
            writer.appendLine("import com.nshiftui.registerEvent")
            writer.appendLine("import com.nshiftui.registerPlugin")
            writer.appendLine()
            writer.appendLine("fun NShiftDependencyContainer.installNShiftGenerated() {")
            if (registrations.isEmpty()) {
                writer.appendLine("}")
            } else {
                registrations.forEach { writer.appendLine(it) }
                writer.appendLine("}")
            }
        }
        generatedInstaller = true
        return emptyList()
    }

    private fun generatePluginFunction(function: KSFunctionDeclaration): GeneratedType? {
        val annotation = function.annotation(PLUGIN_ANNOTATION) ?: return null
        val spec = annotation.pluginSpec()
        if (spec.name.isBlank() || spec.version.isBlank()) {
            logger.error(
                "@NShiftPlugin on a function requires name and version",
                function,
            )
            return null
        }
        if (!function.isComposable()) {
            logger.error("@NShiftPlugin functions must also be @Composable", function)
            return null
        }
        val functionName = function.simpleName.asString()
        val packageName = function.packageName.asString()
        val qualifiedFunction = function.qualifiedName?.asString() ?: return null
        val className = "${functionName}_NShiftPlugin"
        val metadataArg = spec.metadataType?.let { "$it::class" } ?: "null"
        val file = function.containingFile
        val dependencies = if (file == null) {
            Dependencies(aggregating = false)
        } else {
            Dependencies(aggregating = false, file)
        }
        codeGenerator.createNewFile(dependencies, packageName, className).bufferedWriter().use { writer ->
            writer.appendLine("package $packageName")
            writer.appendLine()
            writer.appendLine("import androidx.compose.runtime.Composable")
            writer.appendLine("import com.nshiftui.NShiftPlugin")
            writer.appendLine("import com.nshiftui.NShiftPluginScope")
            writer.appendLine()
            writer.appendLine("class $className : NShiftPlugin(")
            writer.appendLine("    name = \"${spec.name}\",")
            writer.appendLine("    version = \"${spec.version}\",")
            writer.appendLine("    metadata = $metadataArg,")
            writer.appendLine(") {")
            writer.appendLine("    @Composable")
            writer.appendLine("    override fun Content() {")
            writer.appendLine("        $qualifiedFunction(NShiftPluginScope(this))")
            writer.appendLine("    }")
            writer.appendLine("}")
        }
        return GeneratedType("$packageName.$className")
    }

    private fun generateEventFunction(function: KSFunctionDeclaration): GeneratedType? {
        val annotation = function.annotation(EVENT_ANNOTATION) ?: return null
        val spec = annotation.eventSpec()
        if (spec.name.isBlank() || spec.version.isBlank()) {
            logger.error("@NShiftEvent on a function requires name and version", function)
            return null
        }
        if (Modifier.SUSPEND !in function.modifiers) {
            logger.error("@NShiftEvent functions must be suspend", function)
            return null
        }
        val functionName = function.simpleName.asString()
        val packageName = function.packageName.asString()
        val qualifiedFunction = function.qualifiedName?.asString() ?: return null
        val className = "${functionName}_NShiftEvent"
        val metadataArg = spec.metadataType?.let { "$it::class" } ?: "null"
        val file = function.containingFile
        val dependencies = if (file == null) {
            Dependencies(aggregating = false)
        } else {
            Dependencies(aggregating = false, file)
        }
        codeGenerator.createNewFile(dependencies, packageName, className).bufferedWriter().use { writer ->
            writer.appendLine("package $packageName")
            writer.appendLine()
            writer.appendLine("import com.nshiftui.NShiftEvent")
            writer.appendLine("import com.nshiftui.NShiftEventScope")
            writer.appendLine()
            writer.appendLine("class $className : NShiftEvent(")
            writer.appendLine("    name = \"${spec.name}\",")
            writer.appendLine("    version = \"${spec.version}\",")
            writer.appendLine("    metadata = $metadataArg,")
            writer.appendLine(") {")
            writer.appendLine("    override suspend fun execute() {")
            writer.appendLine("        $qualifiedFunction(NShiftEventScope(this))")
            writer.appendLine("    }")
            writer.appendLine("}")
        }
        return GeneratedType("$packageName.$className")
    }
}

private data class GeneratedType(val qualifiedName: String)

private data class PluginSpec(
    val name: String,
    val version: String,
    val metadataType: String?,
    val container: Boolean,
)

private data class EventSpec(
    val name: String,
    val version: String,
    val metadataType: String?,
)

private fun KSAnnotated.annotation(fqName: String): KSAnnotation? =
    annotations.firstOrNull { it.annotationType.resolve().declaration.qualifiedName?.asString() == fqName }

private fun KSAnnotation.argument(name: String): Any? =
    arguments.firstOrNull { it.name?.asString() == name }?.value

private fun KSAnnotation.pluginSpec(): PluginSpec {
    val name = argument("name") as? String ?: ""
    val version = argument("version") as? String ?: ""
    val metadata = (argument("metadata") as? KSType)?.declaration?.qualifiedName?.asString()
        ?.takeUnless { it == EMPTY_METADATA }
    val container = argument("container") as? Boolean ?: false
    return PluginSpec(name, version, metadata, container)
}

private fun KSAnnotation.eventSpec(): EventSpec {
    val name = argument("name") as? String ?: ""
    val version = argument("version") as? String ?: ""
    val metadata = (argument("metadata") as? KSType)?.declaration?.qualifiedName?.asString()
        ?.takeUnless { it == EMPTY_METADATA }
    return EventSpec(name, version, metadata)
}

private fun KSFunctionDeclaration.isComposable(): Boolean =
    annotations.any { it.annotationType.resolve().declaration.qualifiedName?.asString() == COMPOSABLE }

private fun KSClassDeclaration.extends(fqName: String): Boolean {
    val visited = mutableSetOf<String>()
    fun walk(declaration: KSClassDeclaration): Boolean {
        val qualified = declaration.qualifiedName?.asString() ?: return false
        if (!visited.add(qualified)) return false
        if (qualified == fqName) return true
        return declaration.superTypes.any { superType ->
            val superDeclaration = superType.resolve().declaration as? KSClassDeclaration ?: return@any false
            walk(superDeclaration)
        }
    }
    return superTypes.any { superType ->
        val superDeclaration = superType.resolve().declaration as? KSClassDeclaration ?: return@any false
        walk(superDeclaration)
    }
}
