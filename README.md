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

### Motivation

We liked the flexible per table trigger nature of slony, but didn't like the
message complexity of Slony which is O((Nodes * Changes)^2) since all Slony
cluster members are required to know the status of every other member.

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

Slave threads update slavesnapshotstatus on the slave database to mark their 
progress.

### Further reading

see javasrc/com/netblue/bruce/SlaveRunner.java, starting from doWork().

see schema/replication-helpers.sql and schema/replication-ddl.sql


### Restrictions and issues

- It does not handle transaction id rollover, requiring complex manual
  intervention when your databse XID, an unsigned 32bit integer, cycles across
  2^32 and goes back to zero.

- slavesnapshotstatus requires frequent vacuuming. See the postgresql vacuum
  command documentation.

- There is no documentation or support.

- It is possible to change the replication topology on the fly but it is tricky
  and requires a bit of planning.

- The triggers enforce a requirement for a primary key; this protects against
  unintential UPDATE and DELETE but there are some valid INSERT only scenarios
  that would work if this wasn't enforced.

- PUBLIC can insert changes into the transactionlog and snapshots table; you
  could manually narrow this to all users who need to be able to make changes.
  This drawback is due to the fact that __all__ changes are comingled in one
  table; that design decision is driven by concerns over the complexity of
  alternatives.   For instance one possible approach to this is to maintain a
  separate permissions table and use a trigger to validate that at insert time,
  but note this check would be applied for each row changed.

- When a change is applied to a row the before images must match or an error is
  thrown and replication to the node stops.  There should be more flexible ways
  to deal with issues that arise from rare instances of corruption without
  magnifying the impact.  More flexible conflict resolution policies would also
  enable multi master.

- The tree topology should be specified using yaml or a custom config file
  format, or a simply as command line options to the individual daemons; this
  would remove the single point of failure of the schema node.

Status
------

Elvis saw several years of constant high volume production use with Postgresql
8.0, 8.1, and 8.3.  There is a an issue on 8.2 that prevents it being used for
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

The first developer was Bob Klahn who wrote the first version of the c plugin.
Lance Ball wrote early versions of the java daemon.  Matt Grosso provided early
kibitzing.  Further development needed to make it stable in production was done
mostly by Matt Grosso, with important contributions from Kalpesh Patel and
Abhay Saswade.


