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
package com.netblue.bruce.cluster;

import com.netblue.bruce.cluster.persistence.PersistentClusterFactory;
import org.apache.log4j.Logger;

import java.util.Set;

/**
 * An abstract factory class used to obtain <code>ClusterFactory</code> instances.
 * ClusterFactories are responsible for providing {@link Cluster} instances which
 * represent the current cluster topology.
 * <p/>
 * Typically, you won't need to use a custom ClusterFactory, but if you do, you'll
 * need to code up yourself a ClusterFactory class and a Cluster implementation.
 * Your ClusterFactory should know how to create, initialize, manage and maintain
 * it's Cluster references.
 * <p/>
 * To get this <code>ClusterFactory</code> to instantiate your custom ClusterFactory
 * class, you'll need to either set the system property <code>bruce.cluster.factory.class</code>
 * to the fully qualified class name for your ClusterFactory implementation, or call
 * {@link #setClusterFactoryClass(Class)} at runtime.
 * <p/>
 * Typical usage looks something like this:
 * <p/>
 * <pre>
 *       ClusterFactory factory = null;
 *       try
 *       {
 *           factory = ClusterFactory.getClusterFactory();
 *       }
 *       catch (Exception e)
 *       {
 *           e.printStackTrace();
 *           fail("Cannot create defaultFactory: " + e.getLocalizedMessage());
 *       }
 *       Cluster cluster = factory.getCluster("My configured cluster");
 *       if (cluster != null)
 *       {
 *           Node master = cluster.getMaster();
 *           Set<Node> slaves = cluster.getSlaves();
 *       }
 * </pre>
 *
 *
 * @see Cluster
 * @see com.netblue.bruce.cluster.ClusterChangeListener
 *
 * @author lanceball
 */
public abstract class   ClusterFactory
{
    /** Default class for the ClusterFactory **/
    public static final Class DEFAULT_CLUSTER_FACTORY_CLASS = PersistentClusterFactory.class;

    /** A convenience if you are only creating a single cluster **/
    public static final String DEFAULT_CLUSTER_NAME = DEFAULT_CLUSTER_FACTORY_CLASS.getName();


    /** Current class for the ClusterFactory; defaults to DEFAULT_CLUSTER_FACTORY_CLASS */
    private static Class CLUSTER_FACTORY_CLASS = DEFAULT_CLUSTER_FACTORY_CLASS;

    /** Keep folks informed */
    private static final Logger LOGGER = Logger.getLogger(ClusterFactory.class);

    /**
     * When <code>ClusterFactory</code> is loaded by the classloader, we check to see if a
     * system property has been set for the cluster factory class.  If so, we'll use it
     * to create factories.  Otherwise, use the default.
     */
    static
    {
        String className = System.getProperty("bruce.cluster.factory.class");
        if (className != null)
        {
            try
            {
                LOGGER.info("Loading cluster factory class: " + className);
                CLUSTER_FACTORY_CLASS = Class.forName(className);
            }
            catch (ClassNotFoundException e)
            {
                LOGGER.error("Cannot instantiate cluster factory class: " + className, e);
            }
        }
    }

    /**
     * Creates a new instance of a <code>ClusterFactory</code> using the current ClusterFactory class -
     * either the default implementation, or one provided externally and configured via {@link #setClusterFactoryClass(Class)}
     * or through the <code>bruce.cluster.factory.class</code> property
     * @return a new ClusterFactory instantiated via {@link Class#newInstance()}.
     * @throws IllegalAccessException
     * @throws InstantiationException
     */
    public static ClusterFactory getClusterFactory() throws IllegalAccessException, InstantiationException
    {
        synchronized(ClusterFactory.class)
        {
            return (ClusterFactory) CLUSTER_FACTORY_CLASS.newInstance();
        }
    }


    /**
     * Sets the <code>Class</code> used to instantiate new <code>ClusterFactory</code> instances.  Does not
     * affect existing references to other potential <code>ClusterFactories</code> in the runtime system, nor
     * does it notify any <code>Clusters</code> that a new factory has been configured.
     * @param clusterFactoryClass the class used to instantiate new <code>ClusterFactories</code> via {@link Class#newInstance()} 
     */
    public static void setClusterFactoryClass(final Class clusterFactoryClass)
    {
        synchronized(ClusterFactory.class)
        {
            CLUSTER_FACTORY_CLASS = clusterFactoryClass;
        }
    }

    /**
     * Gets a <code>Cluster</code> instance for the current node topology.
     * @param name of the cluster
     * @return the <code>Cluster</code> - never null
     */
    public abstract Cluster getCluster(String name);

    /**
     * Gets all <code>Cluster</code>s currently configured.
     * @return a <code>Set</code> of all <code>Cluster</code>s - never null.
     */
    public abstract Set<Cluster> getAllClusters();

    /**
     * Searches Clusters for Node with name and returns it if found
     * @param name the name of the node
     * @return the node or null
     */
    public abstract Node getNode(String name);

}
