/*
 * The MIT License
 *
 * Copyright 2016 CloudBees, Inc
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package it.dockins.dockerslaves.pipeline;

import hudson.AbortException;
import hudson.Extension;
import hudson.model.queue.QueueListener;
import it.dockins.dockerslaves.DockerSlave;
import it.dockins.dockerslaves.DockerSlaves;
import it.dockins.dockerslaves.spec.ContainerSetDefinition;
import it.dockins.dockerslaves.spec.DockerSocketContainerDefinition;
import it.dockins.dockerslaves.spec.ImageIdContainerDefinition;
import it.dockins.dockerslaves.spec.SideContainerDefinition;
import com.google.inject.Inject;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Computer;
import hudson.model.Executor;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.ResourceList;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.model.queue.CauseOfBlockage;
import hudson.model.queue.SubTask;
import hudson.remoting.ChannelClosedException;
import hudson.remoting.RequestAbortedException;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.security.Permission;
import hudson.slaves.WorkspaceList;
import jenkins.model.Jenkins;
import jenkins.model.queue.AsynchronousExecution;
import jenkins.util.Timer;
import org.acegisecurity.AccessDeniedException;
import org.acegisecurity.Authentication;
import org.jenkinsci.plugins.durabletask.executors.ContinuableExecutable;
import org.jenkinsci.plugins.durabletask.executors.ContinuedTask;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.jenkinsci.plugins.workflow.support.actions.WorkspaceActionImpl;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.export.ExportedBean;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.logging.Level.FINE;
import static java.util.logging.Level.WARNING;

/**
 * This class is heavily based (so to speak...) on org.jenkinsci.plugins.workflow.support.steps.ExecutorStepExecution
 * The original code cannot be easily extended, PlaceholderTask constructor is private for example.
 *
 * As much as we hate copy / pasting, as an experimental implementation, we feel that it is still interesting
 * to test the approach.
 *
 * See https://github.com/jenkinsci/workflow-durable-task-step-plugin/pull/15 for a discussion about that.
 */
public class DockerNodeStepExecution extends AbstractStepExecutionImpl {

    @Inject(optional = true)
    private transient DockerNodeStep step;
    @StepContextParameter
    private transient TaskListener listener;
    @StepContextParameter
    private transient Run<?, ?> run;
    // Here just for requiredContext; could perhaps be passed to the PlaceholderTask constructor:
    @StepContextParameter
    private transient FlowExecution flowExecution;
    @StepContextParameter
    private transient FlowNode flowNode;

    /*
     * General strategy of this step.
     * <p/>
     * 1. schedule {@link PlaceholderTask} into the {@link Queue} (what this method does)
     * 2. when {@link PlaceholderTask} starts running, invoke the closure
     * 3. when the closure is done, let {@link PlaceholderTask} complete
     */
    @Override
    public boolean start() throws Exception {
        final String label = "docker_" + Long.toHexString(System.nanoTime());
        final PlaceholderTask task = new PlaceholderTask(getContext(), label, run);

        final DockerSlaves cloud = DockerSlaves.get();

        String slaveName = "Container for " + run.toString() + "." + flowNode.getId();
        String description = "Container building " + run.getParent().getFullName();

        Queue.Item item = Queue.getInstance().schedule2(task, 0).getCreateItem();
        if (item == null) {
            // There can be no duplicates. But could be refused if a QueueDecisionHandler rejects it for some odd reason.
            throw new IllegalStateException("failed to schedule task");
        }

        List<SideContainerDefinition> sideContainers = new ArrayList<>();
        if (step.getSideContainers() != null) {
            for (String entry : step.getSideContainers()) {
                sideContainers.add(new SideContainerDefinition(entry,
                        new ImageIdContainerDefinition(entry, false)));
            }
        }
        if(step.getSocket()) {
            sideContainers.add(new SideContainerDefinition("socket", new DockerSocketContainerDefinition()));
        }

        ContainerSetDefinition spec = new ContainerSetDefinition(
                new ImageIdContainerDefinition(step.getImage(), false), sideContainers, null);

        final Node node = new DockerSlave(slaveName, description, label,
                cloud.createProvisionerForPipeline(run.getParent(), spec), item);

        Jenkins.getInstance().addNode(node);

        Timer.get().schedule(new Runnable() {
            @Override public void run() {
                Queue.Item item = Queue.getInstance().getItem(task);
                if (item != null) {
                    PrintStream logger;
                    try {
                        logger = listener.getLogger();
                    } catch (Exception x) { // IOException, InterruptedException
                        LOGGER.log(WARNING, null, x);
                        return;
                    }
                    logger.println("Still waiting to schedule task");
                    String why = item.getWhy();
                    if (why != null) {
                        logger.println(why);
                    }
                }
            }
        }, 15, TimeUnit.SECONDS);
        return false;
    }

    @Override
    public void stop(Throwable cause) {
        for (Queue.Item item : Queue.getInstance().getItems()) {
            // if we are still in the queue waiting to be scheduled, just retract that
            if (item.task instanceof PlaceholderTask && ((PlaceholderTask) item.task).context.equals(getContext())) {
                Queue.getInstance().cancel(item);
                break;
            }
        }
        Jenkins j = Jenkins.getInstance();
        if (j != null) {
            // if we are already running, kill the ongoing activities, which releases PlaceholderExecutable from its sleep loop
            // Similar to Executor.of, but distinct since we do not have the Executable yet:
            COMPUTERS: for (Computer c : j.getComputers()) {
                for (Executor e : c.getExecutors()) {
                    Queue.Executable exec = e.getCurrentExecutable();
                    if (exec instanceof PlaceholderTask.PlaceholderExecutable && ((PlaceholderTask.PlaceholderExecutable) exec).getParent().context.equals(getContext())) {
                        PlaceholderTask.finish(((PlaceholderTask.PlaceholderExecutable) exec).getParent().cookie);
                        break COMPUTERS;
                    }
                }
            }
        }
        // Whether or not either of the above worked (and they would not if for example our item were canceled), make sure we die.
        getContext().onFailure(cause);
    }

    @Override public void onResume() {
        super.onResume();
        // See if we are still running, or scheduled to run. Cf. stop logic above.
        for (Queue.Item item : Queue.getInstance().getItems()) {
            if (item.task instanceof PlaceholderTask && ((PlaceholderTask) item.task).context.equals(getContext())) {
                LOGGER.log(FINE, "Queue item for node block in {0} is still waiting after reload", run);
                return;
            }
        }
        Jenkins j = Jenkins.getInstance();
        if (j != null) {
            COMPUTERS: for (Computer c : j.getComputers()) {
                for (Executor e : c.getExecutors()) {
                    Queue.Executable exec = e.getCurrentExecutable();
                    if (exec instanceof PlaceholderTask.PlaceholderExecutable && ((PlaceholderTask.PlaceholderExecutable) exec).getParent().context.equals(getContext())) {
                        LOGGER.log(FINE, "Node block in {0} is running on {1} after reload", new Object[] {run, c.getName()});
                        return;
                    }
                }
            }
        }
        if (step == null) { // compatibility: used to be transient
            listener.getLogger().println("Queue item for node block in " + run.getFullDisplayName() + " is missing (perhaps JENKINS-34281), but cannot reschedule");
            return;
        }
        listener.getLogger().println("Queue item for node block in " + run.getFullDisplayName() + " is missing (perhaps JENKINS-34281); rescheduling");
        try {
            start();
        } catch (Exception x) {
            getContext().onFailure(x);
        }
    }

    @Override public String getStatus() {
        // Yet another copy of the same logic; perhaps this should be factored into some method returning a union of Queue.Item and PlaceholderExecutable?
        for (Queue.Item item : Queue.getInstance().getItems()) {
            if (item.task instanceof PlaceholderTask && ((PlaceholderTask) item.task).context.equals(getContext())) {
                return "waiting for " + item.task.getFullDisplayName() + " to be scheduled; blocked: " + item.getWhy();
            }
        }
        Jenkins j = Jenkins.getInstance();
        if (j != null) {
            COMPUTERS: for (Computer c : j.getComputers()) {
                for (Executor e : c.getExecutors()) {
                    Queue.Executable exec = e.getCurrentExecutable();
                    if (exec instanceof PlaceholderTask.PlaceholderExecutable && ((PlaceholderTask.PlaceholderExecutable) exec).getParent().context.equals(getContext())) {
                        return "running on " + c.getName();
                    }
                }
            }
        }
        return "node block appears to be neither running nor scheduled";
    }

    @Extension public static class CancelledItemListener extends QueueListener {

        @Override public void onLeft(Queue.LeftItem li) {
            if (li.isCancelled()) {
                if (li.task instanceof PlaceholderTask) {
                    (((PlaceholderTask) li.task).context).onFailure(new AbortException(Messages.DockerNodeStepExecution_queue_task_cancelled()));
                }
            }
        }

    }

    /** Transient handle of a running executor task. */
    private static final class RunningTask {
        /** null until placeholder executable runs */
        @Nullable AsynchronousExecution execution;
        /** null until placeholder executable runs */
        @Nullable Launcher launcher;
    }

    private static final String COOKIE_VAR = "JENKINS_SERVER_COOKIE";

    @ExportedBean
    public static final class PlaceholderTask implements ContinuedTask, Serializable, AccessControlled {

        /** keys are {@link #cookie}s */
        private static final Map<String,RunningTask> runningTasks = new HashMap<String,RunningTask>();

        private final StepContext context;
        private String label;
        /** Shortcut for {@link #run}. */
        private String runId;
        /**
         * Unique cookie set once the task starts.
         * Serves multiple purposes:
         * identifies whether we have already invoked the body (since this can be rerun after restart);
         * serves as a key for {@link #runningTasks} and {@link Callback} (cannot just have a doneness flag in {@link PlaceholderTask} because multiple copies might be deserialized);
         * and allows {@link Launcher#kill} to work.
         */
        private String cookie;

        PlaceholderTask(StepContext context, String label, Run<?,?> run) {
            this.context = context;
            this.label = label;
            runId = run.getExternalizableId();
        }

        private Object readResolve() {
            LOGGER.log(FINE, "deserialized {0}", cookie);
            if (cookie != null) {
                synchronized (runningTasks) {
                    runningTasks.put(cookie, new RunningTask());
                }
            }
            return this;
        }

        @Override public Queue.Executable createExecutable() throws IOException {
            return new PlaceholderExecutable();
        }

        @Override public Label getAssignedLabel() {
            if (label == null) {
                return null;
            } else if (label.isEmpty()) {
                Jenkins j = Jenkins.getInstance();
                if (j == null) {
                    return null;
                }
                return j.getSelfLabel();
            } else {
                return Label.get(label);
            }
        }

        @Override public Node getLastBuiltOn() {
            if (label == null) {
                return null;
            }
            Jenkins j = Jenkins.getInstance();
            if (j == null) {
                return null;
            }
            return j.getNode(label);
        }

        @Override public boolean isBuildBlocked() {
            return false;
        }

        @Deprecated
        @Override public String getWhyBlocked() {
            return null;
        }

        @Override public CauseOfBlockage getCauseOfBlockage() {
            return null;
        }

        @Override public boolean isConcurrentBuild() {
            return false;
        }

        @Override public Collection<? extends SubTask> getSubTasks() {
            return Collections.singleton(this);
        }

        @Override public Queue.Task getOwnerTask() {
            Run<?,?> r = run();
            if (r != null && r.getParent() instanceof Queue.Task) {
                return (Queue.Task) r.getParent();
            } else {
                return this;
            }
        }

        @Override public Object getSameNodeConstraint() {
            return null;
        }

        /**
         * Something we can use to check abort and read permissions.
         * Normally this will be a {@link Run}.
         * However if things are badly broken, for example if the build has been deleted,
         * then as a fallback we use the Jenkins root.
         * This allows an administrator to clean up dead queue items and executor cells.
         * TODO make {@link FlowExecutionOwner} implement {@link AccessControlled}
         * so that an implementation could fall back to checking {@link Job} permission.
         */
        @Override public ACL getACL() {
            try {
                if (!context.isReady()) {
                    return Jenkins.getInstance().getACL();
                }
                FlowExecution exec = context.get(FlowExecution.class);
                if (exec == null) {
                    return Jenkins.getInstance().getACL();
                }
                Queue.Executable executable = exec.getOwner().getExecutable();
                if (executable instanceof AccessControlled) {
                    return ((AccessControlled) executable).getACL();
                } else {
                    return Jenkins.getInstance().getACL();
                }
            } catch (Exception x) {
                LOGGER.log(FINE, null, x);
                return Jenkins.getInstance().getACL();
            }
        }

        @Override public void checkPermission(Permission p) throws AccessDeniedException {
            getACL().checkPermission(p);
        }

        @Override public boolean hasPermission(Permission p) {
            return getACL().hasPermission(p);
        }

        @Override public void checkAbortPermission() {
            checkPermission(Item.CANCEL);
        }

        @Override public boolean hasAbortPermission() {
            return hasPermission(Item.CANCEL);
        }

        public @CheckForNull Run<?,?> run() {
            try {
                if (!context.isReady()) {
                    return null;
                }
                return context.get(Run.class);
            } catch (Exception x) {
                LOGGER.log(FINE, "broken " + cookie, x);
                finish(cookie); // probably broken, so just shut it down
                return null;
            }
        }

        public @CheckForNull Run<?,?> runForDisplay() {
            Run<?,?> r = run();
            if (r == null && /* not stored prior to 1.13 */runId != null) {
                return Run.fromExternalizableId(runId);
            }
            return r;
        }

        @Override public String getUrl() {
            // TODO ideally this would be found via FlowExecution.owner.executable, but how do we check for something with a URL? There is no marker interface for it: JENKINS-26091
            Run<?,?> r = runForDisplay();
            return r != null ? r.getUrl() : "";
        }

        @Override public String getDisplayName() {
            // TODO more generic to check whether FlowExecution.owner.executable is a ModelObject
            Run<?,?> r = runForDisplay();
            return r != null ? Messages.DockerNodeStepExecution_PlaceholderTask_displayName(r.getFullDisplayName()) : Messages.DockerNodeStepExecution_PlaceholderTask_displayName_unknown();
        }

        @Override public String getName() {
            return getDisplayName();
        }

        @Override public String getFullDisplayName() {
            return getDisplayName();
        }

        @Override public long getEstimatedDuration() {
            Run<?,?> r = run();
            // Not accurate if there are multiple slaves in one build, but better than nothing:
            return r != null ? r.getEstimatedDuration() : -1;
        }

        @Override public ResourceList getResourceList() {
            return new ResourceList();
        }

        @Override public Authentication getDefaultAuthentication() {
            return ACL.SYSTEM; // TODO should pick up credentials from configuring user or something
        }

        @Override public Authentication getDefaultAuthentication(Queue.Item item) {
            return getDefaultAuthentication();
        }

        @Override public boolean isContinued() {
            return cookie != null; // in which case this is after a restart and we still claim the executor
        }

        private static void finish(@CheckForNull final String cookie) {
            if (cookie == null) {
                return;
            }
            synchronized (runningTasks) {
                final RunningTask runningTask = runningTasks.remove(cookie);
                if (runningTask == null) {
                    LOGGER.log(FINE, "no running task corresponds to {0}", cookie);
                    return;
                }
                final AsynchronousExecution execution = runningTask.execution;
                if (execution == null) {
                    // JENKINS-30759: finished before asynch execution was even scheduled
                    return;
                }
                assert runningTask.launcher != null;
                Timer.get().submit(new Runnable() { // JENKINS-31614
                    @Override public void run() {
                        execution.completed(null);
                        try {
                            runningTask.launcher.kill(Collections.singletonMap(COOKIE_VAR, cookie));
                        } catch (ChannelClosedException x) {
                            // fine, Jenkins was shutting down
                        } catch (RequestAbortedException x) {
                            // slave was exiting; too late to kill subprocesses
                        } catch (Exception x) {
                            LOGGER.log(Level.WARNING, "failed to shut down " + cookie, x);
                        }
                    }
                });
            }
        }

        /**
         * Called when the body closure is complete.
         */
        @SuppressFBWarnings(value="SE_BAD_FIELD", justification="lease is pickled")
        private static final class Callback extends BodyExecutionCallback.TailCall {

            private final String cookie;
            private WorkspaceList.Lease lease;

            Callback(String cookie, WorkspaceList.Lease lease) {
                this.cookie = cookie;
                this.lease = lease;
            }

            @Override protected void finished(StepContext context) throws Exception {
                LOGGER.log(FINE, "finished {0}", cookie);
                lease.release();
                lease = null;
                finish(cookie);
            }

        }

        /**
         * Occupies {@link Executor} while workflow uses this slave.
         */
        @ExportedBean
        private final class PlaceholderExecutable implements ContinuableExecutable {

            @Override public void run() {
                final TaskListener listener;
                Launcher launcher;
                final Run<?, ?> r;
                try {
                    Executor exec = Executor.currentExecutor();
                    if (exec == null) {
                        throw new IllegalStateException("running task without associated executor thread");
                    }
                    Computer computer = exec.getOwner();
                    // Set up context for other steps inside this one.
                    Node node = computer.getNode();
                    if (node == null) {
                        throw new IllegalStateException("running computer lacks a node");
                    }
                    listener = context.get(TaskListener.class);
                    launcher = node.createLauncher(listener);
                    r = context.get(Run.class);
                    if (cookie == null) {
                        // First time around.
                        cookie = UUID.randomUUID().toString();
                        // Switches the label to a self-label, so if the executable is killed and restarted via ExecutorPickle, it will run on the same node:
                        label = computer.getName();

                        EnvVars env = computer.getEnvironment();
                        env.overrideAll(computer.buildEnvironment(listener));
                        env.put(COOKIE_VAR, cookie);
                        if (exec.getOwner() instanceof Jenkins.MasterComputer) {
                            env.put("NODE_NAME", "master");
                        } else {
                            env.put("NODE_NAME", label);
                        }
                        env.put("EXECUTOR_NUMBER", String.valueOf(exec.getNumber()));

                        synchronized (runningTasks) {
                            runningTasks.put(cookie, new RunningTask());
                        }
                        // For convenience, automatically allocate a workspace, like WorkspaceStep would:
                        Job<?,?> j = r.getParent();
                        if (!(j instanceof TopLevelItem)) {
                            throw new Exception(j + " must be a top-level job");
                        }
                        FilePath p = node.getWorkspaceFor((TopLevelItem) j);
                        if (p == null) {
                            throw new IllegalStateException(node + " is offline");
                        }
                        WorkspaceList.Lease lease = computer.getWorkspaceList().allocate(p);
                        FilePath workspace = lease.path;
                        FlowNode flowNode = context.get(FlowNode.class);
                        flowNode.addAction(new WorkspaceActionImpl(workspace, flowNode));
                        listener.getLogger().println("Running on " + computer.getDisplayName() + " in " + workspace); // TODO hyperlink
                        context.newBodyInvoker()
                                .withContexts(exec, computer, env, workspace)
                                .withCallback(new Callback(cookie, lease))
                                .start();
                        LOGGER.log(FINE, "started {0}", cookie);
                    } else {
                        // just rescheduled after a restart; wait for task to complete
                        LOGGER.log(FINE, "resuming {0}", cookie);
                    }
                } catch (Exception x) {
                    context.onFailure(x);
                    return;
                }
                // wait until the invokeBodyLater call above completes and notifies our Callback object
                synchronized (runningTasks) {
                    LOGGER.log(FINE, "waiting on {0}", cookie);
                    RunningTask runningTask = runningTasks.get(cookie);
                    if (runningTask == null) {
                        LOGGER.log(FINE, "running task apparently finished quickly for {0}", cookie);
                        return;
                    }
                    assert runningTask.execution == null;
                    assert runningTask.launcher == null;
                    runningTask.launcher = launcher;
                    runningTask.execution = new AsynchronousExecution() {
                        @Override public void interrupt(boolean forShutdown) {
                            if (forShutdown) {
                                return;
                            }
                            LOGGER.log(FINE, "interrupted {0}", cookie);
                            // TODO save the BodyExecution somehow and call .cancel() here; currently we just interrupt the build as a whole:
                            Executor masterExecutor = r.getExecutor();
                            if (masterExecutor != null) {
                                masterExecutor.interrupt();
                            } else { // ?
                                super.getExecutor().recordCauseOfInterruption(r, listener);
                            }
                        }
                        @Override public boolean blocksRestart() {
                            return false;
                        }
                        @Override public boolean displayCell() {
                            return true;
                        }
                    };
                    throw runningTask.execution;
                }
            }

            @Override public PlaceholderTask getParent() {
                return PlaceholderTask.this;
            }

            @Override public long getEstimatedDuration() {
                return getParent().getEstimatedDuration();
            }

            @Override public boolean willContinue() {
                synchronized (runningTasks) {
                    return runningTasks.containsKey(cookie);
                }
            }

            @Restricted(DoNotUse.class) // for Jelly
            public @CheckForNull Executor getExecutor() {
                return Executor.of(this);
            }

            @Restricted(NoExternalUse.class) // for Jelly and toString
            public String getUrl() {
                return PlaceholderTask.this.getUrl(); // we hope this has a console.jelly
            }

            @Override public String toString() {
                return "PlaceholderExecutable:" + getUrl() + ":" + cookie;
            }

            private static final long serialVersionUID = 1L;
        }
    }

    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = Logger.getLogger(DockerNodeStepExecution.class.getName());

}