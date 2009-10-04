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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;

import org.apache.tools.ant.BuildEvent;
import org.apache.tools.ant.DefaultLogger;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.util.FileUtils;
import org.apache.tools.ant.util.StringUtils;

/**
 * Implementation of a logger which in compatible with a parallel execution of
 * targets.
 * @see ParallelExecutor
 */
public class ParallelExecutorLogger extends DefaultLogger {

    private static final int LEFT_COLUMN_SIZE = 26;

    private static final String  STARTED_SYMBOL = "+ ";
    private static final String FINISHED_SYMBOL = "- ";

    public void targetStarted(BuildEvent event) {
        if (Project.MSG_INFO <= msgOutputLevel
                && !event.getTarget().getName().equals("")) {
            String msg = STARTED_SYMBOL
                    + event.getTarget().getName();
            printMessage(msg, out, event.getPriority());
            log(msg);
        }
    }

    public void targetFinished(BuildEvent event) {
        if (Project.MSG_INFO <= msgOutputLevel
                && !event.getTarget().getName().equals("")) {
            String msg = FINISHED_SYMBOL
                    + event.getTarget().getName();
            printMessage(msg, out, event.getPriority());
            log(msg);
        }
    }

    public void messageLogged(BuildEvent event) {
        int priority = event.getPriority();
        // Filter out messages based on priority
        if (priority <= msgOutputLevel) {

            StringBuffer message = new StringBuffer();
            if (event.getTask() != null && !emacsMode) {
                // Print out the name of the (target, task) if we're in one
                String targetName = event.getTask().getOwningTarget().getName();
                String taskName = event.getTask().getTaskName();
                String label = "[" + targetName + " / " + taskName + "] ";
                int size = LEFT_COLUMN_SIZE - label.length();
                StringBuffer tmp = new StringBuffer();
                for (int i = 0; i < size + 1; i++) {
                    tmp.append(" ");
                }
                tmp.append(label);
                label = tmp.toString();

                BufferedReader r = null;
                try {
                    r = new BufferedReader(new StringReader(event.getMessage()));
                    String line = r.readLine();
                    boolean first = true;
                    do {
                        if (first) {
                            if (line == null) {
                                message.append(label);
                                break;
                            }
                        } else {
                            message.append(StringUtils.LINE_SEP);
                        }
                        first = false;
                        message.append(label).append(line);
                        line = r.readLine();
                    } while (line != null);
                } catch (IOException e) {
                    // shouldn't be possible
                    message.append(label).append(event.getMessage());
                } finally {
                    if (r != null) {
                        FileUtils.close(r);
                    }
                }

            } else {
                // emacs mode or there is no task
                message.append(event.getMessage());
            }
            Throwable ex = event.getException();
            if (Project.MSG_DEBUG <= msgOutputLevel && ex != null) {
                message.append(StringUtils.getStackTrace(ex));
            }

            String msg = message.toString();
            if (priority != Project.MSG_ERR) {
                printMessage(msg, out, priority);
            } else {
                printMessage(msg, err, priority);
            }
            log(msg);
        }
    }

    protected void printMessage(final String message, final PrintStream stream,
            final int priority) {
        synchronized (this) {
            super.printMessage(message, stream, priority);
        }
    }

}
