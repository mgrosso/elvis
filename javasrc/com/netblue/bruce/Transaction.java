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

import java.io.Serializable;
import java.util.*;

// -----------------------------------------------
// ${CLASS}
// -----------------------------------------------

/**
 * A Transaction is an Ordered Set of changes to rows. It is ordered such that row changes are safe
 * to apply in said order.
 *
 * @author rklahn
 * @version $Id$
 */
public class Transaction implements SortedSet<Change>, Serializable
{
    /**
     * @serial used to verify that the sender and receiver of a serialized object have loaded classes for that object
     * that are compatible with respect to serialization.
     */
    private static final long serialVersionUID = 1L;

    /**
     * @serial underlying Synchronized TreeSet containing all Changes
     */
    private final SortedSet<Change> changes = Collections.synchronizedSortedSet(new TreeSet<Change>());

    /**
     * An empty construtor, resulting in an empty transaction set
     */
    public Transaction() {}

    /**
     * A construtor, resulting in a one element set.
     *
     * @param c A change object, resulting transaction will contain this element.
     */
    public Transaction(Change c) {
        this.add(c);
    }

    /**
     * A constructor, resulting in the collections elements in the Transaction, in their natural order.
     *
     * @param t A Transaction, resulting Transaction will contain all elements of the Transaction, in their natural
     * order.
     */
    public Transaction(Transaction t)
    {
        this.addAll(t);
    }

    /**
     * Returns a hash code value for the object.
     * @see Object#hashCode() for more detailed information
     * @return hashcode for the object
     */
    @Override
    public int hashCode()
    {
        return changes.hashCode();
    }

    /**
     * Indicates whether some other object is "equal to" this one.
     * @param obj object to compare to this one
     * @return true if they are equal
     */
    public boolean equals(Object obj)
    {
        Transaction otherTransaction;
        try
        {
            otherTransaction = (Transaction) obj;
        }
        catch (Exception e)
        {
            return false;
        }
        return changes.equals(otherTransaction.changes);
    }

    /**
     * @return String representation of the Transaction
     */
    @Override
    public String toString()
    {
        return changes.toString();
    }

    
    /**
     * @return a sorted set containing TransactionIDs that appear among the Changes in this Transaction
     */
    public SortedSet<TransactionID> getTransactionIDs()
    {
        TreeSet<TransactionID> retVal = new TreeSet<TransactionID>();
        for (Change c:changes)
        {
            retVal.add(c.getXactionID());
        }
        return retVal;
    }

    /**
     * Returns true if the transactionIDs that appear among the Changes in this Transaction are between
     * fromSnapshot (inclusive) and toSnapshot (exclusive).
     * @param fromSnapshot low endpoint Snapshot (inclusive)
     * @param toSnapshot high endpoint Snapshot (exclusive)
     * @return true if all the transactionIDs that appear among the Changes in this Transaction are between
     * fromSnapshot (inclusive) and toSnapshot (exclusive)
     * @throws IllegalArgumentException if fromSnapshot is greater than toSnapshot
     * @throws NullPointerException if either fromSnapshot or toSnapshot is null
     */
    public boolean betweenSnapshots(final Snapshot fromSnapshot, final Snapshot toSnapshot)
    {
        if (fromSnapshot==null)
        {
            throw new NullPointerException("fromSnapshot should not be null");
        }
        if (toSnapshot==null)
        {
            throw new NullPointerException("toSnapshot should not be null");
        }
        if (fromSnapshot.compareTo(toSnapshot)>0)
        {
            throw new IllegalArgumentException("fromSnapshot must be less than toSnapshot");
        }
        SortedSet<TransactionID> ourXactionIDs = this.getTransactionIDs();
        SortedSet<TransactionID> snapshotXactionIDs = fromSnapshot.tIDsBetweenSnapshots(toSnapshot);
        ourXactionIDs.removeAll(snapshotXactionIDs);
        return ourXactionIDs.isEmpty();
    }

    /**
     * We always use the natural ordering of Change, thus, per the contract of SortedSet, always return null
     * @return null
     */
    public Comparator<? super Change> comparator()
    {
        return null;
    }

    /**
     * Returns a view of the portion of this Transaction whose elements range from fromChange, inclusive, to toChange,
     * exclusive. (If fromElement and toElement are equal, the returned sorted set is empty.)
     *
     * @see SortedSet#subSet(Object, Object) for a more detailed description of the exact nature of this method
     * @param fromChange low endpoint (inclusive) of the subSet
     * @param toChange high endpoint (exclusive) of the subSet
     * @return a view of the specified range within the Transaction
     */
    public SortedSet<Change> subSet(final Change fromChange, final Change toChange)
    {
        return changes.subSet(fromChange,toChange);
    }

    /**
     * Returns a view of the portion of this Transaction whose elements are strictly less than toChange.
     * @see SortedSet#headSet(Object) for a more detailed description of the exact nature of this method.
     * @param toChange high endpoint (exclusive) of the headSet
     * @return a view of the specified initial range of this Transaction
     */
    public SortedSet<Change> headSet(final Change toChange)
    {
        return changes.headSet(toChange);
    }

    /**
     * Returns a view of the portion of this Transaction whose elements are greater than or equal to fromChange
     * @see SortedSet#tailSet(Object) for a more detailed description of the exact nature of this method
     * @param fromChange low endpoint (inclusive) of the tailSet
     * @return a view of the specified final range of this transaction
     */
    public SortedSet<Change> tailSet(final Change fromChange)
    {
        return changes.tailSet(fromChange);
    }

    /**
     * Returns the first (lowest) Change currently in this Transaction.
     * @return the first (lowest) Change currently in this Transaction.
     */
    public Change first()
    {
        return changes.first();
    }

    /**
     * Returns the last (highest) Change currently in this Transaction.
     * @return the last (highest) Change currently in this Transaction.
     */
    public Change last()
    {
        return changes.last();
    }

    /**
     * Returns the number of Changes in this Transaction
     * @return number of Changes in this Transaction
     */
    public int size()
    {
        return changes.size();
    }

    /**
     * Returns true if this Transaction contains no Changes.
     * @return true if this Transaction contains no Changes
     */
    public boolean isEmpty()
    {
        return changes.isEmpty();
    }

    /**
     * Returns true if this Transaction contains the specified object.
     * @param o element whose presence in this set is to be tested
     * @return true if this set contains the specified element
     * @throws ClassCastException if the type of the specified element is not Change
     * @throws NullPointerException if the specified element is null
     */
    public boolean contains(final Object o)
    {
        Change c = (Change) o;
        return changes.contains(c);
    }

    /**
     * Returns an iterator over the Changes in this Transaction in their natural order
     * @return an iterator over the Changes in this Transaction
     */
    public Iterator<Change> iterator()
    {
        return changes.iterator();
    }

    /**
     * Returns an array containing all the Changes in this Transaction
     * @return an array containing all the Changes in this Transaction
     */
    public Object[] toArray()
    {
        return changes.toArray();
    }

    /**
     * Returns an array containing all of the Changes in this Transaction;
     * the runtime type of the returned array is that of the specified array.
     * @param ts the array into which the elements of this set are to be stored, if it is big enough;
     * otherwise, a new array of the same runtime type is allocated for this purpose.
     * @return an array containing the Changes of this Transaction.
     * @throws ArrayStoreException the runtime type of a is not a supertype of the runtime type of every element in
     * this Transaction (Change).
     * @throws NullPointerException if the specified array is null.
     */
    public <T> T[] toArray(final T[] ts)
    {
        return changes.toArray(ts);
    }

    /**
     * Adds the specified Change to this Transaction if it is not already present
     * @param change Change to add to the Transaction
     * @return true if this Transaction did not already contain Change
     */
    public boolean add(final Change change)
    {
        return changes.add(change);
    }

    /**
     * Removes the specified Change from this Transaction if it is present
     * @param o object to be removed from this set
     * @return true if the set contained the specified element
     */
    public boolean remove(final Object o)
    {
        return changes.remove(o);
    }

    /**
     * Returns true if this Transaction contains all of the elements of the specified collection.
     * If the specified collection is also a set, this method returns true if it is a subset of this set.
     * @param objects collection to be checked for containment in this Transaction
     * @return true if this Transaction contains all the elements of the specified collection
     */
    public boolean containsAll(final Collection<?> objects)
    {
        return changes.containsAll(objects);
    }

    /**
     * Adds all of the Changes in the specified collection to this Transaction if they're not already present
     * @param objects Collection to be added to this Transaction
     * @return true if this transaction changed as a result of the operation
     * @throws ClassCastException if the class of some element of the specified collection prevents it from being added to this Transaction
     * @throws NullPointerException if the specified collection contains one or more null elements
     */
    public boolean addAll(final Collection<? extends Change> objects)
    {
        return changes.addAll(objects);
    }

    /**
     * Retains only the Changes in this Transaction that are contained in the specified collection. In other words,
     * removes from this Transaction all of its Changes that are not contained in the specified collection. If the
     * specified collection is also a set, this operation effectively modifies this set so that its value is the
     * intersection of the two sets.
     * @param objects collection that defines which elements this set will retain.
     * @return true if this Transaction changed as a result of the call.
     * @throws ClassCastException if Change is incompatible with the specified collection
     * @throws NullPointerException if the specified collection is null.
     */
    public boolean retainAll(final Collection<?> objects)
    {
        return changes.retainAll(objects);
    }

    /**
     * Removes from this Transaction all of its Changes that are contained in the specified collection.
     * If the specified collection is also a set, this operation effectively modifies this set so that its
     * value is the asymmetric set difference of the two sets.
     * @param objects collection that defines which elements will be removed from this set.
     * @return true if this Transaction changed as a result of the call.
     * @throws ClassCastException if Change is incompatible with the specified collection.
     * @throws NullPointerException if the specified collection is null.
     */
    public boolean removeAll(final Collection<?> objects)
    {
        return changes.removeAll(objects);
    }

    /**
     * Removes all the changes from this Transaction
     */
    public void clear()
    {
        changes.clear();
    }
}
