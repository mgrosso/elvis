/*
 * Bruce - A PostgreSQL Database Replication System
 *
 * Portions Copyright (c) 2007, Connexus Corporation
 *
 * Permission to use, copy, modify, and distribute this software and its
 * documentation for any purpose, without fee, and without a written
 * agreement is hereby granted, provided that the above copyright notice and
 * this paragraph and the following two paragraphs appear in all copies.
 *
 * IN NO EVENT SHALL CONNEXUS CORPORATION BE LIABLE TO ANY PARTY FOR DIRECT,
 * INDIRECT, SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES, INCLUDING LOST
 * PROFITS, ARISING OUT OF THE USE OF THIS SOFTWARE AND ITS DOCUMENTATION,
 * EVEN IF CONNEXUS CORPORATION HAS BEEN ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 *
 * CONNEXUS CORPORATION SPECIFICALLY DISCLAIMS ANY WARRANTIES, INCLUDING,
 * BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE. THE SOFTWARE PROVIDED HEREUNDER IS ON AN "AS IS"
 * BASIS, AND CONNEXUS CORPORATION HAS NO OBLIGATIONS TO PROVIDE MAINTENANCE,
 * SUPPORT, UPDATES, ENHANCEMENTS, OR MODIFICATIONS.
*/
package com.netblue.bruce;

import org.apache.log4j.Logger;

import java.io.FileInputStream;
import static java.text.MessageFormat.format;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

class BruceProperties extends Properties
{
    private static final Logger logger = Logger.getLogger(BruceProperties.class.getName());

    /**
     * @serial used to verify that the sender and receiver of a serialized object have loaded classes for that object
     * that are compatible with respect to serialization.
     */
    private static final long serialVersionUID = 1L;

    public BruceProperties()
    {
        super();
        this.putAll(System.getProperties());
    }

    public BruceProperties(String propertiesFilename, String propertiesFilenameDefault)
    {
	this();
        String pfn = this.getProperty(propertiesFilename, propertiesFilenameDefault);
        try
        {
            this.load(new FileInputStream(pfn));
        }
        catch (Exception e)
        {
            logger.debug("Unable to load " + pfn + ". Proceding anyways.");
            logger.debug(e);
        }
    }

    public void logProperties()
    {
        // if we are debug logging, then print out a sorted list of properties
        if (logger.isDebugEnabled())
        {
            logger.debug("Properties:");
            TreeMap<Object, Object> t = new TreeMap<Object, Object>(this);
            for (Map.Entry<Object, Object> entry : t.entrySet())
            {
                logger.debug(format("{0}={1}", entry.getKey().toString(), entry.getValue().toString()));
            }
        }
    }


    public int getIntProperty(String key, int d)
    {
        String vS = super.getProperty(key);
        if (vS == null)
        {
            logger.trace("Property " + key + " is not set");
            logger.trace("Assuming default of " + d);
            return d;
        }
        try
        {
            return new Integer(vS);
        }
        catch (NumberFormatException e)
        {
            logger.trace("Property " + key + " is set to " + vS);
            logger.trace("But unable to convert to Integer");
            logger.trace("Assuming default of " + d);
            return d;
        }
        // Unreachable
    }
}
