/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.foreign;

import jdk.internal.misc.InnocuousThread;
import jdk.internal.vm.annotation.ForceInline;

import java.util.Objects;

/**
 * A confined session, which features an owner thread. The liveness check features an additional
 * confinement check - that is, calling any operation on this session from a thread other than the
 * owner thread will result in an exception. Because of this restriction, checking the liveness bit
 * can be performed in plain mode.
 */
final class ConfinedSession extends MemorySessionImpl {

    public ConfinedSession(Thread owner) {
        super(Objects.requireNonNull(owner), new ConfinedResourceList());
    }

    @Override
    @ForceInline
    public void acquire0() {
        assertIsAccessibleByCurrentThread(false);
        super.acquire0();
    }

    void justClose() {
        assertIsAccessibleByCurrentThread(true);
        super.justClose();
    }

    void assertIsAccessibleByCurrentThread(boolean cleanerAllowed) {
        Thread currentThread = Thread.currentThread();
        if (cleanerAllowed && (currentThread instanceof InnocuousThread))
            return;
        if (owner == currentThread)
            return;
        throw wrongThread();
    }
    /**
     * A confined resource list; no races are possible here.
     */
    static final class ConfinedResourceList extends ResourceList {
        @Override
        void add(ResourceCleanup cleanup) {
            if (fst != ResourceCleanup.CLOSED_LIST) {
                cleanup.next = fst;
                fst = cleanup;
            } else {
                throw alreadyClosed();
            }
        }

        @Override
        void cleanup() {
            if (fst != ResourceCleanup.CLOSED_LIST) {
                ResourceCleanup prev = fst;
                fst = ResourceCleanup.CLOSED_LIST;
                cleanup(prev);
            } else {
                throw alreadyClosed();
            }
        }
    }
}
