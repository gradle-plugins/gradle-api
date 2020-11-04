/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.gradleplugins.api

import groovy.transform.CompileStatic
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkerExecutor

import javax.inject.Inject

@CompileStatic
abstract class GenerateGradleTestKitApiJar extends DefaultTask {
    @Input
    abstract Property<String> getVersion();

    @Classpath
    abstract ConfigurableFileCollection getClasspath()

    @OutputFile
    abstract RegularFileProperty getOutputFile()

    @Inject
    protected abstract WorkerExecutor getWorkerExecutor()

    @Inject
    protected abstract ProjectLayout getLayout()

    @Inject
    GenerateGradleTestKitApiJar() {
        getOutputFile().value(layout.buildDirectory.file(version.map { "gradle-user-home/caches/${it}/generated-gradle-jars/gradle-test-kit-${it}.jar" })).disallowChanges()
    }

    @TaskAction
    private void doGenerate() {
        getWorkerExecutor().processIsolation {
            it.getClasspath().from(this.classpath)
        }.submit(ExecuteGradleAction.class) { param ->
            param.gradleUserHomeDirectory.set(layout.buildDirectory.dir('gradle-user-home'))
            param.workingDirectory.set(this.temporaryDir)
            param.version.set(this.version)
            param.buildscript.set('''
            |def configuration = configurations.create('generator')
            |dependencies {
            |   generator gradleTestKit()
            |}
            |tasks.create('generate') {
            |   doLast {
            |       configuration.resolve()
            |   }
            |}
            |'''.stripMargin())
            param.tasks.set(['generate'])
        }
    }
}
