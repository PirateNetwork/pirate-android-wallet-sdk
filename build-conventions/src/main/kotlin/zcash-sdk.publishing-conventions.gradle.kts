import java.util.Base64

val publicationVariant = "release"
val isSnapshot = project.property("IS_SNAPSHOT").toString().toBoolean()
val myVersion = project.property("LIBRARY_VERSION").toString()

val myGroup = "io.github.piratenetwork"
project.group = myGroup

// Apply plugins
plugins {
    id("maven-publish")
    id("signing")
}

pluginManager.withPlugin("com.android.library") {
    project.the<com.android.build.gradle.LibraryExtension>().apply {
        publishing {
            singleVariant(publicationVariant) {
                withSourcesJar()
                withJavadocJar()
            }
        }
    }
}

plugins.withId("org.gradle.maven-publish") {
    // Get credentials first - available to all tasks
    val mavenPublishUsername = project.findProperty("ZCASH_MAVEN_PUBLISH_USERNAME")?.toString()?.takeIf { it.isNotBlank() }
        ?: project.findProperty("ORG_GRADLE_PROJECT_ZCASH_MAVEN_PUBLISH_USERNAME")?.toString()?.takeIf { it.isNotBlank() }
        ?: project.findProperty("mavenCentralUsername")?.toString()?.takeIf { it.isNotBlank() }
        ?: System.getenv("ZCASH_MAVEN_PUBLISH_USERNAME")?.takeIf { it.isNotBlank() }
        ?: ""
    val mavenPublishPassword = project.findProperty("ZCASH_MAVEN_PUBLISH_PASSWORD")?.toString()?.takeIf { it.isNotBlank() }
        ?: project.findProperty("ORG_GRADLE_PROJECT_ZCASH_MAVEN_PUBLISH_PASSWORD")?.toString()?.takeIf { it.isNotBlank() }
        ?: project.findProperty("mavenCentralPassword")?.toString()?.takeIf { it.isNotBlank() }
        ?: System.getenv("ZCASH_MAVEN_PUBLISH_PASSWORD")?.takeIf { it.isNotBlank() }
        ?: ""

    // Store credentials for upload tasks - do this early
    project.extra["portalUsername"] = mavenPublishUsername
    project.extra["portalPassword"] = mavenPublishPassword
    


    val publishingExtension = extensions.getByType<PublishingExtension>().apply {
        publications {
            register<MavenPublication>("release") {
                groupId = myGroup
                version = if (isSnapshot) {
                    "$myVersion-SNAPSHOT"
                } else {
                    myVersion
                }

                afterEvaluate {
                    from(components[publicationVariant])
                }

                pom {
                    name.set("Pirate Android Wallet SDK")
                    description.set(
                        "This lightweight SDK connects Android to the Pirate Network, allowing third-party " +
                            "Android apps to send and receive shielded transactions easily, securely and privately."
                    )
                    url.set("https://github.com/piratenetwork/pirate-android-wallet-sdk/")
                    inceptionYear.set("2018")
                    scm {
                        url.set("https://github.com/piratenetwork/pirate-android-wallet-sdk/")
                        connection.set("scm:git:git://github.com/piratenetwork/pirate-android-wallet-sdk.git")
                        developerConnection.set("scm:git:ssh://git@github.com/piratenetwork/pirate-android-wallet-sdk.git")
                    }
                    developers {
                        developer {
                            id.set("cryptoforge")
                            name.set("Forge")
                            url.set("https://github.com/cryptoforge/")
                        }
                    }
                    licenses {
                        license {
                            name.set("The MIT License")
                            url.set("http://opensource.org/licenses/MIT")
                            distribution.set("repo")
                        }
                    }
                }
            }
        }

        repositories {
            mavenLocal {
                name = "MavenLocal"
            }
            
            // Maven Central Portal - Direct API
            // Using the Portal Publisher API directly per https://central.sonatype.com/api-doc
            // Authentication: Bearer token with base64(username:password)

            if (!isSnapshot) {
                // For releases: Use Maven Central Portal Direct API
                // Create local staging repository for artifacts
                val buildDir = project.layout.buildDirectory.get().asFile
                val portalStagingDir = File(buildDir, "portal-staging")
                
                maven(portalStagingDir.toURI()) {
                    name = "MavenCentral"
                }
            } else {
                // For snapshots: Use OSSRH snapshot repository  
                maven("https://oss.sonatype.org/content/repositories/snapshots/") {
                    name = "MavenCentral"
                    credentials {
                        username = mavenPublishUsername.ifBlank { "MISSING" }
                        password = mavenPublishPassword.ifBlank { "MISSING" }
                    }
                    authentication {
                        create<BasicAuthentication>("basic")
                    }
                }
            }
        }
    }

    // Maven Central Portal API Upload Tasks
    if (!isSnapshot) {
        val createPortalBundle = tasks.register("createPortalBundle", Zip::class.java) {
            group = "publishing"
            description = "Creates a bundle for Maven Central Portal upload"
            
            val buildDir = project.layout.buildDirectory.get().asFile
            val portalStagingDir = File(buildDir, "portal-staging")
            
            dependsOn("publishReleasePublicationToMavenCentralRepository")
            from(portalStagingDir)
            archiveFileName.set("${project.name}-${myVersion}-central-bundle.zip")
            destinationDirectory.set(File(buildDir, "portal"))
        }

        val uploadToPortal = tasks.register("uploadToPortal") {
            group = "publishing"
            description = "Uploads bundle to Maven Central Portal via API"
            
            dependsOn(createPortalBundle)
            
            doLast {
                val username = project.extra["portalUsername"].toString()
                val password = project.extra["portalPassword"].toString()
                
                if (username.isBlank() || password.isBlank()) {
                    throw GradleException("Maven Central Portal credentials not found. Set ZCASH_MAVEN_PUBLISH_USERNAME and ZCASH_MAVEN_PUBLISH_PASSWORD")
                }
                
                // Create Bearer token (base64 encoded username:password) as required by Portal API
                val credentials = "$username:$password"
                val token = Base64.getEncoder().encodeToString(credentials.toByteArray())
                
                val buildDir = project.layout.buildDirectory.get().asFile
                val bundleFile = File(File(buildDir, "portal"), "${project.name}-${myVersion}-central-bundle.zip")
                
                if (!bundleFile.exists()) {
                    throw GradleException("Bundle file not found: ${bundleFile.absolutePath}")
                }
                
                // Upload via Portal API
                val uploadUrl = "https://central.sonatype.com/api/v1/publisher/upload"
                val publishingType = if (project.findProperty("AUTO_PUBLISH")?.toString()?.toBoolean() == true) "AUTOMATIC" else "USER_MANAGED"
                
                logger.lifecycle("Uploading bundle to Maven Central Portal...")
                
                // Use curl for the upload - Maven Central Portal API expects multipart/form-data
                val curlCmd = listOf(
                    "curl", "--fail", "--show-error", "--location",
                    "--request", "POST",
                    "--header", "Authorization: Bearer $token",
                    "--form", "bundle=@${bundleFile.absolutePath};type=application/octet-stream",
                    "$uploadUrl?publishingType=$publishingType"
                )
                
                try {
                    // Check if curl is available
                    val curlCheck = ProcessBuilder("curl", "--version")
                        .redirectErrorStream(true)
                        .start()
                    curlCheck.waitFor()
                    if (curlCheck.exitValue() != 0) {
                        throw GradleException("curl is not available. Please install curl or use a different upload method.")
                    }
                    
                    val process = ProcessBuilder(curlCmd)
                        .redirectErrorStream(true)
                        .start()
                    
                    val output = process.inputStream.bufferedReader().readText()
                    val exitCode = process.waitFor()
                    
                    if (exitCode == 0) {
                        // The output should contain the deployment ID
                        val deploymentId = output.trim()
                        logger.lifecycle("✅ Upload successful!")
                        logger.lifecycle("📋 Deployment ID: $deploymentId")
                        logger.lifecycle("🔗 View deployment at: https://central.sonatype.com/publishing/deployments")
                        
                        if (publishingType == "USER_MANAGED") {
                            logger.lifecycle("⚠️  Manual action required: Go to the Portal to publish the deployment")
                        } else {
                            logger.lifecycle("🚀 Automatic publishing enabled - deployment will be published automatically if validation passes")
                        }
                    } else {
                        logger.error("❌ Upload failed with exit code $exitCode")
                        throw GradleException("Upload failed with exit code $exitCode:\n$output")
                    }
                } catch (e: Exception) {
                    logger.error("❌ Failed to upload to Maven Central Portal")
                    throw GradleException("Failed to upload to Maven Central Portal: ${e.message}", e)
                }
            }
        }

        // Replace the default publish task for releases
        tasks.named("publishReleasePublicationToMavenCentralRepository") {
            finalizedBy(uploadToPortal)
        }
    }

    plugins.withId("org.gradle.signing") {
        project.the<SigningExtension>().apply {
            // Check for Base64-encoded ASCII key first (modern approach)
            val base64EncodedKey = project.findProperty("ZCASH_ASCII_GPG_KEY")?.toString()?.takeIf { it.isNotEmpty() }
            val asciiSigningKey = if (base64EncodedKey != null) {
                try {
                    val keyBytes = Base64.getDecoder().decode(base64EncodedKey)
                    String(keyBytes)
                } catch (e: Exception) {
                    logger.warn("Failed to decode ZCASH_ASCII_GPG_KEY: ${e.message}")
                    ""
                }
            } else {
                ""
            }
            
            // Check for traditional GPG file-based signing
            val hasTraditionalGpgConfig = project.findProperty("signing.keyId") != null && 
                                        project.findProperty("signing.password") != null && 
                                        project.findProperty("signing.secretKeyRingFile") != null

            // Only require signing for non-snapshots and only if we have any signing configuration
            isRequired = !isSnapshot && (asciiSigningKey.isNotEmpty() || hasTraditionalGpgConfig)

            if (asciiSigningKey.isNotEmpty()) {
                // Use in-memory ASCII key
                useInMemoryPgpKeys(asciiSigningKey, "")
                sign(publishingExtension.publications)
            } else if (hasTraditionalGpgConfig) {
                // Use traditional file-based GPG signing
                sign(publishingExtension.publications)
            } else if (!isSnapshot) {
                logger.warn("No GPG signing configuration found. Set ZCASH_ASCII_GPG_KEY or configure signing.keyId, signing.password, signing.secretKeyRingFile")
            }
        }
    }
}




