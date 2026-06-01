@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(24)
    jvm {
        mainRun {
            mainClass.set("com.yiaobang.kserial.MainKt")
        }
    }

    sourceSets {
        commonMain.dependencies {
            val composeVersion = libs.versions.composeMultiplatform.get()
            implementation("org.jetbrains.compose.runtime:runtime:$composeVersion")
            implementation("org.jetbrains.compose.foundation:foundation:$composeVersion")
            implementation("org.jetbrains.compose.ui:ui:$composeVersion")
            implementation("org.jetbrains.compose.components:components-resources:$composeVersion")
            implementation("org.jetbrains.compose.material3:material3:1.9.0")
            implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            //串口
            implementation(libs.fazecast.jSerialComm)
            //配置
            implementation(libs.typesafe.config)
        }
    }
}
compose {
    resources {
        // 是否生成公开的 Res 类。如果你的资源只在当前模块使用，建议设为 false
        publicResClass = false

        // 生成的 Res 类的包名（建议与你的应用包名一致）
        packageOfResClass = "com.yiaobang.kserial.resources"

        // 是否自动生成 Res 类。'auto' 是默认值
        generateResClass = auto
    }
    desktop {
        application {
            mainClass = "com.yiaobang.kserial.MainKt"
            jvmArgs(
                "-Dsun.java2d.d3d=true",
                "-Dskiko.renderApi=DIRECT3D",
                "-XX:+UseZGC",             // 使用 ZGC 降低停顿
                "-Xmx2G",                   // 给足内存
                "--enable-native-access=ALL-UNNAMED"
            )
            nativeDistributions {
                targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
                packageName = "KSerial"
                packageVersion = "1.0.0"
                buildTypes.release.proguard.isEnabled.set(false)
                buildTypes{
                    release{
                        proguard {
                            // 确保项目根目录或 composeApp 目录下确实有这个 .pro 文件
                            configurationFiles.from(project.file("compose-desktop.pro"))
                        }
                    }
                }

                windows {
                    iconFile.set(project.file("src/main/resources/icons/icon.ico"))
                     //如果需要设置开始菜单/桌面快捷方式，可以在这里增加
                    menu = true
                    shortcut = true
                    // 某些驱动会通过这个参数识别是否需要开启高性能 GPU
                    jvmArgs(
                        "-Dsun.java2d.d3d=true",
                        "-Dskiko.renderApi=DIRECT3D",
                        "-XX:+UseZGC",             // 使用 ZGC 降低停顿
                        "-Xmx2G",                   // 给足内存
                        "--enable-native-access=ALL-UNNAMED"
                    )
                }
//                macOS {
//                    iconFile.set(project.file("src/main/resources/icons/icon.icns"))
//                }
//                linux {
//                    iconFile.set(project.file("src/main/resources/icons/icon.png"))
//                }
            }
        }
    }
}
