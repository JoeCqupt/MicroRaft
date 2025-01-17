/*
 * Original work Copyright (c) 2008-2020, Hazelcast, Inc.
 * Modified work Copyright (c) 2020, MicroRaft.
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

package io.microraft.persistence;

import java.io.IOException;
import java.util.List;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;

import io.microraft.RaftConfig;
import io.microraft.RaftNode;
import io.microraft.lifecycle.RaftNodeLifecycleAware;
import io.microraft.model.RaftModel;
import io.microraft.model.RaftModelFactory;
import io.microraft.model.log.LogEntry;
import io.microraft.model.log.RaftGroupMembersView;
import io.microraft.model.log.SnapshotChunk;
import io.microraft.model.persistence.RaftEndpointPersistentState;
import io.microraft.model.persistence.RaftTermPersistentState;
import io.microraft.statemachine.StateMachine;

/**
 * This interface is used for persisting only the internal state of the Raft
 * consensus algorithm. Internal state of {@link StateMachine} implementations
 * are not persisted with this interface.
 * <p>
 * A {@link RaftStore} implementation can implement
 * {@link RaftNodeLifecycleAware} to perform initialization and clean up work
 * during {@link RaftNode} startup and termination. {@link RaftNode} calls
 * {@link RaftNodeLifecycleAware#onRaftNodeStart()} before calling any other
 * method on {@link RaftStore}, and finally calls
 * {@link RaftNodeLifecycleAware#onRaftNodeTerminate()} on termination.
 *
 * @see RaftModel
 * @see RaftModelFactory
 * @see RaftNode
 */
public interface RaftStore {

    /**
     * Persists and flushes the given local Raft endpoint and its voting flag.
     * <p>
     * When this method returns, all the provided data has become durable.
     *
     * @param localEndpointPersistentState
     *            the local endpoint state to be persisted
     *
     * @throws IOException
     *             if any failure occurs during persisting the given values
     */
    void persistAndFlushLocalEndpoint(@Nonnull RaftEndpointPersistentState localEndpointPersistentState)
            throws IOException;

    /**
     * Persists and flushes the given initial Raft group members.
     * <p>
     * When this method returns, all the provided data has become durable.
     *
     * @param initialGroupMembers
     *            the initial Raft group member list to persist
     *
     * @throws IOException
     *             if any failure occurs during persisting the given values
     */
    void persistAndFlushInitialGroupMembers(@Nonnull RaftGroupMembersView initialGroupMembers) throws IOException;

    /**
     * Persists the term and the Raft endpoint that the local Raft node voted for in
     * the given term.
     * <p>
     * When this method returns, all the provided data has become durable.
     *
     * @param termPersistentState
     *            the term state to be persisted
     *
     *
     * @throws IOException
     *             if any failure occurs during persisting the given values
     */
    void persistAndFlushTerm(@Nonnull RaftTermPersistentState termPersistentState) throws IOException;

    /**
     * Persists the given log entries.
     * <p>
     * Log entries are appended to the Raft log with sequential log indices. The
     * first log index is 1.
     * <p>
     * A block of consecutive log entries has no gaps in the indices, but a gap can
     * appear between a snapshot entry and its preceding regular log entry. This
     * happens in an edge case where a follower has fallen so far behind that the
     * missing entries are no longer available from the leader. In that case the
     * leader will send its snapshot entry instead.
     * <p>
     * In another rare failure scenario, MicroRaft can delete a range of the latest
     * entries which are uncommitted and roll back to a previous log index which is
     * known to be committed. Consider the following case where Raft persists 3 log
     * entries and then deletes entries from index=2:
     * <ul>
     * <li>persistLogEntries(1, 2, 3)
     * <li>truncateLogEntriesFrom(2)
     * </ul>
     * After this call sequence log indices will remain sequential and the next
     * persistLogEntries() call will be for <em>index=2</em>.
     *
     * @param logEntries
     *            the list of log entries to be persisted
     *
     * @throws IOException
     *             if any failure occurs during persisting the given log entry
     *
     * @see #flush()
     * @see #persistSnapshotChunk(SnapshotChunk)
     * @see #truncateLogEntriesFrom(long)
     * @see RaftConfig
     */

    void persistLogEntries(@Nonnull List<LogEntry> logEntries) throws IOException;

    /**
     * Persists the given snapshot chunk.
     * <p>
     * A snapshot is persisted with at least 1 chunk. The number of chunks in a
     * snapshot is provided via {@link SnapshotChunk#getSnapshotChunkCount()}. A
     * snapshot is considered to be complete when all of its chunks are provided to
     * this method in any order, and {@link #flush()} will be called afterwards.
     * <p>
     * MicroRaft takes snapshots at a predetermined interval, controlled by
     * {@link RaftConfig#getCommitCountToTakeSnapshot()}. For instance, if it is
     * 100, snapshots will occur at indices 100, 200, 300, and so on.
     * <p>
     * The snapshot index can lag behind the index of the highest log entry which
     * was already persisted and flushed, but there is an upper bound to this
     * difference, controlled by {@link RaftConfig#getMaxPendingLogEntryCount()}.
     * For instance, if it is 10, and a {@code persistSnapshot()} call is made with
     * <em>snapshotIndex=100</em>, the index of the preceding
     * {@code persistLogEntries()} call can be at most 110.
     * <p>
     * On the other hand, the snapshot index can also be ahead of the highest log
     * entry. This can happen when a Raft follower has fallen so far behind the
     * leader and the leader no longer holds the missing entries. In that case, the
     * follower receives a snapshot from the leader. There is no upper-bound on the
     * gap between the highest log entry and the index of the received snapshot.
     *
     * @param snapshotChunk
     *            the snapshot chunk object to persist
     *
     * @throws IOException
     *             if any failure occurs during persisting the given snapshot chunk
     *
     * @see #flush()
     * @see #persistLogEntries(List)
     * @see RaftConfig
     */
    void persistSnapshotChunk(@Nonnull SnapshotChunk snapshotChunk) throws IOException;

    /**
     * Rolls back the log by truncating all entries starting with the given index. A
     * truncated log entry is no longer valid and must not be restored (or at least
     * must be ignored during the restore process).
     * <p>
     * There is an upper-bound on the number of persisted log entries that can be
     * truncated afterwards, which is specified by
     * {@link RaftConfig#getMaxPendingLogEntryCount()} + 1. Say that it is 5 and the
     * highest persisted log entry index is 20. Then, at most 5 highest entries can
     * be truncated, hence truncation can start at index=16 or higher.
     *
     * @param logIndexInclusive
     *            the log index value from which the log entries must be truncated
     *
     * @throws IOException
     *             if any failure occurs during truncating the log entries
     *
     * @see #flush()
     * @see #persistLogEntries(List)
     * @see RaftConfig
     */
    void truncateLogEntriesFrom(@Nonnegative long logIndexInclusive) throws IOException;

    /**
     * MicroRaft calls this method after it successfully persists and flushes a
     * snapshot. This method is used for deleting log entries that are before the
     * snapshot index and are not needed to be restored.
     *
     * This is merely an optimization method and its side-effects can be sync'ed to
     * the storage when {@link #flush()} is called,
     *
     * @param logIndexInclusive
     *            the log index value until which the log entries must be truncated
     *
     * @throws IOException
     *             if any failure occurs during truncating the log entries
     */
    void truncateLogEntriesUntil(@Nonnegative long logIndexInclusive) throws IOException;

    /**
     * Deletes persisted snapshot chunks at the given log index.
     *
     * MicroRaft calls this method when it detects that it needs to start installing
     * a newer snapshot while there is a snapshot persisted partially. Those
     * snapshot chunks are no longer valid and must not be restored (or at least
     * must be ignored during the restore process).
     *
     * This is merely an optimization method and its side-effects can be sync'ed to
     * the storage when {@link #flush()} is called.
     *
     * @param logIndex
     *            the log index value at which some snapshot chunks are persisted.
     * @param snapshotChunkCount
     *            the number of snapshot chunks that could have been persisted.
     *
     * @throws IOException
     *             if any failure occurs during deletion.
     */
    void deleteSnapshotChunks(@Nonnegative long logIndex, @Nonnegative int snapshotChunkCount) throws IOException;

    /**
     * Forces all buffered (in any layer) Raft log changes to be written to the
     * storage and returns after those changes are written.
     * <p>
     * When this method returns, all the changes previously done via the other
     * methods have become durable.
     *
     * @throws IOException
     *             if any failure occurs during the flush operation
     *
     * @see #persistLogEntries(List)
     * @see #persistSnapshotChunk(SnapshotChunk)
     * @see #deleteSnapshotChunks(long, int)
     * @see #truncateLogEntriesFrom(long)
     */
    void flush() throws IOException;

}
