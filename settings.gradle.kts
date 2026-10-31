/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * ARCHIVO: settings.gradle.kts
 * ================================================================================
 */

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "FenixStudio"

include(":app")
include(":core:designsystem")
include(":core:database")
include(":core:security-vault")
include(":core:network-ai")
include(":core:document-engine")
include(":core:accessibility-engines")
include(":core:mcp-sandbox")
include(":domain")
include(":feature:auth")
include(":feature:home-chat")
include(":feature:settings")
include(":feature:processor")
include(":feature:classrooms")
include(":feature:fenix-docs")
include(":feature:community")
