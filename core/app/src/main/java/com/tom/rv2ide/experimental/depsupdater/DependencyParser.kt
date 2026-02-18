package com.tom.rv2ide.experimental.depsupdater

class DependencyParser {
    
    fun parseDependencies(content: String): List<Dependency> {
        val dependencies = mutableListOf<Dependency>()
        val seen = mutableSetOf<String>()
        
        val variables = parseVariables(content)
        
        VARIABLE_PATTERN.findAll(content).forEach { match ->
            val group = match.groupValues[2]
            val name = match.groupValues[3]
            val variableName = match.groupValues[4]
            val version = variables[variableName] ?: ""
            val key = "$group:$name"
            
            if (!seen.contains(key)) {
                seen.add(key)
                dependencies.add(
                    Dependency(
                        group = group,
                        name = name,
                        currentVersion = version,
                        variableReference = variableName
                    )
                )
            }
        }
        
        CATALOG_PATTERN.findAll(content).forEach { match ->
            val catalogRef = match.groupValues[2]
            val key = "catalog:$catalogRef"
            
            if (!seen.contains(key)) {
                seen.add(key)
                dependencies.add(
                    Dependency(
                        group = "",
                        name = "",
                        currentVersion = "",
                        catalogReference = catalogRef
                    )
                )
            }
        }
        
        IMPLEMENTATION_PATTERN.findAll(content).forEach { match ->
            val group = match.groupValues[2]
            val name = match.groupValues[3]
            val version = match.groupValues[4]
            val key = "$group:$name"
            
            if (!seen.contains(key)) {
                seen.add(key)
                dependencies.add(
                    Dependency(
                        group = group,
                        name = name,
                        currentVersion = version
                    )
                )
            }
        }
        
        return dependencies
    }
    
    private fun parseVariables(content: String): Map<String, String> {
        val variables = mutableMapOf<String, String>()
        
        VAL_PATTERN.findAll(content).forEach { match ->
            variables[match.groupValues[1]] = match.groupValues[2]
        }
        
        VAR_PATTERN.findAll(content).forEach { match ->
            variables[match.groupValues[1]] = match.groupValues[2]
        }
        
        return variables
    }
    
    fun parseVersionCatalog(content: String): Map<String, DependencyInfo> {
        val catalog = mutableMapOf<String, DependencyInfo>()
        val versions = mutableMapOf<String, String>()
        
        val cleanContent = content.replace("\r\n", "\n")
        val lines = cleanContent.lines()
        
        var inVersions = false
        var inLibraries = false
        
        var i = 0
        while (i < lines.size) {
            var line = lines[i].trim()
            
            when {
                line.startsWith("[versions]") -> {
                    inVersions = true
                    inLibraries = false
                }
                line.startsWith("[libraries]") -> {
                    inVersions = false
                    inLibraries = true
                }
                line.startsWith("[") -> {
                    inVersions = false
                    inLibraries = false
                }
                line.isEmpty() || line.startsWith("#") -> {
                }
                inVersions -> {
                    val parts = line.split("=", limit = 2)
                    if (parts.size == 2) {
                        val key = parts[0].trim()
                        val value = parts[1].trim().removeSurrounding("\"")
                        versions[key] = value
                    }
                }
                inLibraries -> {
                    if (line.contains("=")) {
                        val libName = line.substringBefore("=").trim()
                        
                        if (line.contains("{")) {
                            val fullDef = StringBuilder()
                            fullDef.append(line)
                            
                            while (!line.contains("}") && i < lines.size - 1) {
                                i++
                                line = lines[i].trim()
                                fullDef.append(" ").append(line)
                            }
                            
                            val definition = fullDef.toString()
                            
                            val groupMatch = GROUP_REGEX.find(definition)
                            val nameMatch = NAME_REGEX.find(definition)
                            val versionRefMatch = VERSION_REF_REGEX.find(definition)
                            val versionMatch = VERSION_REGEX.find(definition)
                            
                            if (groupMatch != null && nameMatch != null) {
                                val group = groupMatch.groupValues[1]
                                val name = nameMatch.groupValues[1]
                                val version = when {
                                    versionRefMatch != null -> versions[versionRefMatch.groupValues[1]] ?: ""
                                    versionMatch != null -> versionMatch.groupValues[1]
                                    else -> ""
                                }
                                
                                catalog[libName] = DependencyInfo(group, name, version)
                            }
                        } else {
                            val matches = STRING_PATTERN.findAll(line).toList()
                            if (matches.isNotEmpty()) {
                                val coordString = matches.last().groupValues[1]
                                val parts = coordString.split(":")
                                if (parts.size >= 3) {
                                    catalog[libName] = DependencyInfo(parts[0], parts[1], parts[2])
                                }
                            }
                        }
                    }
                }
            }
            i++
        }
        
        return catalog
    }

    private companion object {
        val VARIABLE_PATTERN = """(implementation|api|compileOnly|runtimeOnly|testImplementation|androidTestImplementation)\s*\(\s*["']([^:]+):([^:]+):\$\{?([a-zA-Z0-9_]+)\}?["']\s*\)""".toRegex()
        val CATALOG_PATTERN = """(implementation|api|compileOnly|runtimeOnly|testImplementation|androidTestImplementation)\s*\(\s*libs\.([a-zA-Z0-9\.\-_]+)\s*\)""".toRegex()
        val IMPLEMENTATION_PATTERN = """(implementation|api|compileOnly|runtimeOnly|testImplementation|androidTestImplementation)\s*\(\s*["']([^:]+):([^:]+):([^"'\$]+)["']\s*\)""".toRegex()

        val VAL_PATTERN = """val\s+([a-zA-Z0-9_]+)\s*=\s*["']([^"']+)["']""".toRegex()
        val VAR_PATTERN = """var\s+([a-zA-Z0-9_]+)\s*=\s*["']([^"']+)["']""".toRegex()

        val GROUP_REGEX = """group\s*=\s*"([^"]+)"""".toRegex()
        val NAME_REGEX = """name\s*=\s*"([^"]+)"""".toRegex()
        val VERSION_REF_REGEX = """version\.ref\s*=\s*"([^"]+)"""".toRegex()
        val VERSION_REGEX = """version\s*=\s*"([^"]+)"""".toRegex()

        val STRING_PATTERN = """"([^"]+)"""".toRegex()
    }
}

data class DependencyInfo(
    val group: String,
    val name: String,
    val version: String
)