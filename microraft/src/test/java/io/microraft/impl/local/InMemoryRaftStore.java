/*
 * Original work Copyright (c) 2008-2020, Hazelcast, Inc.
 * Modified work Copyright 2020, MicroRaft.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.microraft.impl.local;

import io.microraft.RaftEndpoint;
import io.microraft.impl.log.RaftLog;
import io.microraft.impl.model.log.DefaultSnapshotEntry.DefaultSnapshotEntryBuilder;
import io.microraft.model.log.LogEntry;
import io.microraft.model.log.SnapshotChunk;
import io.microraft.model.log.SnapshotEntry;
import io.microraft.persistence.RaftStore;
import io.microraft.persistence.RestoredRaftState;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * A very simple in-memory {@link RaftStore} implementation used for testing.
 *
 * @author mdogan
 * @author metanet
 */
public final class InMemoryRaftStore
        implements RaftStore {

    private RaftEndpoint localEndpoint;
    private Collection<RaftEndpoint> initialMembers;
    private int term;
    private RaftEndpoint votedFor;
    private RaftLog raftLog;
    private List<SnapshotChunk> snapshotChunks = new ArrayList<>();

    public InMemoryRaftStore(int logCapacity) {
        this.raftLog = RaftLog.create(logCapacity);
    }

    @Override
    public synchronized void open() {
    }

    @Override
    public synchronized void persistInitialMembers(@Nonnull RaftEndpoint localEndpoint,
                                                   @Nonnull Collection<RaftEndpoint> initialMembers) {
        this.localEndpoint = localEndpoint;
        this.initialMembers = initialMembers;
    }

    @Override
    public synchronized void persistTerm(int term, @Nullable RaftEndpoint votedFor) {
        this.term = term;
        this.votedFor = votedFor;
    }

    @Override
    public synchronized void persistLogEntry(@Nonnull LogEntry logEntry) {
        raftLog.appendEntry(logEntry);
    }

    @Override
    public synchronized void persistSnapshotChunk(@Nonnull SnapshotChunk snapshotChunk) {
        snapshotChunks.add(snapshotChunk);

        if (snapshotChunk.getSnapshotChunkCount() == snapshotChunks.size()) {
            snapshotChunks.sort(Comparator.comparingInt(SnapshotChunk::getSnapshotChunkIndex));
            SnapshotEntry snapshotEntry = new DefaultSnapshotEntryBuilder().setTerm(snapshotChunk.getTerm())
                                                                           .setIndex(snapshotChunk.getIndex())
                                                                           .setSnapshotChunks(snapshotChunks)
                                                                           .setGroupMembersLogIndex(
                                                                                   snapshotChunk.getGroupMembersLogIndex())
                                                                           .setGroupMembers(snapshotChunk.getGroupMembers())
                                                                           .build();
            raftLog.setSnapshot(snapshotEntry);
            snapshotChunks = new ArrayList<>();
        }
    }

    @Override
    public synchronized void truncateLogEntriesFrom(long logIndexInclusive) {
        raftLog.truncateEntriesFrom(logIndexInclusive);
    }

    @Override
    public synchronized void truncateSnapshotChunksUntil(long logIndexInclusive) {
        snapshotChunks.clear();
    }

    @Override
    public synchronized void flush() {
    }

    @Override
    public synchronized void close() {
    }

    public synchronized RestoredRaftState toRestoredRaftState() {
        List<LogEntry> entries;
        if (raftLog.snapshotIndex() < raftLog.lastLogOrSnapshotIndex()) {
            entries = raftLog.getLogEntriesBetween(raftLog.snapshotIndex() + 1, raftLog.lastLogOrSnapshotIndex());
        } else {
            entries = Collections.emptyList();
        }

        return new RestoredRaftState(localEndpoint, initialMembers, term, votedFor, raftLog.snapshotEntry(), entries);
    }

}
