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

import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Target;

/**
 * Stores the graph of dependencies between targets.
 */
public class DependencyTree {

    private Map<String, Set<String>> dependencies;
    
    // Stores both direct and transitive dependencies
    private Map<String, Set<String>> flatDependencies;

    /**
     * Create a new dependency tree.
     *
     * @param project the project
     * @param targetName the name of the target to run
     * @throws BuildException if there is a circular dependency
     */
    public DependencyTree(Project project, String targetName) {
        dependencies = new HashMap<String, Set<String>>();
        Hashtable<?, ?> targets = project.getTargets(); // name -> target
        addDependency(project, targets, targetName);
        flatDependencies = flattenDependencyTree();
    }

    /**
     * Get the dependency tree.
     *
     * @return the dependency tree
     */
    public Map<String, Set<String>> getDependencies() {
        return new HashMap<String, Set<String>>(dependencies);
    }

    /**
     * Get a comparator for targets so that t1 < t2 if t1 should be run before t2.
     *
     * @return a target comparator
     */
    public Comparator<Target> getComparator() {
        return new DependencyComparator(flatDependencies);
    }

    /**
     * Fill the dependency tree.
     *
     * @param project the project
     * @param targets the name to target map
     * @param targetName the name of the target we're adding to the tree
     */
    private void addDependency(Project project, Hashtable<?, ?> targets, String targetName) {
        if (dependencies.get(targetName) == null) {
            Set<String> deps = new HashSet<String>();
            dependencies.put(targetName, deps);
            Target target = (Target)targets.get(targetName);
            for (Enumeration<?> depIter = target.getDependencies();
                    depIter.hasMoreElements(); ) {
                String depName = (String)depIter.nextElement();
                deps.add(depName);
                addDependency(project, targets, depName);
            }
        }
    }

    /**
     * Flatten the dependency tree.
     *
     * @return the flattened dependency tree
     * @throws BuildException if there are circular dependencies
     */
    private Map<String, Set<String>> flattenDependencyTree() {
        final Map<String, Set<String>> newDeps = new HashMap<String, Set<String>>();
        for (Iterator<String> it = dependencies.keySet().iterator(); it.hasNext();) {
            String targetName = it.next();
            Set<String> flattenDeps = new HashSet<String>();
            flattenDependencyTree(targetName, new Stack<String>(), flattenDeps);
            newDeps.put(targetName, flattenDeps);
        }
        return newDeps;
    }

    @SuppressWarnings("unchecked")
    private void flattenDependencyTree(String targetName, Stack<String> stack, Set<String> flattenDeps) {
        if (stack.contains(targetName)) {
            throw makeCircularException((Stack<String>)stack.clone(), targetName);
        } else {
            stack.add(targetName);
        }
        Set<String> deps = dependencies.get(targetName);
        for (Iterator<String> it = deps.iterator(); it.hasNext(); ) {
            String depName = it.next();
            flattenDeps.add(depName);
            flattenDependencyTree(depName, (Stack<String>)stack.clone(), flattenDeps);
        }
    }

    private BuildException makeCircularException(Stack<String> stack, String depName) {
        StringBuilder builder = new StringBuilder(depName);
        while (true) {
            String targetName = stack.pop();
            if (targetName == null) {
                break;
            }
            builder.insert(0, " --> ");
            builder.insert(0, targetName);
            if (targetName.equals(depName)) {
                break;
            }
        }
        builder.insert(0, "Circular dependency: ");
        return new BuildException(builder.toString());
    }

    /**
     * A comparator for targets so that t1 < t2 => t1 should be run before t2.
     */
    private static class DependencyComparator implements Comparator<Target> {

        private Map<String, Set<String>> flatDependencies;

        public DependencyComparator(Map<String, Set<String>> flatDependencies) {
            this.flatDependencies = flatDependencies;
        }

        public int compare(Target target0, Target target1) {
            String t0 = target0.getName();
            String t1 = target1.getName();
            Set<String> t0Deps = flatDependencies.get(t0);
            Set<String> t1Deps = flatDependencies.get(t1);

            if (t0Deps.equals(t1Deps)) {
                return 0;
            } else if (t1Deps.isEmpty() || t0Deps.contains(t1)) {
                return 1;
            } else if (t0Deps.isEmpty() || t1Deps.contains(t0)) {
                return -1;
            }
            return t0Deps.size() - t1Deps.size();
        }

    }

}
