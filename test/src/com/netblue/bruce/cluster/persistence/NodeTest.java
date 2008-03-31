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

import com.netblue.bruce.cluster.Node;
import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.criterion.Restrictions;
import static org.junit.Assert.*;
import org.junit.Test;

import java.util.List;

/**
 * Tests the persisted Node class.
 *
 * @author lanceball
 */
public class NodeTest extends HibernatePersistenceTest
{
    /**
     * Tests whether we can set nodes as available or not
     */
    @Test
    public void testAvailable()
    {
        SessionFactory sessionFactory = getSessionFactory();
        Session session = sessionFactory.openSession();
        Transaction transaction = null;
        try
        {
            transaction = session.beginTransaction();
            List nodes = getAllNodes(session);

            // Our default data set has all nodes available
            for (Object object : nodes)
            {
                Node node = (Node) object;

                // Check that they are available, and then change the state to unavailable
                assertTrue("Default state should be available", node.isAvailable());
                node.isAvailable(false);
            }
            transaction.commit();

            // Now get them again and see if the state was persisted as expected
            transaction.begin();
            nodes = getAllNodes(session);
            transaction.commit();

            // Our default data set has all nodes available
            for (Object object : nodes)
            {
                Node node = (Node) object;

                // Check that the available state was persisted
                assertFalse("State was not persisted", node.isAvailable());
            }
        }
        catch (Exception e)
        {
            if (transaction != null)
            {
                transaction.rollback();
            }
            fail(e.getLocalizedMessage());
        }
        finally
        {
            session.close();
        }
    }

    @Test
    public void testEquals()
    {
        SessionFactory sessionFactory = getSessionFactory();
        Session session = sessionFactory.openSession();
        Node nodeOne = getNode(session, "Cluster 0 - Primary master");
        Node copy = copyNode(nodeOne);
        assertEquals(nodeOne, copy);
        copy.setName("Another name");               
        assertFalse(nodeOne.equals(copy));
        session.close();
        sessionFactory.close();
    }

    /**
     * Makes an identical copy of <code>node</code> 
     * @param node
     * @return
     */
    private Node copyNode(final Node node)
    {
        Node copy = new com.netblue.bruce.cluster.persistence.Node();
        ((com.netblue.bruce.cluster.persistence.Node)copy).id = node.getId();
        copy.setName(node.getName());
        copy.setCluster(node.getCluster());
        copy.setIncludeTable(node.getIncludeTable());
        copy.setUri(node.getUri());
        copy.isAvailable(node.isAvailable());
        return copy;
    }

    private List getAllNodes(final Session session)
    {
        List nodes = session.createCriteria(Node.class).list();
        assertEquals(5, nodes.size());
        Object firstItem = nodes.get(0);
        assertTrue("Unexpected type found.  Expected " + Node.class.getName() + ", but was " + firstItem.getClass().getName(), firstItem instanceof Node);
        return nodes;
    }

    @SuppressWarnings("unchecked")
    private Node getNode(final Session session, final String name)
    {
        Criteria nodeCriteria = session.createCriteria(Node.class).add(Restrictions.eq("name", name));
        List<Node> nodes = nodeCriteria.list();
        assertEquals(1, nodes.size());
        return nodes.get(0);
    }


}
