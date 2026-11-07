# 依赖许可清单

检查日期：2026-11-06。根据本地离线 Gradle 实际解析结果生成，未上传项目文件。

此表覆盖 release/debug 运行时、debug JVM 测试与 debug 设备测试的已解析外部归档坐标。不同配置中不同版本分别列出；平台 BOM 等无归档的约束仍见 `gradle/libs.versions.toml`。许可来自对应缓存 POM（必要时继承其父 POM），不是从整个缓存目录推测依赖。

项目自身的 `IntentGate:app:unspecified` 不属于第三方，未列入。此表不包括完整构建工具链/SDK/JDK 的所有传递依赖，也不表示分发了这些第三方二进制。源码候选中不包含下载的 AAR/JAR 缓存。

完整随附许可及分发边界见 [第三方声明](../THIRD-PARTY-NOTICES.md)。原项目的 MIT 方案仍待批准。后续依赖或实际二进制产物发生变化时需重新复核此表及 NOTICE。

| 实际坐标 | 出现配置 | 上游 POM 声明的许可 |
| --- | --- | --- |
| `androidx.activity:activity-compose:1.8.2` | 正式运行时、调试运行时、JVM 测试、设备测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.activity:activity-ktx:1.8.2` | 正式运行时、调试运行时、JVM 测试、设备测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.activity:activity:1.8.2` | 正式运行时、调试运行时、JVM 测试、设备测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.annotation:annotation-experimental:1.4.1` | 正式运行时、调试运行时、JVM 测试、设备测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.annotation:annotation-jvm:1.9.1` | 正式运行时、调试运行时、JVM 测试、设备测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.arch.core:core-common:2.2.0` | 正式运行时、调试运行时、JVM 测试、设备测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.arch.core:core-runtime:2.2.0` | 正式运行时、调试运行时、JVM 测试、设备测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.autofill:autofill:1.0.0` | 正式运行时、调试运行时、JVM 测试、设备测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.collection:collection-jvm:1.5.0` | 正式运行时、调试运行时、JVM 测试、设备测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.collection:collection-ktx:1.5.0` | 正式运行时、调试运行时、JVM 测试、设备测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.compose.animation:animation-android:1.10.4` | 正式运行时、调试运行时、JVM 测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.compose.animation:animation-core-android:1.10.4` | 正式运行时、调试运行时、JVM 测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.compose.foundation:foundation-android:1.10.4` | 正式运行时、调试运行时、JVM 测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.compose.foundation:foundation-layout-android:1.10.4` | 正式运行时、调试运行时、JVM 测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.compose.material3:material3-android:1.4.0` | 正式运行时、调试运行时、JVM 测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.compose.material:material-android:1.10.4` | 调试运行时、JVM 测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.compose.material:material-ripple-android:1.10.4` | 正式运行时、调试运行时、JVM 测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.compose.runtime:runtime-android:1.10.4` | 正式运行时、调试运行时、JVM 测试、设备测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.compose.runtime:runtime-annotation-android:1.10.4` | 正式运行时、调试运行时、JVM 测试、设备测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.compose.runtime:runtime-retain-android:1.10.4` | 正式运行时、调试运行时、JVM 测试、设备测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.compose.runtime:runtime-saveable-android:1.10.4` | 正式运行时、调试运行时、JVM 测试、设备测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.compose.ui:ui-android:1.10.4` | 正式运行时、调试运行时、JVM 测试、设备测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.compose.ui:ui-geometry-android:1.10.4` | 正式运行时、调试运行时、JVM 测试、设备测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.compose.ui:ui-graphics-android:1.10.4` | 正式运行时、调试运行时、JVM 测试、设备测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.compose.ui:ui-test-android:1.10.4` | 设备测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.compose.ui:ui-test-junit4-android:1.10.4` | 设备测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.compose.ui:ui-test-manifest:1.10.4` | 调试运行时、JVM 测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.compose.ui:ui-text-android:1.10.4` | 正式运行时、调试运行时、JVM 测试、设备测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.compose.ui:ui-tooling-android:1.10.4` | 调试运行时、JVM 测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.compose.ui:ui-tooling-data-android:1.10.4` | 调试运行时、JVM 测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.compose.ui:ui-tooling-preview-android:1.10.4` | 正式运行时、调试运行时、JVM 测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.compose.ui:ui-unit-android:1.10.4` | 正式运行时、调试运行时、JVM 测试、设备测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.compose.ui:ui-util-android:1.10.4` | 正式运行时、调试运行时、JVM 测试、设备测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.concurrent:concurrent-futures-ktx:1.2.0` | 设备测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.concurrent:concurrent-futures:1.1.0` | 正式运行时、调试运行时、JVM 测试、设备测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.core:core-ktx:1.16.0` | 正式运行时、调试运行时、JVM 测试、设备测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.core:core-viewtree:1.0.0` | 正式运行时、调试运行时、JVM 测试、设备测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.core:core:1.16.0` | 正式运行时、调试运行时、JVM 测试、设备测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.customview:customview-poolingcontainer:1.0.0` | 正式运行时、调试运行时、JVM 测试、设备测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.documentfile:documentfile:1.0.0` | 正式运行时、调试运行时、JVM 测试、设备测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.dynamicanimation:dynamicanimation:1.0.0` | 正式运行时、调试运行时、JVM 测试、设备测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.emoji2:emoji2:1.4.0` | 正式运行时、调试运行时、JVM 测试、设备测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.graphics:graphics-path:1.0.1` | 正式运行时、调试运行时、JVM 测试、设备测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.interpolator:interpolator:1.0.0` | 正式运行时、调试运行时、JVM 测试、设备测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.legacy:legacy-support-core-utils:1.0.0` | 正式运行时、调试运行时、JVM 测试、设备测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.lifecycle:lifecycle-common-java8:2.9.4` | 正式运行时、调试运行时、JVM 测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.lifecycle:lifecycle-common-jvm:2.9.4` | 正式运行时、调试运行时、JVM 测试、设备测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.lifecycle:lifecycle-livedata-core-ktx:2.9.4` | 正式运行时、调试运行时、JVM 测试、设备测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.lifecycle:lifecycle-livedata-core:2.9.4` | 正式运行时、调试运行时、JVM 测试、设备测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.lifecycle:lifecycle-livedata:2.9.4` | 正式运行时、调试运行时、JVM 测试、设备测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.lifecycle:lifecycle-process:2.9.4` | 正式运行时、调试运行时、JVM 测试、设备测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.lifecycle:lifecycle-runtime-android:2.9.4` | 正式运行时、调试运行时、JVM 测试、设备测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.lifecycle:lifecycle-runtime-compose-android:2.9.4` | 正式运行时、调试运行时、JVM 测试、设备测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.lifecycle:lifecycle-runtime-ktx-android:2.9.4` | 正式运行时、调试运行时、JVM 测试、设备测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.lifecycle:lifecycle-viewmodel-android:2.9.4` | 正式运行时、调试运行时、JVM 测试、设备测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4` | 正式运行时、调试运行时、JVM 测试、设备测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.lifecycle:lifecycle-viewmodel-savedstate-android:2.9.4` | 正式运行时、调试运行时、JVM 测试、设备测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.lifecycle:lifecycle-viewmodel:2.9.4` | 正式运行时、调试运行时、JVM 测试、设备测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.loader:loader:1.0.0` | 正式运行时、调试运行时、JVM 测试、设备测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.localbroadcastmanager:localbroadcastmanager:1.0.0` | 正式运行时、调试运行时、JVM 测试、设备测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.print:print:1.0.0` | 正式运行时、调试运行时、JVM 测试、设备测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.profileinstaller:profileinstaller:1.4.0` | 正式运行时、调试运行时、JVM 测试、设备测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.savedstate:savedstate-android:1.3.2` | 正式运行时、调试运行时、JVM 测试、设备测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.savedstate:savedstate-compose-android:1.3.2` | 正式运行时、调试运行时、JVM 测试、设备测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.savedstate:savedstate-ktx:1.3.2` | 正式运行时、调试运行时、JVM 测试、设备测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.startup:startup-runtime:1.1.1` | 正式运行时、调试运行时、JVM 测试、设备测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.test.espresso:espresso-core:3.7.0` | 设备测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.test.espresso:espresso-idling-resource:3.7.0` | 设备测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.test.ext:junit:1.3.0` | 设备测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.test.services:storage:1.6.0` | 设备测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.test:core:1.7.0` | 设备测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.test:monitor:1.8.0` | 设备测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.test:runner:1.7.0` | 设备测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.tracing:tracing:1.2.0` | 正式运行时、调试运行时、JVM 测试、设备测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.transition:transition:1.6.0` | 正式运行时、调试运行时、JVM 测试、设备测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.versionedparcelable:versionedparcelable:1.1.1` | 正式运行时、调试运行时、JVM 测试、设备测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.window:window-core-android:1.5.0` | 正式运行时、调试运行时、JVM 测试、设备测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `androidx.window:window:1.5.0` | 正式运行时、调试运行时、JVM 测试、设备测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `com.google.code.findbugs:jsr305:3.0.2` | 设备测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `com.google.errorprone:error_prone_annotations:2.30.0` | 设备测试 | [Apache 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `com.google.guava:listenablefuture:1.0` | 正式运行时、调试运行时、JVM 测试、设备测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `javax.inject:javax.inject:1` | 设备测试 | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `junit:junit:4.13.2` | JVM 测试、设备测试 | [Eclipse Public License 1.0](http://www.eclipse.org/legal/epl-v10.html) |
| `org.hamcrest:hamcrest-core:1.3` | JVM 测试、设备测试 | [New BSD License](http://www.opensource.org/licenses/bsd-license.php) |
| `org.hamcrest:hamcrest-library:1.3` | 设备测试 | [New BSD License](http://www.opensource.org/licenses/bsd-license.php) |
| `org.jetbrains.kotlin:kotlin-stdlib:2.2.10` | 正式运行时、调试运行时、JVM 测试、设备测试 | [The Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0` | 正式运行时、调试运行时、JVM 测试、设备测试 | [The Apache Software License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0.txt) |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.9.0` | 正式运行时、调试运行时、JVM 测试、设备测试 | [The Apache Software License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0.txt) |
| `org.jetbrains.kotlinx:kotlinx-coroutines-test-jvm:1.9.0` | 设备测试 | [The Apache Software License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0.txt) |
| `org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.7.3` | 正式运行时、调试运行时、JVM 测试、设备测试 | [The Apache Software License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0.txt) |
| `org.jetbrains:annotations:23.0.0` | 正式运行时、调试运行时、JVM 测试、设备测试 | [The Apache Software License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0.txt) |
| `org.jspecify:jspecify:1.0.0` | 正式运行时、调试运行时、JVM 测试、设备测试 | [The Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) |
