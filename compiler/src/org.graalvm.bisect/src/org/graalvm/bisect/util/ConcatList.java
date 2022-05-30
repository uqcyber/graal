/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.graalvm.bisect.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * A linked list with O(1) concatenation.
 * @param <T> the element type
 */
public class ConcatList<T> implements Iterable<T> {
    private static class ConcatListIterator<T> implements Iterator<T> {
        private Node<T> node;
        private ConcatListIterator(Node<T> node) {
            this.node = node;
        }

        @Override
        public boolean hasNext() {
            return node != null;
        }

        @Override
        public T next() {
            T item = node.item;
            node = node.next;
            return item;
        }
    }

    @Override
    public Iterator<T> iterator() {
        return new ConcatListIterator<>(head);
    }

    private static class Node<T> {
        private Node<T> next;
        private final T item;

        private Node(T item) {
            this.item = item;
        }
    }

    private Node<T> head = null;
    private Node<T> tail = null;

    /**
     * Adds an item at the end of the list.
     *
     * @param item the item to be added
     */
    public void add(T item) {
        Node<T> next = new Node<>(item);
        if (head == null) {
            assert tail == null;
            tail = head = next;
        } else {
            assert tail != null && tail.next == null;;
            tail.next = next;
            tail = next;
        }
    }

    /**
     * Concatenates the other list's elements with this list's elements. The other list will be empty after
     * the concatenation.
     *
     * @param otherList the other list that will be emptied
     */
    public void concat(ConcatList<T> otherList) {
        if (otherList.head == null) {
            assert otherList.tail == null;
            return;
        }
        assert otherList.tail != null;
        if (head == null) {
            assert tail == null;
            head = otherList.head;
            tail = otherList.tail;
        } else {
            assert tail != null && tail.next == null;;
            tail.next = otherList.head;
            tail = otherList.tail;
        }
        otherList.head = null;
        otherList.tail = null;
    }

    /**
     * Returns the items in a list.
     * @return a list of items
     */
    public List<T> toList() {
        List<T> list = new ArrayList<>();
        for (T item : this) {
            list.add(item);
        }
        return list;
    }

    /**
     * Returns the items in a set.
     * @return a set of items
     */
    public Set<T> toSet() {
        Set<T> set = new HashSet<>();
        for (T item : this) {
            set.add(item);
        }
        return set;
    }

    public boolean isEmpty() {
        assert (head == null) == (tail == null);
        return head == null;
    }
}
