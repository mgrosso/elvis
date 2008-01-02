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

import com.netblue.bruce.TestDatabaseHelper;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.AnnotationConfiguration;
import org.junit.*;
import static org.junit.Assert.*;

import java.sql.SQLException;

/**
 * Base class for using Hibernate persistence.  Provides setup of the session classes
 * @author lanceball
 */
public class HibernatePersistence // extends ReplicationTest
{
    private SessionFactory sessionFactory;

    /**
     * Get a SessionFactory initialized with the default configuration
     * @return the SessionFactory
     */
    public SessionFactory getSessionFactory()
    {
        return sessionFactory;
    }

    @BeforeClass public static void setupBeforeClass() throws SQLException {
	TestDatabaseHelper.createTestDatabase();
	// Using the admin tool, initialize the config database
	String[] args = new String[]{"-loadschema",
				     "-url", TestDatabaseHelper.buildUrl(TestDatabaseHelper.getTestDatabaseName())};
	com.netblue.bruce.admin.Main.main(args);
    }

    @Before public void setupBefore() {
	sessionFactory = new AnnotationConfiguration().configure().buildSessionFactory();
    }

    @After public void teardownAfter() {
        if (sessionFactory != null) {
            sessionFactory.close();
        }
    }
}
