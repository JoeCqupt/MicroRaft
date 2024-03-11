package io.microraft.impl;

import io.microraft.Ordered;
import io.microraft.QueryPolicy;
import io.microraft.RaftConfig;
import io.microraft.RaftNode;
import io.microraft.impl.local.LocalRaftGroup;
import io.microraft.impl.local.SimpleStateMachine;
import io.microraft.model.message.AppendEntriesSuccessResponse;
import io.microraft.report.RaftNodeReport;
import io.microraft.test.util.BaseTest;
import org.junit.After;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static io.microraft.test.util.AssertionUtils.eventually;
import static org.assertj.core.api.Assertions.assertThat;

public class AppendEntriesTest extends BaseTest {
    private LocalRaftGroup group;

    @After
    public void tearDown() {
        if (group != null) {
            group.destroy();
        }
    }
    
    @Test
    public void testRaftLeaderFailure() {
        RaftConfig config = RaftConfig.newBuilder().setLeaderHeartbeatTimeoutSecs(1).setLeaderHeartbeatTimeoutSecs(5)
                .build();
        group = LocalRaftGroup.newBuilder(3).setConfig(config).start();
        RaftNode leader = group.waitUntilLeaderElected();

        // the leader can replicate log entries to the followers, but it won't
        // get any response back since we are blocking responses here, so even
        // though it replicates our operation, it won't be able to commit it
        // and send us the response
        for (RaftNode follower : group.getNodesExcept(leader.getLocalEndpoint())) {
            group.dropMessagesTo(follower.getLocalEndpoint(), leader.getLocalEndpoint(),
                    AppendEntriesSuccessResponse.class);
        }

        String value = "value";
        leader.replicate(SimpleStateMachine.applyValue(value));

        // wait until the followers get the log entry by checking their log
        // indices repeatedly
        eventually(() -> {
            RaftNodeReport leaderReport = leader.getReport().join().getResult();
            long leaderLastLogIndex = leaderReport.getLog().getLastLogOrSnapshotIndex();
            assertThat(leaderLastLogIndex).isGreaterThan(0);
            for (RaftNode follower : group.getNodesExcept(leader.getLocalEndpoint())) {
                RaftNodeReport followerReport = follower.getReport().join().getResult();
                long followerLastLogIndex = followerReport.getLog().getLastLogOrSnapshotIndex();
                assertThat(followerLastLogIndex).isEqualTo(leaderLastLogIndex);
            }
        });

        // now the followers have our operation. let's kill the leader
        // now we don't know what happened to our first operation
        group.terminateNode(leader.getLocalEndpoint());

        // we will get a new leader in a second
        RaftNode newLeader = group.waitUntilLeaderElected();

        // we replicate our operation again
        newLeader.replicate(SimpleStateMachine.applyValue(value)).join();

        Ordered<List<String>> queryResult = newLeader.<List<String>>query(SimpleStateMachine.queryAllValues(),
                QueryPolicy.LEADER_LEASE, Optional.empty(), Optional.empty()).join();

        // it turns out that our operation is committed twice
        assertThat(queryResult.getResult()).hasSize(2);
    }

}
