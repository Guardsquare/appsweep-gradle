package com.guardsquare.appsweep.gradle.dependencyanalysis

import com.squareup.moshi.JsonClass
import com.squareup.moshi.ToJson
import org.w3c.dom.Document
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory

@JsonClass(generateAdapter = false)
class AppLibrary(val group: String?, val name: String, val version: String?, val hash: String?) {

    private val classNames = mutableSetOf<String>()
    private val otherFileNames = mutableSetOf<String>()

    constructor(name: String) : this(null, name, null, null)

    /**
     * Read the library and extract all classes and other files.
     */
    fun getInformationFromZip(file: File) {
        val zipFile = ZipFile(file.absoluteFile)

        for (entry in zipFile.entries()) {
            // read package name from AndroidManifest.xml
            if (entry.name.equals("AndroidManifest.xml")) {
                try {
                    addRClasses(zipFile, entry)
                } catch (e: Exception) {
                } // it might not be a valid xml
            }

            when {
                entry.name.endsWith(".jar") -> { // for an .aar, it will contain a jar containing the classes
                    val zipInputStream = ZipInputStream(zipFile.getInputStream(entry))
                    var innerEntry = zipInputStream.nextEntry
                    while (innerEntry != null) {
                        if (innerEntry.name.endsWith(".class")) {
                            classNames.add(innerEntry.name)
                        }
                        innerEntry = zipInputStream.nextEntry
                    }
                }

                entry.name.endsWith(".class") -> {
                    classNames.add(entry.name)
                }

                else -> {
                    otherFileNames.add(entry.name)
                }
            }
        }
    }

    /**
     * Adds all known R-classes to the contained classes. Since these are not present in the aar, we need to fake this here.
     */
    private fun addRClasses(zipFile: ZipFile, entry: ZipEntry) {
        val factory: DocumentBuilderFactory = DocumentBuilderFactory.newInstance()
        val builder: DocumentBuilder = factory.newDocumentBuilder()
        val document: Document = builder.parse(zipFile.getInputStream(entry))

        document.documentElement.normalize()
        val list: NodeList = document.getElementsByTagName("manifest")
        if (list.length >= 1) { // should only be one manifest tag, if there are multiple, just use the first one
            val node: Node = list.item(0)
            val namedItem = node.attributes.getNamedItem("package")
            if (namedItem != null) {
                val packageName = namedItem.nodeValue.toString().replace(".", "/")

                val rNames = setOf(
                    "R.class",
                    "R\$anim.class",
                    "R\$attr.class",
                    "R\$bool.class",
                    "R\$color.class",
                    "R\$dimen.class",
                    "R\$drawable.class",
                    "R\$id.class",
                    "R\$integer.class",
                    "R\$layout.class",
                    "R\$mipmap.class",
                    "R\$string.class",
                    "R\$style.class",
                    "R\$styleable.class"
                )

                for (R in rNames) {
                    classNames.add("$packageName/$R")
                }
            }
        }
    }

    override fun toString(): String {
        return if (group == null) {
            name
        } else {
            "$group:$name:$version"
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AppLibrary) return false

        if (group != other.group) return false
        if (name != other.name) return false
        if (version != other.version) return false

        return hash != other.hash
    }

    override fun hashCode(): Int {
        var result = group?.hashCode() ?: 0
        result = 31 * result + name.hashCode()
        result = 31 * result + (version?.hashCode() ?: 0)
        result = 31 * result + (hash?.hashCode() ?: 0)
        return result
    }

    /**
     * Convert to a simple reference to this library, i.e., stripping off the contained files
     */
    fun toReference(): AppLibraryReference {
        return AppLibraryReference(group, name, version, hash)
    }

    class JsonAdapter {
        @ToJson
        fun eventToJson(library: AppLibrary): AppLibraryJson {
            return AppLibraryJson(
                library.group,
                library.name,
                library.version,
                library.hash,
                library.classNames.toSet(),
                library.otherFileNames.toSet()
            )
        }
    }
}
