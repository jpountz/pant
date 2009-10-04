/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package net.jpountz.ant.helper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Executor;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Target;
import org.apache.tools.ant.helper.SingleCheckExecutor;

/**
 * An executor which allows parallel execution of targets.
 */
public class ParallelExecutor implements Executor {

    private static final String ANT_EXECUTOR_THREADCOUNT = "ant.executor.threadcount";

    private static final int DEFAULT_THREADCOUNT = 1;

    private static final SingleCheckExecutor SUB_EXECUTOR = new SingleCheckExecutor();

    @Override
    public void executeTargets(Project project, String[] targetNames)
            throws BuildException {
        for (int i = 0; i < targetNames.length; ++i) {
            executeTarget(project, targetNames[i]);
        }
    }

    /**
     * Get the thread count from the {@value #ANT_EXECUTOR_THREADCOUNT} property. 
     *
     * @param project the project
     * @return the thread count
     */
    private int getThreadCount(Project project) {
        String threadCountAsString = project.getProperty(ANT_EXECUTOR_THREADCOUNT);
        int threadCount = DEFAULT_THREADCOUNT;
        if (threadCountAsString != null) {
            threadCount = Integer.parseInt(threadCountAsString);
        }
        if (threadCount <  1) {
            throw new IllegalArgumentException(ANT_EXECUTOR_THREADCOUNT + " must be >= 1");
        }
        return threadCount;
    }

    /**
     * Run a target and all its dependencies in several threads.
     *
     * @param project the project
     * @param targetName the name of the target to run
     * @throws BuildException an error occurred
     */
    public void executeTarget(Project project, String targetName)
            throws BuildException {
        final DependencyTree dependencyTree = new DependencyTree(project, targetName);
        Map<String, Set<String>> deps = dependencyTree.getDependencies();

        /* Get the thread count and create the thread pool */
        int threadCount = getThreadCount(project);
        project.log("Running tasks in " + threadCount + " thread" +
                (threadCount > 1 ? "s" : ""), Project.MSG_INFO);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        /* Sort targets. Targets with fewer dependencies first. */
        List<Target> targets = new ArrayList<Target>(deps.size());
        Hashtable<?, ?> nameToTarget = project.getTargets();
        for (String depName : deps.keySet()) {
            Target dep = (Target)nameToTarget.get(depName);
            if (dep != null) {
                targets.add(dep);
            } else {
                throw new BuildException("No definition for target: \"" + depName + '"');
            }
        }
        Comparator<Target> comparator = dependencyTree.getComparator();
        Collections.sort(targets, comparator);

        /* Prepare to run  */
        final ThrowableWrapper error = new ThrowableWrapper();
        final Map<String, CountDownLatch> doneSignals = new HashMap<String, CountDownLatch>();
        List<TargetRunnable> targetRunnables = new ArrayList<TargetRunnable>(targets.size());
        for (Iterator<Target> it = targets.iterator(); it.hasNext(); ) {
            Target target = it.next();
            TargetRunnable te = new TargetRunnable(project, target,
                    deps.get(target.getName()), doneSignals, error);
            targetRunnables.add(te);
        }

        /* Blast off! */
        for (Iterator<TargetRunnable> it = targetRunnables.iterator(); it.hasNext(); ) {
            TargetRunnable te = it.next();
            executor.execute(te);
        }

        /* Back on earth */
        executor.shutdown();
        while (true) {
            try {
                if (executor.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS)) {
                  break;
                }
            } catch (InterruptedException e) { }
        }

        /* Is the rocket damaged? */
        Throwable t = error.getThrowable();
        if (t != null) {
            if (t instanceof BuildException) {
                throw (BuildException)t;
            } else {
                throw new BuildException(t);
            }
        }
    }

    public Executor getSubProjectExecutor() {
        return SUB_EXECUTOR;
    }

    private static class TargetRunnable implements Runnable {

        private ThrowableWrapper error;
        private Project project;
        private Target target;
        private Set<String> deps;
        private CountDownLatch doneSignal;
        private Map<String, CountDownLatch> doneSignals;

        public TargetRunnable(Project project, Target target, Set<String> deps,
                Map<String, CountDownLatch> doneSignals, ThrowableWrapper error) {
            this.project = project;
            this.error = error;
            this.target = target;
            this.deps = deps;
            this.doneSignals = doneSignals;
            doneSignal = new CountDownLatch(1);
            doneSignals.put(target.getName(), doneSignal);
        }

        public void run() {
            try {
                for (String targetName : deps) {
                    CountDownLatch targetDoneSignal = doneSignals.get(targetName);
                    targetDoneSignal.await();
                }
                if (project.isKeepGoingMode() || error.getThrowable() == null) {
                    target.performTasks();
                }
            } catch (Throwable t) {
                error.setThrowable(t);
            } finally {
                doneSignal.countDown();
            }
        }

    }

    /**
     * A wrapper around a {@link Throwable}.
     */
    private static class ThrowableWrapper {

        private volatile Throwable t = null;

        public Throwable getThrowable() { return t; }
        public void setThrowable(Throwable t) {
            if (t != null) {
                this.t = t;
            }
        }

    }

}
