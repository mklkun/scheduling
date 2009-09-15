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
 *  Contributor(s): ActiveEon Team - http://www.activeeon.com
 *
 * ################################################################
 * $$ACTIVEEON_CONTRIBUTOR$$
 */
package org.ow2.proactive.resourcemanager.utils;

import org.objectweb.proactive.api.PAActiveObject;
import org.objectweb.proactive.api.PAFuture;
import org.objectweb.proactive.core.node.Node;
import org.objectweb.proactive.core.node.NodeFactory;
import org.objectweb.proactive.core.util.wrapper.BooleanWrapper;
import org.ow2.proactive.authentication.crypto.Credentials;
import org.ow2.proactive.resourcemanager.authentication.RMAuthentication;
import org.ow2.proactive.resourcemanager.common.RMConstants;
import org.ow2.proactive.resourcemanager.exception.AddingNodesException;
import org.ow2.proactive.resourcemanager.frontend.RMAdmin;
import org.ow2.proactive.resourcemanager.frontend.RMConnection;


/**
 * This class is responsible for implementing actions that are started in
 * ProActiveAgent: registration in ProActive Resource Manager
 * 
 * @author ProActive team
 */
public final class PAAgentServiceRMStarter {

    /**
     * The starter will try to connect to the Resource Manager before killing
     * itself that means that it will try to connect during
     * RM_WAIT_ON_JOIN_TIMEOUT_IN_MS milliseconds
     */
    private static final int RM_WAIT_ON_JOIN_TIMEOUT_IN_MS = 60000;
    /**
     * The ping delay used in RMPinger that pings the RM and exists if the
     * Resource Manager is down
     */
    private static final long PING_DELAY = 30000;
    /** The default name of the node */
    private static final String PAAGENT_DEFAULT_NODE_NAME = "PA-AGENT_NODE";

    /** The number of attempts to register to the RM before quitting */
    private static int NB_OF_REGISTER_ATTEMPTS = 10;

    /** The delay, in milliseconds, between two register attempts */
    private static int REGISTER_ATTEMPSTS_DELAY = 5000;

    /** The node to be registered in RM */
    private Node localNode;

    private RMAdmin admin;

    /**
     * Creates a new instance of this class and calls registersInRm method. The
     * arguments must be as follows: arg[0] = username, arg[1] = password,
     * arg[2] = rmUrl, arg[3] = nodeName (optional), args[4] = nodeSourceName
     * (optional)
     * 
     * @param args
     *            The arguments needed to join the Resource Manager
     */
    public static void main(final String args[]) {
        if (args.length < 3 || args.length > 5) {
            System.out
                    .println("Usage: java PAAgentServiceRMStarter username password rmUrl [nodename] [nodeSourceName]");
            return;
        }
        final String username = args[0];
        final String password = args[1];
        final String rmUrl = args[2];
        // If the nodename was specified use it
        final String nodename = (args.length >= 4 ? args[3] : PAAGENT_DEFAULT_NODE_NAME);
        // If specified, nodeSource to which the node will try to connect
        final String nsName = (args.length >= 5 ? args[4] : null);
        // Use given args
        final PAAgentServiceRMStarter starter = new PAAgentServiceRMStarter();

        if (!starter.startLocalNode(nodename)) {
            System.out.println("Local node could not be created. Application will exit. ");
            System.exit(1);
        }

        boolean registered = false;
        int register_attempts = 0;

        while ((!registered) && (register_attempts < NB_OF_REGISTER_ATTEMPTS)) {
            register_attempts++;
            registered = starter.registerInRM(username, password, rmUrl, nsName);

            if (registered) {
                System.out.println("Connected to the Resource Manager at " + rmUrl + "\n");
            } else { // not yet registered
                System.out.println("Attempt number " + register_attempts + " out of " +
                    NB_OF_REGISTER_ATTEMPTS + " to register to the Resource Manager at " + rmUrl +
                    " has failed.");
                try {
                    Thread.sleep(REGISTER_ATTEMPSTS_DELAY);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }// while

        if (!registered) {
            System.out.println("The Resource Manager at " + " is unreachable ! " + NB_OF_REGISTER_ATTEMPTS +
                " attempts have been performed. The application will exit.");
            System.exit(1);
        }// if not registered
    }

    /**Creates a node on localhost
     * */
    private boolean startLocalNode(final String nodename) {
        try {
            localNode = NodeFactory.createLocalNode(nodename, false, null, null, null);
            if (localNode == null) {
                throw new RuntimeException("The node returned by the NodeFactory is null");
            }
        } catch (Throwable t) {
            System.out.println("Could not create the local node " + nodename);
            t.printStackTrace();
            return false;
        }
        return true;

    }

    /**
     * Registers the local node in the ResourceManager at given URL in parameter and handles all
     * errors/exceptions. Tries to joins the Resource Manager with a specified
     * timeout then logs as admin with the provided username and password and
     * adds the created node to the Resource Manager
     */
    private boolean registerInRM(final String username, final String password, final String rmUrl,
            final String nodeSourceName) {

        // Create the full url to contact the Resource Manager
        final String fullUrl = rmUrl.endsWith("/") ? rmUrl + RMConstants.NAME_ACTIVE_OBJECT_RMAUTHENTICATION
                : rmUrl + "/" + RMConstants.NAME_ACTIVE_OBJECT_RMAUTHENTICATION;
        // 2 - Try to join the Resource Manager with a specified timeout
        RMAuthentication auth = null;
        try {
            auth = RMConnection.waitAndJoin(fullUrl, RM_WAIT_ON_JOIN_TIMEOUT_IN_MS);
            if (auth == null) {
                throw new RuntimeException("The RMAuthentication instance is null");
            }
        } catch (Throwable t) {
            System.out.println("Could not join the Resource Manager at " + rmUrl);
            t.printStackTrace();
            return false;
        }
        // 3 - Log as admin with the provided username and password
        if (admin == null)
            try {
                Credentials creds = Credentials.createCredentials(username, password, auth.getPublicKey());
                admin = auth.logAsAdmin(creds);
                if (admin == null) {
                    throw new RuntimeException("The RMAdmin instance is null");
                }
            } catch (Throwable t) {
                System.out.println("Could not log as admin into the Resource Manager at " + rmUrl);
                t.printStackTrace();
                return false;
            }

        // 4 - Add the created node to the Resource Manager
        try {
            BooleanWrapper result;
            if (nodeSourceName != null) {
                result = admin.addNode(localNode.getNodeInformation().getURL(), nodeSourceName);
            } else {
                result = admin.addNode(localNode.getNodeInformation().getURL());
            }

            if (result.booleanValue()) {
                System.out.println("Node " + localNode.getNodeInformation().getURL() + " added");
            }

        } catch (AddingNodesException ex) {
            System.out.println("Could not add the local node the Resource Manager at " + rmUrl);
            ex.printStackTrace();
            return false;
        }
        // 5 - Start a new pinger thread
        final RMPinger rp = new RMPinger(admin);
        new Thread(rp).start();
        return true;
    }

    private final class RMPinger implements Runnable {
        /** The reference to ping */
        private final RMAdmin admin;

        public RMPinger(final RMAdmin admin) {
            this.admin = admin;
        }

        public void run() {
            // ping the rm to see if we are still connected
            // if not connected just exit
            while (PAActiveObject.pingActiveObject(admin)) {
                try {
                    Thread.sleep(PING_DELAY);
                } catch (InterruptedException e) {
                }
            }// while connected
            // if we are here it means we lost the connection. just exit..
            System.out
                    .println("The connection to the Resource Manager has been lost. The application will exit. ");
            System.exit(1);
        }
    }
}