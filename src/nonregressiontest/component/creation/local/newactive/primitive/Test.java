/*
* ################################################################
*
* ProActive: The Java(TM) library for Parallel, Distributed,
*            Concurrent computing with Security and Mobility
*
* Copyright (C) 1997-2002 INRIA/University of Nice-Sophia Antipolis
* Contact: proactive-support@inria.fr
*
* This library is free software; you can redistribute it and/or
* modify it under the terms of the GNU Lesser General Public
* License as published by the Free Software Foundation; either
* version 2.1 of the License, or any later version.
*
* This library is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
* Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public
* License along with this library; if not, write to the Free Software
* Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307
* USA
*
*  Initial developer(s):               The ProActive Team
*                        http://www.inria.fr/oasis/ProActive/contacts.html
*  Contributor(s):
*
* ################################################################
*/
package nonregressiontest.component.creation.local.newactive.primitive;

import java.net.InetAddress;

import nonregressiontest.component.creation.ComponentA;
import nonregressiontest.component.creation.ComponentInfo;

import org.objectweb.fractal.api.Component;
import org.objectweb.fractal.api.type.InterfaceType;
import org.objectweb.fractal.api.type.TypeFactory;
import org.objectweb.proactive.ProActive;
import org.objectweb.proactive.core.component.ComponentParameters;
import org.objectweb.proactive.core.component.Fractal;
import org.objectweb.proactive.core.component.type.ProActiveTypeFactory;

import testsuite.test.FunctionalTest;

/**
 * @author Matthieu Morel
 * 
 * creates a new component
 */
public class Test extends FunctionalTest {
	Component componentA;
	String name;
	String nodeUrl;

	public Test() {
		super(
			"Creation of a primitive component on the local default node",
			"Test newActiveComponent method for a primitive component on the local default node");

	}

	/**
	 * @see testsuite.test.FunctionalTest#action()
	 */
	public void action() throws Exception {

		System.setProperty("proactive.future.ac", "enable");
		// start a new thread so that automatic continuations are enabled for components
		ACThread acthread = new ACThread();
		acthread.start();
		acthread.join();
		System.setProperty("proactive.future.ac", "disable");
	}

	private class ACThread extends Thread {

		public void run() {
			
			try {

			ProActiveTypeFactory type_factory = ProActiveTypeFactory.instance();
			ComponentParameters component_parameters =
				new ComponentParameters(
					"componentA",
					ComponentParameters.PRIMITIVE,
					type_factory.createFcType(
						new InterfaceType[] {
							type_factory.createFcItfType(
								"componentInfo",
								ComponentInfo.class.getName(),
								TypeFactory.SERVER,
								TypeFactory.MANDATORY,
								TypeFactory.SINGLE),
							}));
			componentA =
				ProActive.newActiveComponent(
					ComponentA.class.getName(),
					new Object[] { "toto" },
					null,
					null,
					null,
					component_parameters);
			//logger.debug("OK, instantiated the component");
			// start the component!
			
			Fractal.getLifeCycleController(componentA).startFc();
			ComponentInfo ref = (ComponentInfo) componentA.getFcInterface("componentInfo");
			name = ref.getName();
			nodeUrl = ((ComponentInfo) componentA.getFcInterface("componentInfo")).getNodeUrl();
			} catch (Exception e) {
				logger.error(e.getMessage());
				e.printStackTrace();
			}
		}
	}

	/**
	 * @see testsuite.test.AbstractTest#initTest()
	 */
	public void initTest() throws Exception {
	}

	/**
	 * @see testsuite.test.AbstractTest#endTest()
	 */
	public void endTest() throws Exception {
	}

	public boolean postConditions() throws Exception {
		return (name.equals("toto") && (nodeUrl.indexOf(InetAddress.getLocalHost().getHostName()) != -1));
	}

	public static void main(String[] args) {
		Test test = new Test();
		try {
			test.action();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
