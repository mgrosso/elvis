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
package com.netblue.bruce.cluster.persistence;

import com.netblue.bruce.cluster.Cluster;
import com.netblue.bruce.cluster.ClusterFactory;
import com.netblue.bruce.cluster.ClusterInitializationException;
import org.apache.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.cfg.AnnotationConfiguration;
import org.hibernate.cfg.Configuration;
import org.hibernate.criterion.Restrictions;

import java.util.*;

/**
 * Provides an instance to a {@link com.netblue.bruce.cluster.DefaultCluster}.
 * @author lanceball
 * @see com.netblue.bruce.cluster.Cluster
 * @see com.netblue.bruce.cluster.ClusterFactory
 */
public class PersistentClusterFactory extends ClusterFactory
{
    private static final Map<String, com.netblue.bruce.cluster.Cluster> CLUSTER_MAP = new HashMap<String, Cluster>();
    private static final Logger LOGGER = Logger.getLogger(PersistentClusterFactory.class);
    private final SessionFactory sessionFactory;    


    /**
     * Creates a new factory.  Loads all existing cluster configurations into memory.
     */
    public PersistentClusterFactory()
    {
        final Configuration configuration = new AnnotationConfiguration().configure();
        configuration.addProperties(System.getProperties());
        sessionFactory = configuration.buildSessionFactory();
        Session initSession = null;
        Transaction tx = null;
        try
        {
            initSession = sessionFactory.openSession();
            tx = initSession.beginTransaction();
            List clusterList = initSession.createCriteria(com.netblue.bruce.cluster.persistence.Cluster.class).list();
            tx.commit();
            for (Object o : clusterList)
            {
                Cluster cluster = (Cluster) o;
                CLUSTER_MAP.put(cluster.getName(), cluster);
                LOGGER.info("Loading cluster: " + cluster.getName());
            }
        }
        catch (RuntimeException ex)
        {
            LOGGER.error("Error initializing cluster factory", ex);
            try
            {
                tx.rollback();
            }
            catch(RuntimeException ex1)
            {
                LOGGER.error("Cannot roll back transaction", ex1);
            }
            throw new ClusterInitializationException(ex);
        }
        finally
        {
            initSession.close();
        }
    }


    /**
     * Gets a <code>Cluster</code> instance for the current node topology.
     *
     * @param name the name of the cluster
     * @return the <code>Cluster</code> - never null
     */
    public synchronized Cluster getCluster(String name)
    {
        if (name == null)
        {
            throw new ClusterInitializationException();
        }
        Cluster cluster = CLUSTER_MAP.get(name);
        if (cluster == null)
        {
            Session session = null;
            Transaction tx = null;
            try
            {
                LOGGER.info("Creating new cluster: " + name);
                session = sessionFactory.openSession();
                tx = session.beginTransaction();
                cluster = new com.netblue.bruce.cluster.persistence.Cluster();
                cluster.setName(name);
                CLUSTER_MAP.put(name, cluster);
                session.save(cluster);
                tx.commit();
            }
            catch (RuntimeException e)
            {
                LOGGER.error("Error initializing cluster factory", e);
                try
                {
                    tx.rollback();
                }
                catch(RuntimeException ex1)
                {
                    LOGGER.error("Cannot roll back transaction", ex1);
                }
                throw new ExceptionInInitializerError(e);
            }
            finally
            {
                session.close();
            }
        }
        return cluster;
    }

    /**
     * Gets a <code>Node</code> instance for the current node topology.
     *
     * @param name the name of the node
     * @return the <code>Node</code> or null if not found
     */
    public synchronized com.netblue.bruce.cluster.Node getNode(String name)
    {
        if (name == null)
        {
            return null;
        }
        Session session = null;
        Transaction tx = null;
        try
        {
            session = sessionFactory.openSession();
            tx = session.beginTransaction();
            List nodeList = session.createCriteria(com.netblue.bruce.cluster.Node.class).add(Restrictions.eq("name", name)).list();
            tx.rollback();
            if (nodeList.size() > 0)
            {
                return (com.netblue.bruce.cluster.Node) nodeList.get(0);
            }

        }
        finally
        {
            session.close();
        }
        return null;
    }

    /**
     * Gets all <code>Cluster</code>s currently configured.
     *
     * @return a <code>Set</code> of all <code>Cluster</code>s - never null.
     */
    public Set<Cluster> getAllClusters()
    {
        return new HashSet<Cluster>(CLUSTER_MAP.values());
    }

}
