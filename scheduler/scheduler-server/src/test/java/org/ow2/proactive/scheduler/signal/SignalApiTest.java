/*
 * ProActive Parallel Suite(TM):
 * The Open Source library for parallel and distributed
 * Workflows & Scheduling, Orchestration, Cloud Automation
 * and Big Data Analysis on Enterprise Grids & Clouds.
 *
 * Copyright (c) 2007 - 2017 ActiveEon
 * Contact: contact@activeeon.com
 *
 * This library is free software: you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation: version 3 of
 * the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 * If needed, contact us to obtain a release under GPL Version 2 or 3
 * or a different license than the AGPL.
 */
package org.ow2.proactive.scheduler.signal;

import java.io.File;
import java.io.IOException;
import java.rmi.AlreadyBoundException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.objectweb.proactive.ActiveObjectCreationException;
import org.objectweb.proactive.api.PAActiveObject;
import org.objectweb.proactive.core.config.CentralPAPropertyRepository;
import org.objectweb.proactive.core.node.Node;
import org.objectweb.proactive.core.node.NodeException;
import org.objectweb.proactive.core.runtime.ProActiveRuntimeImpl;
import org.ow2.proactive.scheduler.common.job.JobId;
import org.ow2.proactive.scheduler.common.task.TaskId;
import org.ow2.proactive.scheduler.core.properties.PASchedulerProperties;
import org.ow2.proactive.scheduler.job.JobIdImpl;
import org.ow2.proactive.scheduler.synchronization.AOSynchronization;
import org.ow2.proactive.scheduler.synchronization.InvalidChannelException;
import org.ow2.proactive.scheduler.synchronization.SynchronizationInternal;
import org.ow2.proactive.scheduler.task.TaskIdImpl;
import org.ow2.tests.ProActiveTestClean;

import com.jayway.awaitility.Awaitility;


public class SignalApiTest extends ProActiveTestClean {

    private static final String USER = "user";

    private static final JobId JOB_ID = new JobIdImpl(0, "JobWithSignals");

    private static final TaskId TASK_ID = TaskIdImpl.createTaskId(JOB_ID, "Task", 0);

    private static final String SIGNALS_CHANNEL = PASchedulerProperties.SCHEDULER_SIGNALS_CHANNEL.getValueAsString();

    private static SynchronizationInternal synchronizationInternal;

    private static SignalApi signalApi;

    @ClassRule
    public static TemporaryFolder folder = new TemporaryFolder();

    private static File tempFolder;

    private static ExecutorService executor;

    @BeforeClass
    public static void classInit()
            throws IOException, ActiveObjectCreationException, NodeException, AlreadyBoundException {
        CentralPAPropertyRepository.PA_CLASSLOADING_USEHTTP.setValue(false);
        if (System.getProperty("log4j.configuration") == null) {
            // While logger is not configured and it not set with sys properties, use Console logger
            Logger.getRootLogger().getLoggerRepository().resetConfiguration();
            BasicConfigurator.configure(new ConsoleAppender(new PatternLayout("%m%n")));
            Logger.getRootLogger().setLevel(Level.DEBUG);
        }
        Logger.getLogger(SignalApiImpl.class).setLevel(Level.TRACE);

        tempFolder = folder.newFolder("signal");

        Node localNode = ProActiveRuntimeImpl.getProActiveRuntime().createLocalNode("signal-node-0",
                                                                                    true,
                                                                                    "signal-v-node-0");

        synchronizationInternal = PAActiveObject.newActive(AOSynchronization.class,
                                                           new Object[] { tempFolder.getAbsolutePath() },
                                                           localNode);
        synchronizationInternal.createChannelIfAbsent(USER, TASK_ID, SIGNALS_CHANNEL, true);
        signalApi = new SignalApiImpl(USER, TASK_ID, synchronizationInternal);
        executor = Executors.newFixedThreadPool(2);
    }

    @Before
    public void clearJobSignals() throws SignalApiException {
        signalApi.clearJobSignals();
    }

    @AfterClass
    public static void cleanUp() {
        executor.shutdownNow();
        PAActiveObject.terminateActiveObject(synchronizationInternal, true);
        tempFolder.delete();
    }

    @Test
    public void testReadyForSignal() throws SignalApiException, InvalidChannelException {
        String signal = "test_signal_1";
        signalApi.readyForSignal(signal);

        Assert.assertFalse(((HashSet) synchronizationInternal.get(USER,
                                                                  TASK_ID,
                                                                  SIGNALS_CHANNEL,
                                                                  JOB_ID.value())).contains(signal));
        Assert.assertTrue(((HashSet) synchronizationInternal.get(USER,
                                                                 TASK_ID,
                                                                 SIGNALS_CHANNEL,
                                                                 JOB_ID.value())).contains(((SignalApiImpl) signalApi).READY_PREFIX +
                                                                                           signal));
    }

    @Test(expected = SignalApiException.class)
    public void testReadyForSignalWithException() throws SignalApiException {
        String signal = " ";

        // Send ready for an empty signal then assert a SignalAPIException is thrown
        signalApi.readyForSignal(signal);
    }

    @Test
    public void testIsReceived() throws SignalApiException {
        String signal = "test_signal_2";
        signalApi.sendSignal(signal);
        Assert.assertTrue(signalApi.isReceived(signal));
    }

    @Test
    public void testCheckForSignals() throws SignalApiException {
        Set<String> signalsToBeChecked = new HashSet<String>() {
            {
                add("test_signal_3_1");
                add("test_signal_3_2");
            }
        };
        signalApi.sendManySignals(signalsToBeChecked);
        String receivedSignal = signalApi.checkForSignals(signalsToBeChecked);
        Assert.assertTrue((receivedSignal == "test_signal_3_1") || receivedSignal == "test_signal_3_2");
    }

    @Test
    public void testSendSignal() throws InvalidChannelException, SignalApiException {
        String signal = "test_signal_4";

        // Send the signal then check that its associated ready signal is removed
        signalApi.sendSignal(signal);

        Assert.assertFalse(((HashSet) synchronizationInternal.get(USER,
                                                                  TASK_ID,
                                                                  SIGNALS_CHANNEL,
                                                                  JOB_ID.value())).contains(((SignalApiImpl) signalApi).READY_PREFIX +
                                                                                            signal));

        Assert.assertEquals(1,
                            ((HashSet) synchronizationInternal.get(USER,
                                                                   TASK_ID,
                                                                   SIGNALS_CHANNEL,
                                                                   JOB_ID.value())).stream()
                                                                                   .filter(sig -> sig.equals(signal))
                                                                                   .count());
    }

    @Test(expected = SignalApiException.class)
    public void testSendSignalWithException() throws SignalApiException {
        String signal = " ";

        // Send an empty signal then assert a SignalAPIException is thrown
        signalApi.sendSignal(signal);
    }

    @Test
    public void testSendManySignals() throws InvalidChannelException, SignalApiException {
        String signal1 = "test_signal_5_1";
        String signal2 = "test_signal_5_2";
        Set<String> signalsToBeSent = new HashSet<String>() {
            {
                add(signal1);
                add(signal2);
            }
        };

        // Send signals twice and check that each signal is added only once, and their associated ready signals are removed
        signalApi.sendManySignals(signalsToBeSent);
        signalApi.sendManySignals(signalsToBeSent);

        Set<String> allSignals = (HashSet) synchronizationInternal.get(USER, TASK_ID, SIGNALS_CHANNEL, JOB_ID.value());

        Assert.assertFalse(allSignals.contains(((SignalApiImpl) signalApi).READY_PREFIX + signal1) ||
                           allSignals.contains(((SignalApiImpl) signalApi).READY_PREFIX + signal2));

        signalsToBeSent.forEach(signal -> Assert.assertEquals(1,
                                                              allSignals.stream()
                                                                        .filter(sig -> sig.equals(signal))
                                                                        .count()));
    }

    @Test(expected = SignalApiException.class)
    public void testSendManySignalsWithException() throws SignalApiException {
        Set<String> signalsToBeSent = new HashSet<String>() {
            {
                add("");
            }
        };

        // Send empty signals then assert a SignalAPIException is thrown
        signalApi.sendManySignals(signalsToBeSent);
    }

    @Test
    public void testRemoveSignal() throws InvalidChannelException, SignalApiException {
        String signal = "test_signal_6";
        signalApi.sendSignal(signal);
        signalApi.removeSignal(signal);
        Assert.assertFalse(((HashSet) synchronizationInternal.get(USER,
                                                                  TASK_ID,
                                                                  SIGNALS_CHANNEL,
                                                                  JOB_ID.value())).contains(signal));
    }

    @Test
    public void testRemoveManySignals() throws InvalidChannelException, SignalApiException {
        Set<String> signalsToBeRemoved = new HashSet<String>() {
            {
                add("test_signal_7_1");
                add("test_signal_7_2");
            }
        };
        signalApi.sendManySignals(signalsToBeRemoved);
        signalApi.removeManySignals(signalsToBeRemoved);
        Set<String> allSignals = (HashSet) synchronizationInternal.get(USER, TASK_ID, SIGNALS_CHANNEL, JOB_ID.value());
        signalsToBeRemoved.forEach(signal -> Assert.assertFalse(allSignals.contains(signal)));
    }

    @Test
    public void testGetJobSignals() throws SignalApiException {
        String signal = "test_signal_8";
        signalApi.sendSignal(signal);
        Assert.assertFalse(signalApi.getJobSignals().isEmpty());
    }

    @Test
    public void testClearJobSignals() throws InvalidChannelException, SignalApiException {
        signalApi.clearJobSignals();
        Assert.assertFalse(synchronizationInternal.containsKey(USER, TASK_ID, SIGNALS_CHANNEL, JOB_ID.value()));
    }

    @Test
    public void testWaitFor() throws SignalApiException {
        String signal = "test_signal_9";
        long durationInMillis = 1000;

        //Define a thread that waits for the signal reception
        Runnable waitForSignalThread = () -> {
            try {
                signalApi.waitFor(signal);
            } catch (Exception e) {
            }
        };

        executor.submit(sendSignalRunnable(signal, durationInMillis));
        Awaitility.await().atMost(3 * durationInMillis, TimeUnit.MILLISECONDS).until(waitForSignalThread);
        Assert.assertTrue(signalApi.isReceived(signal));
    }

    @Test
    public void testWaitForAny() throws SignalApiException {
        String signal_1 = "test_signal_10_1";
        String signal_2 = "test_signal_10_2";
        Set<String> signals = new HashSet<String>() {
            {
                add(signal_1);
                add(signal_2);
            }
        };
        long durationInMillis_1 = 1000;
        long durationInMillis_2 = 4000;

        //Define a thread that waits for the signal reception
        Runnable waitForAnySignalThread = () -> {
            try {
                signalApi.waitForAny(signals);
            } catch (Exception e) {
            }
        };

        executor.submit(sendSignalRunnable(signal_1, durationInMillis_1));
        executor.submit(sendSignalRunnable(signal_2, durationInMillis_2));
        Awaitility.await().atMost(3 * durationInMillis_1, TimeUnit.MILLISECONDS).until(waitForAnySignalThread);
        Assert.assertTrue(signalApi.isReceived(signal_1));
        Assert.assertFalse(signalApi.isReceived(signal_2));
    }

    @Test
    public void testWaitForAll() throws SignalApiException {
        String signal_1 = "test_signal_11_1";
        String signal_2 = "test_signal_11_2";
        Set<String> signals = new HashSet<String>() {
            {
                add(signal_1);
                add(signal_2);
            }
        };
        long durationInMillis = 0;

        //Define a thread that waits for signals' reception
        Runnable waitForAllSignalThread = () -> {
            try {
                signalApi.waitForAll(signals);
            } catch (Exception e) {
            }
        };

        executor.submit(sendSignalRunnable(signal_1, durationInMillis));
        executor.submit(sendSignalRunnable(signal_2, durationInMillis));

        Awaitility.await().atMost(3000, TimeUnit.MILLISECONDS).until(waitForAllSignalThread);

        Assert.assertTrue(signalApi.isReceived(signal_1) && signalApi.isReceived(signal_2));
    }

    private Runnable sendSignalRunnable(String signal, long duration) {
        Runnable waitForSignalThread = () -> {
            try {
                TimeUnit.MILLISECONDS.sleep(duration);
                signalApi.sendSignal(signal);
            } catch (SignalApiException | InterruptedException e) {
            }
        };
        return waitForSignalThread;
    }
}
