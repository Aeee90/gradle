/*
 * Copyright 2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.tasks.compile;

import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.tasks.compile.daemon.DaemonGroovyCompiler;
import org.gradle.api.internal.tasks.compile.processing.AnnotationProcessorDetector;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.compile.GroovyCompileOptions;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.jvm.inspection.JvmVersionDetector;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.language.base.internal.compile.CompilerFactory;
import org.gradle.process.internal.DefaultExecActionFactory;
import org.gradle.process.internal.JavaForkOptionsFactory;
import org.gradle.process.internal.worker.child.WorkerDirectoryProvider;
import org.gradle.workers.internal.IsolatedClassloaderWorkerFactory;
import org.gradle.workers.internal.WorkerDaemonFactory;
import org.gradle.workers.internal.WorkerFactory;

import java.io.Serializable;

public class GroovyCompilerFactory implements CompilerFactory<GroovyJavaJointCompileSpec> {
    private final WorkerDaemonFactory workerDaemonFactory;
    private final IsolatedClassloaderWorkerFactory inProcessWorkerFactory;
    private final JavaForkOptionsFactory forkOptionsFactory;
    private final AnnotationProcessorDetector processorDetector;
    private final JvmVersionDetector jvmVersionDetector;
    private final WorkerDirectoryProvider workerDirectoryProvider;
    private final ClassPathRegistry classPathRegistry;

    public GroovyCompilerFactory(WorkerDaemonFactory workerDaemonFactory, IsolatedClassloaderWorkerFactory inProcessWorkerFactory, JavaForkOptionsFactory forkOptionsFactory, AnnotationProcessorDetector processorDetector, JvmVersionDetector jvmVersionDetector, WorkerDirectoryProvider workerDirectoryProvider, ClassPathRegistry classPathRegistry) {
        this.workerDaemonFactory = workerDaemonFactory;
        this.inProcessWorkerFactory = inProcessWorkerFactory;
        this.forkOptionsFactory = forkOptionsFactory;
        this.processorDetector = processorDetector;
        this.jvmVersionDetector = jvmVersionDetector;
        this.workerDirectoryProvider = workerDirectoryProvider;
        this.classPathRegistry = classPathRegistry;
    }

    @Override
    public Compiler<GroovyJavaJointCompileSpec> newCompiler(GroovyJavaJointCompileSpec spec) {
        GroovyCompileOptions groovyOptions = spec.getGroovyCompileOptions();
        WorkerFactory workerFactory;
        if (groovyOptions.isFork()) {
            workerFactory = workerDaemonFactory;
        } else {
            workerFactory = inProcessWorkerFactory;
        }
        Compiler<GroovyJavaJointCompileSpec> groovyCompiler = new DaemonGroovyCompiler(workerDirectoryProvider.getWorkingDirectory(), DaemonSideCompiler.class, classPathRegistry, workerFactory, forkOptionsFactory, jvmVersionDetector);
        return new AnnotationProcessorDiscoveringCompiler<GroovyJavaJointCompileSpec>(new NormalizingGroovyCompiler(groovyCompiler), processorDetector);
    }

    static class DaemonSideCompiler implements Compiler<GroovyJavaJointCompileSpec>, Serializable {
        @Override
        public WorkResult execute(GroovyJavaJointCompileSpec spec) {
            DefaultExecActionFactory execHandleFactory = DefaultExecActionFactory.root();
            try {
                Compiler<JavaCompileSpec> javaCompiler;
                if (CommandLineJavaCompileSpec.class.isAssignableFrom(spec.getClass())) {
                    javaCompiler = new CommandLineJavaCompiler(execHandleFactory);
                } else {
                    javaCompiler = new JdkJavaCompiler(new JavaHomeBasedJavaCompilerFactory());
                }
                Compiler<GroovyJavaJointCompileSpec> groovyCompiler = new ApiGroovyCompiler(javaCompiler);
                return groovyCompiler.execute(spec);
            } finally {
                CompositeStoppable.stoppable(execHandleFactory).stop();
            }
        }
    }
}
