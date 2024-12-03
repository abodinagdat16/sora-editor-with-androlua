package com.androlua.source

import android.content.Context
import io.dingyi222666.sora.lua.R
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import dalvik.system.DexFile
import io.dingyi222666.sora.lua.CompletionName
import io.github.rosemoe.sora.lang.completion.CompletionItemKind

object PackageUtil {
    private var packages: JSONObject? = null
    private val classMap = mutableMapOf<String, MutableList<String>>()

    private val cacheClassed = mutableMapOf<String, List<CompletionName>>()

    @JvmStatic
    fun load(context: Context) {
        if (packages != null) return

        try {
            // 先尝试从缓存加载
            loadFromCache(context)
        } catch (e: Exception) {
            // 缓存加载失败，从原始资源加载
            loadFromRawResource(context)
        }
    }

    @JvmStatic
    fun load(context: Context, path: String) {
        if (packages != null) return

        try {
            File(path).readText().let { content ->
                initializePackages(context, content)
            }
        } catch (e: Exception) {
            // 如果自定义路径加载失败，回退到默认加载
            load(context)
        }
    }

    private fun loadFromCache(context: Context) {
        val cacheFile = File(context.cacheDir, "package_cache.json")
        if (!cacheFile.exists()) {
            throw FileNotFoundException("Cache file not found")
        }
        initializePackages(context, cacheFile.readText())
    }

    private fun loadFromRawResource(context: Context) {
        context.resources.openRawResource(R.raw.android).use { stream ->
            val content = stream.bufferedReader().use { it.readText() }
            initializePackages(context, content)

            // 保存到缓存
            try {
                val cacheFile = File(context.cacheDir, "package_cache.json")
                cacheFile.writeText(content)
            } catch (e: Exception) {
                // 缓存保存失败可以忽略
            }
        }
    }

    private fun initializePackages(context: Context, jsonContent: String) {
        packages = JSONObject(jsonContent)

        // 处理 DexFile 条目
        DexFile(context.packageCodePath).entries().asSequence()
            .map { it.replace("$", ".").split(".") }.forEach { parts ->
                var currentJson = packages
                parts.filter { it.length > 2 }.forEach { part ->
                    currentJson = currentJson?.let { json ->
                        when {
                            json.has(part) -> json.getJSONObject(part)
                            else -> JSONObject().also { json.put(part, it) }
                        }
                    }
                }
            }

        // 构建导入映射
        buildImports(packages!!, "")
    }

    private fun buildImports(json: JSONObject, pkg: String) {
        json.keys().asSequence().forEach { key ->
            try {
                val subJson = json.getJSONObject(key)
                if (key[0].isUpperCase()) {
                    classMap.getOrPut(key) { mutableListOf() }.add(pkg + key)
                }
                buildImports(subJson, "$pkg$key.")
            } catch (e: Exception) {
                // 忽略解析错误
            }
        }
    }

    @JvmStatic
    fun fix(name: String): List<String>? = classMap[name]

    @JvmStatic
    fun filter(name: String): List<CompletionName> {
        if (packages == null) return emptyList()

        val parts = name.split(".")
        val (searchDepth, searchTerm) = if (name.endsWith(".")) {
            Pair(parts.size, "")
        } else {
            Pair(parts.size - 1, parts.last().lowercase())
        }

        var currentJson = packages
        // 遍历路径
        for (i in 0 until searchDepth) {
            currentJson = try {
                currentJson?.getJSONObject(parts[i])
            } catch (e: Exception) {
                return emptyList()
            }
        }

        return currentJson?.keys()?.asSequence()?.filter { it.lowercase().startsWith(searchTerm) }
            ?.map { CompletionName(it, CompletionItemKind.Module, " :package | :class") }?.toList()
            ?: emptyList()
    }

    @JvmStatic
    fun filterPackage(name: String, current: String): List<CompletionName> {
        if (packages == null) return emptyList()

        return classMap.keys.asSequence().filter {
            name.indexOf(it.lowercase()) != -1 && name.length > 3
        }.flatMap {
            classMap.getValue(it)
        }.mapNotNull {
            runCatching {
                Class.forName(it)
            }.getOrNull()
        }.flatMap {
            runCatching {
                cacheClassed.getOrPut(it.name) {
                   /* val base = (getJavaMethods(it) + getJavaFields(it)).toMutableList()

                    // 添加父类方法，直到 Object

                    var clazz = it

                    while (clazz.name != "java.lang.Object") {
                        clazz = clazz.superclass

                        base.addAll(getJavaMethods(clazz))
                        base.addAll(getJavaFields(clazz))
                    }
                    base*/

                    getJavaMethods(it) + getJavaFields(it)
                }
            }.getOrElse { emptyList() }
        }.distinct()
            .filter {
                it.name.lowercase().startsWith(current.lowercase())
            }.toList()
    }

    private fun getJavaMethods(clazz: Class<*>): List<CompletionName> {
        val methods = clazz.methods
        val names = mutableListOf<CompletionName>()
        for (method in methods) {
            names.add(CompletionName(method.name, CompletionItemKind.Method, " :method"))

            if (method.parameters.isEmpty() && method.name.startsWith("get")) {
                names.add(CompletionName(method.name, CompletionItemKind.Property, " :property"))
            }

            if (method.parameters.size == 1 && method.name.startsWith(
                    "set"
                )
            ) {
                var name = method.name.substring(3)

                if (name.endsWith("Listener")) {
                    name = name.substring(0, name.length - 8)
                }

                // sort the first char
                name = name.substring(0, 1).lowercase() + name.substring(1)

                println(name)

                names.add(CompletionName(name, CompletionItemKind.Field, " :field"))
            }
        }

        return names
    }

    private fun getJavaFields(clazz: Class<*>): List<CompletionName> {
        val fields = clazz.fields
        val names = mutableListOf<CompletionName>()
        for (field in fields) {
            names.add(CompletionName(field.name, CompletionItemKind.Field, " :field"))
        }

        return names
    }
}