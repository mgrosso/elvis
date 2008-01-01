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

import java.util.*;

/**
 * A TidsBetweenSnapshotsSet is a specialized sorted set, representing all transactions occuring
 * between two snapshots, minus any elements removed using Set.remove*() (or its cousins).
 *
 * It should be noted, this is not a general purpose implementation of SortedSet, and 
 * violates the 'general-purpose sorted set' contract as discussed in {@link java.util.SortedSet}.
 * Several of the {@link java.util.SortedSet} and {@link java.util.Set} methods are not supported, 
 * and will throw NoSuchMethodError if called.
 *
 * @author rklahn
 * @version $Id: $
 */

public class TidsBetweenSnapshotsSet implements SortedSet<TransactionID> {
    
    /* 
     * @param s1 - a {@link Snapshot}
     * @param s2 - a {@link Snapshot}
     */
    public TidsBetweenSnapshotsSet(Snapshot s1, Snapshot s2) {
	// s1 is always <= to s2
	if (s1.getMaxXid().compareTo(s2.getMaxXid()) > 0) {
	    this.s1 = s2;
	    this.s2 = s1;
	} else {
	    this.s1 = s1;
	    this.s2 = s2;
	}
    }

    // Override Object.toString()

    /**
     * {@inheritDoc}
     */
    public String toString() {
	String retval = "";
	for (TransactionID i=s1.getMinXid();i.compareTo(s2.getMaxXid())<0;i=i.nextNormal()) {
	    if (s1.transactionIDGE(i) && s2.transactionIDLT(i) && (!removedElements.contains(i))) {
		if (retval.equals("")) {
		    retval=i.toString();
		} else {
		    retval=retval+","+i.toString();
		}
	    }
	}
	return retval;
    }

    // Interface java.util.SortedSet<E> methods

    /**
     * {@inheritDoc}
     */
    public Comparator<? super TransactionID> comparator() {
	// We always use natural ordering, therefore, always return null 
	return null;
    }

    /**
     * {@inheritDoc}
     */
    public TransactionID first() {
	for (TransactionID i=s1.getMinXid();i.compareTo(s2.getMaxXid())<0;i=i.nextNormal()) {
	    if (s1.transactionIDGE(i) && s2.transactionIDLT(i) && (!removedElements.contains(i))) {
		return i;
	    }
	}
	throw new NoSuchElementException();
    }

    /**
     * @throws NoSuchMethodError
     */
    public SortedSet<TransactionID> headSet(TransactionID toElement) {
	throw new NoSuchMethodError();
    }

    /**
     * {@inheritDoc}
     */
    public TransactionID last() {
	for (TransactionID i=s2.getMaxXid();i.compareTo(s1.getMinXid())>=0;i=i.priorNormal()) {
	    if (s1.transactionIDGE(i) && s2.transactionIDLT(i) && (!removedElements.contains(i))) {
		return i;
	    }
	}
	throw new NoSuchElementException();
    }
    
    /**
     * @throws NoSuchMethodError
     */
    public SortedSet<TransactionID> subSet(TransactionID fromElement,TransactionID toElement) {
	throw new NoSuchMethodError();
    }

    /**
     * @throws NoSuchMethodError
     */
    public SortedSet<TransactionID> tailSet(TransactionID fromElement) {
	throw new NoSuchMethodError();
    }

    // Interface java.util.Set methods

    /**
     * @throws NoSuchMethodError
     */
    public boolean add(TransactionID t) {
	throw new NoSuchMethodError();
    }

    /**
     * @throws NoSuchMethodError
     */
    public boolean addAll(Collection<? extends TransactionID> c) {
	throw new NoSuchMethodError();
    }

    /**
     * @throws NoSuchMethodError
     */
    public void clear() {
	throw new NoSuchMethodError();
    }

    /**
     * {@inheritDoc}
     */
    public boolean contains(Object o) {
	TransactionID i = (TransactionID) o;
	return (s1.transactionIDGE(i) && s2.transactionIDLT(i) && (!removedElements.contains(i)));
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    public boolean containsAll(Collection<?> o) {
	Collection<TransactionID> c = (Collection<TransactionID>) o;
	for (TransactionID t:c) {
	    if (!contains(t)) {
		return false;
	    }
	}
	return true;
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    public boolean equals(Object o) {
	try {
	    TidsBetweenSnapshotsSet t = (TidsBetweenSnapshotsSet) o;
	    if (!this.s1.equals(t.s1)) {
		return false;
	    }
	    if (!this.s2.equals(t.s2)) {
		return false;
	    }
	    if (!this.removedElements.equals(t.removedElements)) {
		return false;
	    }
	    return true;
	} catch (ClassCastException e) {
	    SortedSet<TransactionID> sst = (SortedSet<TransactionID>) o;
	    return (this.containsAll(sst) && sst.containsAll(this));
	}
    }

    /**
     * {@inheritDoc}
     */
    public int hashCode() {
	int retVal = 0;
	for (TransactionID i=s1.getMinXid();i.compareTo(s2.getMaxXid())<0;i=i.nextNormal()) {
	    if (s1.transactionIDGE(i) && s2.transactionIDLT(i) && (!removedElements.contains(i))) {
		retVal+=i.hashCode();
	    }
	}
	return retVal;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isEmpty() {
	for (TransactionID i=s1.getMinXid();i.compareTo(s2.getMaxXid())<0;i=i.nextNormal()) {
	    if (s1.transactionIDGE(i) && s2.transactionIDLT(i) && (!removedElements.contains(i))) {
		return false;
	    }
	}
	return true;
    }

    /**
     * {@inheritDoc}
     */
    public Iterator<TransactionID> iterator() {
	return new TBSSIterator(s1,s2,removedElements);
    }

    /**
     * {@inheritDoc}
     */
    public boolean remove(Object o) {
	TransactionID xid = (TransactionID) o;
	return !removedElements.add(xid);
    }
    
    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    public boolean removeAll(Collection<?> c) {
	Collection<TransactionID> t = (Collection<TransactionID>) c;
	return !removedElements.addAll(t);
    }

    /**
     * @throws NoSuchMethodError
     */
    public boolean retainAll(Collection<?> c) {
	throw new NoSuchMethodError();
    }

    /**
     * {@inheritDoc}
     */
    public int size() {
	int retVal = 0;
	for (TransactionID i=s1.getMinXid();i.compareTo(s2.getMaxXid())<0;i=i.nextNormal()) {
	    if (s1.transactionIDGE(i) && s2.transactionIDLT(i) && (!removedElements.contains(i))) {
		retVal++;
	    }
	}
	return retVal;
    }

    /**
     * @throws NoSuchMethodError
     */
    public Object[] toArray() {
	throw new NoSuchMethodError();
    }

    /**
     * @throws NoSuchMethodError
     */
    public <T> T[] toArray(T[] a) {
	throw new NoSuchMethodError();
    }

    class TBSSIterator implements Iterator<TransactionID> {
	public TBSSIterator (Snapshot s1, Snapshot s2, TreeSet<TransactionID> removedElements) {
	    this.s1=s1;
	    this.s2=s2;
	    this.curPos=this.s1.getMinXid();
	    this.removedElements=removedElements;
	}

	public boolean hasNext() {
	    try {
		TransactionID before = this.curPos;
		this.next();
		this.curPos=before;
	    } catch (NoSuchElementException e) {
		return false;
	    }
	    return true;
	}

	public TransactionID next() {
	    while (this.curPos.compareTo(s2.getMaxXid())<0) {
		TransactionID cmp = this.curPos;
		this.curPos=this.curPos.nextNormal();
		if (s1.transactionIDGE(cmp) &&
		    s2.transactionIDLT(cmp) &&
			(!removedElements.contains(cmp))) {
		    return cmp;
		}
	    }
	    throw new NoSuchElementException();
	}

	public void remove() {
	    throw new NoSuchMethodError();
	}

	private TransactionID curPos;
	private Snapshot s1,s2;
	private TreeSet<TransactionID> removedElements;
    }
    
    private Snapshot s1,s2;
    private TreeSet<TransactionID> removedElements = new TreeSet<TransactionID>();
    private static final Logger logger = Logger.getLogger(TidsBetweenSnapshotsSet.class);
}
