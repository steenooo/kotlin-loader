package dev.steyn.kotlinloader.jar

import com.google.common.io.ByteStreams
import dev.steyn.kotlinloader.KotlinPlugin
import dev.steyn.kotlinloader.bootstrap.KotlinLoaderPlugin
import dev.steyn.kotlinloader.desc.KotlinPluginDescription
import dev.steyn.kotlinloader.exception.InvalidPluginException
import dev.steyn.kotlinloader.kts.KotlinScript
import dev.steyn.kotlinloader.loader.AbstractPluginClassLoader
import dev.steyn.kotlinloader.loader.KotlinPluginLoader
import dev.steyn.kotlinloader.plugin.KotlinLoader
import java.io.File
import java.net.URL
import java.security.CodeSource
import java.util.*
import java.util.jar.JarFile
import java.util.jar.Manifest

class KotlinPluginClassLoader(
        pluginLoader: KotlinPluginLoader,
        val file: File,
        val folder: File,
        parent: ClassLoader = KotlinLoader::class.java.classLoader,
        val desc: KotlinPluginDescription,
        private val jar: JarFile,
        private val manifest: Manifest? = jar.manifest,
        val url: URL = file.toURI().toURL()
) : AbstractPluginClassLoader(pluginLoader, arrayOf(url), parent) {

    companion object {
        init {
            ClassLoader.registerAsParallelCapable()
        }


    }


    override val plugin =
            try {
                @Suppress("UNCHECKED_CAST")
                Class.forName(desc.main, true, this) as Class<out KotlinPlugin>
            } catch (ex: ClassNotFoundException) {
                throw InvalidPluginException("Unable to find main class", ex)
            } catch (ex: ClassCastException) {
                throw InvalidPluginException("${desc.main} does not extend KotlinPlugin", ex)
            }.let {
                it.kotlin.objectInstance ?: try {
                    it.getConstructor().newInstance()
                } catch (ex: IllegalAccessException) {
                    throw InvalidPluginException("Unable to find a public constructor", ex)
                } catch (ex: InstantiationException) {
                    throw InvalidPluginException("Unable to instantiate ${desc.main}", ex)
                }
            }

    init {
        val mainClass = Class.forName(desc.main)

        val kscript = mainClass.getAnnotation(KotlinScript::class.java)
        val useKts = kscript != null

        plugin.init(file, folder, this, pluginLoader, desc, pluginLoader.server, useKts)
        if(useKts && !KotlinLoaderPlugin.getInstance().allowScripting()) {
            when(kscript?.onMissing ?: KotlinScript.Action.WARN) {
                KotlinScript.Action.WARN ->
                    plugin.logger.info(if(kscript.message.isEmpty())"${plugin.name} requires KotlinScript! Please consider enabling it." else kscript.message)

                KotlinScript.Action.DISABLE -> {
                    plugin.logger.info(if(kscript.message.isEmpty())"${plugin.name} requires KotlinScript! ${plugin.name} will not be able to start before this feature is enabled." else kscript.message)
                    pluginLoader.disablePlugin(plugin)
                }
            }
        }
    }


    override fun byJar(name: String): Class<*>? {
        try {
            val path = name.replace('.', '/') + "class"
            val entry = this.jar.getJarEntry(name)
            if (entry != null) {
                var bytes = jar.getInputStream(entry).use {
                    ByteStreams.toByteArray(it)
                }
                bytes = pluginLoader.server.unsafe.processClass(desc.bukkit, path, bytes)

                val dot = name.lastIndexOf('.')
                if (dot != -1) {
                    val pkgName = name.substring(0, dot)
                    if (getPackage(pkgName) == null) {
                        try {
                            if (manifest != null) {
                                definePackage(pkgName, manifest, url)
                            } else {
                                definePackage(pkgName, null, null, null, null, null, null, null)
                            }
                        } catch (ex: IllegalArgumentException) {
                            checkNotNull(getPackage(pkgName)) { "Cannot find package $pkgName" }
                        }
                    }
                }
                val signers = entry.codeSigners
                val source = CodeSource(url, signers)
                return defineClass(name, bytes, 0, bytes.size, source)
            }
            return null
        } catch (e: Exception) {
            throw e
        }
    }


    override fun getResource(name: String?): URL? {
        return findResource(name)
    }

    override fun getResources(name: String?): Enumeration<URL> {
        return findResources(name)
    }

}