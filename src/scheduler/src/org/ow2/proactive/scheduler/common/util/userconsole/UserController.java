/*
 * ################################################################
 *
 * ProActive: The Java(TM) library for Parallel, Distributed,
 *            Concurrent computing with Security and Mobility
 *
 * Copyright (C) 1997-2009 INRIA/University of Nice-Sophia Antipolis
 * Contact: proactive@ow2.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version
 * 2 of the License, or any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307
 * USA
 *
 *  Initial developer(s):               The ProActive Team
 *                        http://proactive.inria.fr/team_members.htm
 *  Contributor(s):
 *
 * ################################################################
 * $PROACTIVE_INITIAL_DEV$
 */
package org.ow2.proactive.scheduler.common.util.userconsole;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import javax.security.auth.login.LoginException;

import org.apache.commons.cli.AlreadySelectedException;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.MissingArgumentException;
import org.apache.commons.cli.MissingOptionException;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.Parser;
import org.apache.commons.cli.UnrecognizedOptionException;
import org.apache.log4j.Logger;
import org.objectweb.proactive.core.util.log.ProActiveLogger;
import org.objectweb.proactive.core.util.passwordhandler.PasswordField;
import org.ow2.proactive.scheduler.common.SchedulerAuthenticationInterface;
import org.ow2.proactive.scheduler.common.SchedulerConnection;
import org.ow2.proactive.scheduler.common.UserSchedulerInterface;
import org.ow2.proactive.scheduler.common.exception.SchedulerException;
import org.ow2.proactive.scheduler.common.job.Job;
import org.ow2.proactive.scheduler.common.job.JobId;
import org.ow2.proactive.scheduler.common.job.factories.FlatJobFactory;
import org.ow2.proactive.scheduler.common.util.SchedulerLoggers;
import org.ow2.proactive.scheduler.common.util.Tools;
import org.ow2.proactive.utils.console.Console;
import org.ow2.proactive.utils.console.SimpleConsole;
import org.ow2.proactive.utils.console.VisualConsole;


/**
 * UserController will help you to interact with the scheduler.<br>
 * Use this class to submit jobs, get results, pause job, etc...
 *
 * @author The ProActive Team
 * @since ProActive Scheduling 1.0
 */
public class UserController {

    private static final String SCHEDULER_DEFAULT_URL = Tools.getHostURL("//localhost/");

    private static final String control = "<ctl> ";
    private static Logger logger = ProActiveLogger.getLogger(SchedulerLoggers.CONSOLE);
    private static UserController shell;

    private String commandName = "userScheduler";

    private CommandLine cmd = null;
    private String user = null;
    private String pwd = null;

    private SchedulerAuthenticationInterface auth = null;
    private UserSchedulerModel userModel;

    //private MBeanInfoViewer mbeanInfoViewer;

    /**
     * Start the Scheduler controller
     *
     * @param args the arguments to be passed
     */
    public static void main(String[] args) {
        shell = new UserController();
        shell.load(args);
    }

    public UserController() {
        userModel = UserSchedulerModel.getModel();
    }

    public void load(String[] args) {
        Options options = new Options();

        Option help = new Option("h", "help", false, "Display this help");
        help.setRequired(false);
        options.addOption(help);

        Option username = new Option("l", "login", true, "The username to join the Scheduler");
        username.setArgName("login");
        username.setArgs(1);
        username.setRequired(false);
        options.addOption(username);

        Option schedulerURL = new Option("u", "schedulerURL", true, "The scheduler URL (default " +
            SCHEDULER_DEFAULT_URL + ")");
        schedulerURL.setArgName("schedulerURL");
        schedulerURL.setRequired(false);
        options.addOption(schedulerURL);

        Option visual = new Option("g", "gui", false, "Start the console in a graphical view");
        schedulerURL.setRequired(false);
        options.addOption(visual);

        addCommandLineOptions(options);

        boolean displayHelp = false;

        try {
            String pwdMsg = null;

            Parser parser = new GnuParser();
            cmd = parser.parse(options, args);

            if (cmd.hasOption("h")) {
                displayHelp = true;
            } else {
                String url;
                if (cmd.hasOption("u")) {
                    url = cmd.getOptionValue("u");
                } else {
                    url = SCHEDULER_DEFAULT_URL;
                }
                logger.info("Trying to connect Scheduler on " + url);
                auth = SchedulerConnection.join(url);
                logger.info("\t-> Connection established on " + url);

                logger.info("\nConnecting admin to the Scheduler");
                if (cmd.hasOption("l")) {
                    user = cmd.getOptionValue("l");
                    pwdMsg = user + "'s password: ";
                } else {
                    System.out.print("login: ");
                    BufferedReader buf = new BufferedReader(new InputStreamReader(System.in));
                    user = buf.readLine();
                    pwdMsg = "password: ";
                }

                //ask password to User
                char password[] = null;
                try {
                    password = PasswordField.getPassword(System.in, pwdMsg);
                    if (password == null) {
                        pwd = "";
                    } else {
                        pwd = String.valueOf(password);
                    }
                } catch (IOException ioe) {
                    logger.error("" + ioe);
                }

                //connect to the scheduler
                connect();
                //connect JMX service
                //connectJMXClient(URIBuilder.getHostNameFromUrl(url));
                //start the command line or the interactive mode
                start();
            }
        } catch (MissingArgumentException e) {
            logger.error(e.getLocalizedMessage());
            displayHelp = true;
        } catch (MissingOptionException e) {
            logger.error("Missing option: " + e.getLocalizedMessage());
            displayHelp = true;
        } catch (UnrecognizedOptionException e) {
            logger.error(e.getLocalizedMessage());
            displayHelp = true;
        } catch (AlreadySelectedException e) {
            logger.error(e.getClass().getSimpleName() + " : " + e.getLocalizedMessage());
            displayHelp = true;
        } catch (ParseException e) {
            displayHelp = true;
        } catch (LoginException e) {
            logger.error(e.getMessage() + "\nShutdown the controller.\n");
            System.exit(1);
        } catch (SchedulerException e) {
            logger.error(e.getMessage() + "\nShutdown the controller.\n");
            System.exit(1);
        } catch (Exception e) {
            logger.error("An error has occurred : " + e.getMessage() + "\nShutdown the controller.\n", e);
            System.exit(1);
        }

        if (displayHelp) {
            logger.info("");
            HelpFormatter hf = new HelpFormatter();
            hf.setWidth(135);
            String note = "\nNOTE : if no " + control +
                "command is specified, the controller will start in interactive mode.";
            hf.printHelp(commandName + Tools.shellExtension(), "", options, note, true);
            System.exit(2);
        }

        // if execution reaches this point this means it must exit
        System.exit(0);
    }

    protected void connect() throws LoginException {
        UserSchedulerInterface scheduler = auth.logAsUser(user, pwd);
        userModel.connectScheduler(scheduler);
        logger.info("\t-> User '" + user + "' successfully connected\n");
    }

    //    private void connectJMXClient(String url) {
    //        if (!url.startsWith("//")) {
    //            url = "//" + url;
    //        }
    //        if (!url.endsWith("/")) {
    //            url = url + "/";
    //        }
    //        //connect the JMX client
    //        ClientConnector connectorClient = new ClientConnector(url, "ServerFrontend");
    //        try {
    //            connectorClient.connect();
    //            ProActiveConnection connection = connectorClient.getConnection();
    //            ObjectName mbeanName = new ObjectName("SchedulerFrontend:name=SchedulerWrapperMBean");
    //            MBeanInfo info = connection.getMBeanInfo(mbeanName);
    //            mbeanInfoViewer = new MBeanInfoViewer(connection, mbeanName, info);
    //        } catch (Exception e) {
    //            logger.error("Scheduler MBean not found using : SchedulerFrontend:name=SchedulerWrapperMBean");
    //        }
    //    }

    private void start() throws Exception {
        //start one of the two command behavior
        if (startCommandLine(cmd)) {
            startCommandListener();
        }
    }

    protected OptionGroup addCommandLineOptions(Options options) {
        OptionGroup actionGroup = new OptionGroup();

        Option opt = new Option("submit", true, control + "Submit the given job XML file");
        opt.setArgName("XMLDescriptor");
        opt.setRequired(false);
        opt.setArgs(Option.UNLIMITED_VALUES);
        actionGroup.addOption(opt);

        opt = new Option("cmd", false, control +
            "If mentionned, -submit argument becomes a command line, ie: -submit command args...");
        opt.setRequired(false);
        options.addOption(opt);
        opt = new Option("cmdf", false, control +
            "If mentionned, -submit argument becomes a text file path containing command lines to schedule");
        opt.setRequired(false);
        options.addOption(opt);
        opt = new Option("o", true, control +
            "Used with submit action, specify a log file path to store job output");
        opt.setArgName("logFile");
        opt.setRequired(false);
        opt.setArgs(1);
        options.addOption(opt);
        opt = new Option("s", true, control + "Used with submit action, specify a selection script");
        opt.setArgName("selScript");
        opt.setRequired(false);
        opt.setArgs(1);
        options.addOption(opt);
        opt = new Option("jn", true, control + "Used with submit action, specify the job name");
        opt.setArgName("jobName");
        opt.setRequired(false);
        opt.setArgs(1);
        options.addOption(opt);

        opt = new Option("pausejob", true, control + "Pause the given job (pause every non-running tasks)");
        opt.setArgName("jobId");
        opt.setRequired(false);
        opt.setArgs(1);
        actionGroup.addOption(opt);

        opt = new Option("resumejob", true, control + "Resume the given job (restart every paused tasks)");
        opt.setArgName("jobId");
        opt.setRequired(false);
        opt.setArgs(1);
        actionGroup.addOption(opt);

        opt = new Option("killjob", true, control + "Kill the given job (cause the job to finish)");
        opt.setArgName("jobId");
        opt.setRequired(false);
        opt.setArgs(1);
        actionGroup.addOption(opt);

        opt = new Option("removejob", true, control + "Remove the given job");
        opt.setArgName("jobId");
        opt.setRequired(false);
        opt.setArgs(1);
        actionGroup.addOption(opt);

        opt = new Option("result", true, control + "Get the result of the given job");
        opt.setArgName("jobId");
        opt.setRequired(false);
        opt.setArgs(1);
        actionGroup.addOption(opt);

        opt = new Option("tresult", true, control + "Get the result of the given task");
        opt.setArgName("jobId taskName");
        opt.setRequired(false);
        opt.setArgs(2);
        actionGroup.addOption(opt);

        opt = new Option("output", true, control + "Get the output of the given job");
        opt.setArgName("jobId");
        opt.setRequired(false);
        opt.setArgs(1);
        actionGroup.addOption(opt);

        opt = new Option("toutput", true, control + "Get the output of the given task");
        opt.setArgName("jobId taskName");
        opt.setRequired(false);
        opt.setArgs(2);
        actionGroup.addOption(opt);

        opt = new Option("priority", true, control +
            "Change the priority of the given job (Idle, Lowest, Low, Normal, High, Highest)");
        opt.setArgName("jobId newPriority");
        opt.setRequired(false);
        opt.setArgs(2);
        actionGroup.addOption(opt);

        //        opt = new Option("jmxinfo", false, control +
        //            "Display some statistics provided by the Scheduler MBean");
        //        opt.setRequired(false);
        //        opt.setArgs(0);
        //        actionGroup.addOption(opt);

        options.addOptionGroup(actionGroup);

        return actionGroup;
    }

    private void startCommandListener() throws Exception {
        Console console;
        if (cmd.hasOption("g")) {
            console = new VisualConsole();
        } else {
            console = new SimpleConsole();
        }
        userModel.connectConsole(console);
        userModel.start();
    }

    protected boolean startCommandLine(CommandLine cmd) {
        userModel.setDisplayOnStdStream(true);
        if (cmd.hasOption("pausejob")) {
            UserSchedulerModel.pause(cmd.getOptionValue("pausejob"));
        } else if (cmd.hasOption("resumejob")) {
            UserSchedulerModel.resume(cmd.getOptionValue("resumejob"));
        } else if (cmd.hasOption("killjob")) {
            UserSchedulerModel.kill(cmd.getOptionValue("killjob"));
        } else if (cmd.hasOption("removejob")) {
            UserSchedulerModel.remove(cmd.getOptionValue("removejob"));
        } else if (cmd.hasOption("submit")) {
            if (cmd.hasOption("cmd") || cmd.hasOption("cmdf")) {
                submitCMD();
            } else {
                UserSchedulerModel.submit(cmd.getOptionValue("submit"));
            }
        } else if (cmd.hasOption("result")) {
            UserSchedulerModel.result(cmd.getOptionValue("result"));
        } else if (cmd.hasOption("tresult")) {
            String[] optionValues = cmd.getOptionValues("tresult");
            if (optionValues == null || optionValues.length != 2) {
                userModel.error("tresult must have two arguments. Start with --help for more informations");
            }
            UserSchedulerModel.tresult(optionValues[0], optionValues[1]);
        } else if (cmd.hasOption("output")) {
            UserSchedulerModel.output(cmd.getOptionValue("output"));
        } else if (cmd.hasOption("toutput")) {
            String[] optionValues = cmd.getOptionValues("toutput");
            if (optionValues == null || optionValues.length != 2) {
                userModel.error("toutput must have two arguments. Start with --help for more informations");
            }
            UserSchedulerModel.toutput(optionValues[0], optionValues[1]);
        } else if (cmd.hasOption("priority")) {
            try {
                UserSchedulerModel.priority(cmd.getOptionValues("priority")[0], cmd
                        .getOptionValues("priority")[1]);
            } catch (ArrayIndexOutOfBoundsException e) {
                userModel
                        .print("Missing arguments for job priority. Arguments must be <jobId> <newPriority>\n\t"
                            + "where priorities are Idle, Lowest, Low, Normal, High, Highest");
            }
        }
        //        else if (cmd.hasOption("jmxinfo")) {
        //            JMXinfo();
        //        } 
        else {
            userModel.setDisplayOnStdStream(false);
            return true;
        }
        return false;
    }

    private String submitCMD() {
        try {
            Job job;
            String jobGivenName = null;
            String jobGivenOutput = null;
            String givenSelScript = null;
            if (cmd.hasOption("jn")) {
                jobGivenName = cmd.getOptionValue("jn");
            }
            if (cmd.hasOption("o")) {
                jobGivenOutput = cmd.getOptionValue("o");
            }
            if (cmd.hasOption("s")) {
                givenSelScript = cmd.getOptionValue("s");
            }

            if (cmd.hasOption("cmd")) {
                //create job from a command to launch specified in command line
                String cmdTab[] = cmd.getOptionValues("submit");
                String jobCommand = "";

                for (String s : cmdTab) {
                    jobCommand += (s + " ");
                }
                jobCommand = jobCommand.trim();
                job = FlatJobFactory.getFactory().createNativeJobFromCommand(jobCommand, jobGivenName,
                        givenSelScript, jobGivenOutput, user);
            } else {
                String commandFilePath = cmd.getOptionValue("submit");
                job = FlatJobFactory.getFactory().createNativeJobFromCommandsFile(commandFilePath,
                        jobGivenName, givenSelScript, jobGivenOutput, user);
            }
            JobId id = userModel.getScheduler().submit(job);
            userModel.print("Job successfully submitted ! (id=" + id.value() + ")");
            return id.value();
        } catch (Exception e) {
            userModel.handleExceptionDisplay("Error on job Submission", e);
        }
        return "";
    }

    /**
     * Set the commandName value to the given commandName value
     *
     * @param commandName the commandName to set
     */
    public void setCommandName(String commandName) {
        this.commandName = commandName;
    }

}
