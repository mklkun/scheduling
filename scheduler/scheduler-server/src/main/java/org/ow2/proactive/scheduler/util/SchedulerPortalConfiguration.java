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
package org.ow2.proactive.scheduler.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.ow2.proactive.scheduler.core.properties.PASchedulerProperties;


public class SchedulerPortalConfiguration {
    private String path;

    private static SchedulerPortalConfiguration configuration;

    private static Logger logger = Logger.getLogger(SchedulerPortalConfiguration.class);

    private SchedulerPortalConfiguration() {
    }

    public static synchronized SchedulerPortalConfiguration getConfiguration() {
        if (configuration == null) {
            configuration = new SchedulerPortalConfiguration();
        }
        return configuration;
    }

    public String getPath() {
        if (path == null) {
            path = PASchedulerProperties.getAbsolutePath(PASchedulerProperties.SCHEDULER_PORTAL_CONFIGURATION.getValueAsString());
        }
        return path;
    }

    public Properties getProperties() {
        Properties props = new Properties();
        try {
            InputStream fis = new FileInputStream(getPath());
            props.load(fis);
        } catch (IOException e) {
            logger.warn("Scheduler Portal Configuration file: " + getPath() + " not found!", e);
        }
        return props;
    }
}
