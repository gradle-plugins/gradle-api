package dev.gradleplugins.api

import dev.gradleplugins.fixtures.file.FileSystemFixture
import dev.gradleplugins.fixtures.runnerkit.GradleScriptFixture
import dev.gradleplugins.runnerkit.GradleExecutor
import dev.gradleplugins.runnerkit.GradleRunner
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters

abstract class ExecuteGradleAction implements WorkAction<Parameters>, FileSystemFixture, GradleScriptFixture {
    static interface Parameters extends WorkParameters {
        DirectoryProperty getWorkingDirectory();
        DirectoryProperty getGradleUserHomeDirectory();

        Property<String> getVersion();
        Property<String> getBuildscript();

        ListProperty<String> getTasks();
    }

    @Override
    File getTestDirectory() {
        return parameters.workingDirectory.get().asFile
    }

    @Override
    void execute() {
        testDirectory.mkdirs()

        writeBuildscriptFile()

        GradleRunner.create(GradleExecutor.gradleTestKit())
                .inDirectory(testDirectory)
                .withGradleUserHomeDirectory(parameters.gradleUserHomeDirectory.get().asFile)
                .withGradleVersion(parameters.version.get())
                .withTasks(parameters.tasks.get())
                .withoutDeprecationChecks()
                .build()
    }

    private void writeBuildscriptFile() {
        buildFile.text = parameters.buildscript.get()
    }
}
