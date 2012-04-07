Elvis, scalable Postgresql replication
===============================

What it is
----------

Elvis is a Postgresql replication project with several years of intense
production usage in several different contexts.  It is uses triggers to put
changes into a change table and daemons to apply those changes to slave
databases.  Transactional consistency is maintained at a granularity you
control.

It consists of a c plugin, some pgsql functions, and a java daemon.  The c 
plugin knows how to take changes and add them to the change table, and how 
to apply a change to a slave table.   

### Asynchronous, Master Slave with flexible topolgies

Replication is asynchronous but respects transactions; slaves recieve changes
in batches that correspond to groups of one or more transactions from the
master.  The granularity of the groups of transaction is determined by a 
snapshot table (more below).

The replication topology for a given table __should__ be a tree with
changes arising only at the root (more below). 


### Trigger Based So Topology Is At Table Granularity

To make a table the master you apply the master trigger.  To make it a slave
you apply the slave trigger.  If a table has both triggers it is a relay table.

You can flexibly mix and match which tables are masters and slaves on which 
machines.

### How changes are represented and propogated

The c function encodes changes into before and after strings and puts them into
the change table along with the current transaction id.

The daemon creates periodic transactions and notes their transaction ids into a
snapshot table.

The tree topology is stored in a special schema, a copy of which must be
available to daemons when they startup.

One daemon per master or relay node will have a thread for each slave; each
slave selects its changes from the database separately so they won't hold each
other up.  Changes are cached within the daemon to reduce load on the master
for the situation of many slaves.

### Restrictions and issues

- The triggers enforce a requirement for a primary key; this protects against
  unintential UPDATE and DELETE but there are some valid INSERT only scenarios
  that would work if this wasn't enforced.

- When a change is applied to a row the before images must match or an error is
  thrown and replication to the node stops.  There should be more flexible ways
  to deal with issues that arise from rare instances of corruption without
  magnifying the impact.  More flexible conflict resolution policies would also
  enable multi master.

- The tree topology should be specified using yaml or a custom config file
  format, or a simply as command line options to the individual daemons; this
  would remove the single point of failure of the schema node.

- The Java daemon should probably be in straight c so it can run anywhere
  postgresql does and reduce the number of languages required to master the 
  entire system. It also has some unnecessary jar file dependencies.

Status
------

Elvis saw several years of constant high volume production use with Postgresql
8.0 and 8.3.  There is a an issue on 8.2 that prevents it being used for
production.  It hasn't been tested on 9.x or developed or maintained for about
two years.


History and Credits
-------------------

Elvis was sponsored by Connexus, which was purchased by Epic Media in 2010.

Elvis is based on Slony. See slon2_20070108.txt for original design notes by Bob Klahn.

Elvis used to be named Bruce after the monty python skit in which everyone is
named Bruce because its easier that way. It was renamed to Elvis because "Elvis
has left the building".

### Credits 

The first developer was Bob Klahn who wrote the first version of the c plugin. Lance Ball wrote early versions of the java daemon.  Matt Grosso provided early kibitzing.  Further development needed to make it stable in production was done mostly by Matt Grosso, with important contributions from Kalpesh Patel and Abhay Saswade.


