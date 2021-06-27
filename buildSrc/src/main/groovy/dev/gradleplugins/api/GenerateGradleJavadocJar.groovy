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
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkerExecutor

import javax.inject.Inject

@CompileStatic
abstract class GenerateGradleJavadocJar extends DefaultTask {
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
    GenerateGradleJavadocJar() {
		getOutputFile().value(layout.buildDirectory.file(version.map { "tmp/${this.name}/build/distributions/gradle-${it}-javadoc.jar" })).disallowChanges()
	}

	@TaskAction
	private void doGenerate() {
		getWorkerExecutor().processIsolation {
			it.classpath.from(this.classpath)
		}.submit(ExecuteGradleAction.class) { param ->
			param.gradleUserHomeDirectory.set(layout.buildDirectory.dir('gradle-user-home'))
			param.workingDirectory.set(this.temporaryDir)
			param.version.set('6.5') // unrelated to target Gradle version
			param.buildscript.set("""
			|buildscript {
			|	dependencies {
			|		classpath 'io.github.http-builder-ng:http-builder-ng-core:1.0.4'
			|	}
			|	repositories {
			|		mavenCentral()
			|	}
			|}
			|
			|apply plugin: 'java'
			|import groovyx.net.http.HttpBuilder
			|import groovyx.net.http.optional.Download
			|tasks.create('download') {
			|	ext.outputFile = file("gradle-javadoc-${this.version.get()}.zip")
			|	outputs.file(outputFile)
			|	inputs.property('gradleVersion', gradle.gradleVersion)
			|	doFirst {
			|		File file = HttpBuilder.configure {
			|			request.uri = "https://services.gradle.org/distributions/gradle-${this.version.get()}-all.zip"
			|			request.headers.put('User-Agent', 'gradle-api-extractor');
			|		}.get {
			|			Download.toFile(delegate, outputFile)
			|		}
			|	}
			|}
			|tasks.create('generate', Zip) {
			|	dependsOn(tasks.download)
			|	from({zipTree(tasks.download.outputFile).matching { include('*/docs/javadoc/**/*') } }) {
			|		eachFile {
			|			relativePath = new RelativePath(relativePath.file, relativePath.segments.drop(3))
			|		}
			|		includeEmptyDirs = false
			|	}
			|	baseName = 'gradle'
			|	version = '${this.getVersion().get()}'
			|	extension = 'jar'
			|	classifier = 'javadoc'
			|}
			|""".stripMargin())
			param.tasks.set(['generate'])
		}
	}
}
