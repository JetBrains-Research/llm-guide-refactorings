package com.intellij.ml.llm.template.testdata;

public class KafkaAdminClientTest {
    @Test
    public void testDeleteRecords() throws Exception {
        HashMap<Integer, Node> nodes = new HashMap<>();
        nodes.put(0, new Node(0, "localhost", 8121));
        List<PartitionInfo> partitionInfos = new ArrayList<>();
        partitionInfos.add(new PartitionInfo("my_topic", 0, nodes.get(0), new Node[] {nodes.get(0)}, new Node[] {nodes.get(0)}));
        partitionInfos.add(new PartitionInfo("my_topic", 1, nodes.get(0), new Node[] {nodes.get(0)}, new Node[] {nodes.get(0)}));
        partitionInfos.add(new PartitionInfo("my_topic", 2, null, new Node[] {nodes.get(0)}, new Node[] {nodes.get(0)}));
        partitionInfos.add(new PartitionInfo("my_topic", 3, nodes.get(0), new Node[] {nodes.get(0)}, new Node[] {nodes.get(0)}));
        partitionInfos.add(new PartitionInfo("my_topic", 4, nodes.get(0), new Node[] {nodes.get(0)}, new Node[] {nodes.get(0)}));
        Cluster cluster = new Cluster("mockClusterId", nodes.values(),
                partitionInfos, Collections.emptySet(),
                Collections.emptySet(), nodes.get(0));

        TopicPartition myTopicPartition0 = new TopicPartition("my_topic", 0);
        TopicPartition myTopicPartition1 = new TopicPartition("my_topic", 1);
        TopicPartition myTopicPartition2 = new TopicPartition("my_topic", 2);
        TopicPartition myTopicPartition3 = new TopicPartition("my_topic", 3);
        TopicPartition myTopicPartition4 = new TopicPartition("my_topic", 4);

        try (AdminClientUnitTestEnv env = new AdminClientUnitTestEnv(cluster)) {
            env.kafkaClient().setNodeApiVersions(NodeApiVersions.create());

            DeleteRecordsResponseData m = new DeleteRecordsResponseData();
            m.topics().add(new DeleteRecordsResponseData.DeleteRecordsTopicResult().setName(myTopicPartition0.topic())
                    .setPartitions(new DeleteRecordsResponseData.DeleteRecordsPartitionResultCollection(asList(
                            new DeleteRecordsResponseData.DeleteRecordsPartitionResult()
                                    .setPartitionIndex(myTopicPartition0.partition())
                                    .setLowWatermark(3)
                                    .setErrorCode(Errors.NONE.code()),
                            new DeleteRecordsResponseData.DeleteRecordsPartitionResult()
                                    .setPartitionIndex(myTopicPartition1.partition())
                                    .setLowWatermark(DeleteRecordsResponse.INVALID_LOW_WATERMARK)
                                    .setErrorCode(Errors.OFFSET_OUT_OF_RANGE.code()),
                            new DeleteRecordsResponseData.DeleteRecordsPartitionResult()
                                    .setPartitionIndex(myTopicPartition3.partition())
                                    .setLowWatermark(DeleteRecordsResponse.INVALID_LOW_WATERMARK)
                                    .setErrorCode(Errors.NOT_LEADER_OR_FOLLOWER.code()),
                            new DeleteRecordsResponseData.DeleteRecordsPartitionResult()
                                    .setPartitionIndex(myTopicPartition4.partition())
                                    .setLowWatermark(DeleteRecordsResponse.INVALID_LOW_WATERMARK)
                                    .setErrorCode(Errors.UNKNOWN_TOPIC_OR_PARTITION.code())
                    ).iterator())));

            List<MetadataResponse.TopicMetadata> t = new ArrayList<>();
            List<MetadataResponse.PartitionMetadata> p = new ArrayList<>();
            p.add(new MetadataResponse.PartitionMetadata(Errors.NONE, myTopicPartition0,
                    Optional.of(nodes.get(0).id()), Optional.of(5), singletonList(nodes.get(0).id()),
                    singletonList(nodes.get(0).id()), Collections.emptyList()));
            p.add(new MetadataResponse.PartitionMetadata(Errors.NONE, myTopicPartition1,
                    Optional.of(nodes.get(0).id()), Optional.of(5), singletonList(nodes.get(0).id()),
                    singletonList(nodes.get(0).id()), Collections.emptyList()));
            p.add(new MetadataResponse.PartitionMetadata(Errors.LEADER_NOT_AVAILABLE, myTopicPartition2,
                    Optional.empty(), Optional.empty(), singletonList(nodes.get(0).id()),
                    singletonList(nodes.get(0).id()), Collections.emptyList()));
            p.add(new MetadataResponse.PartitionMetadata(Errors.NONE, myTopicPartition3,
                    Optional.of(nodes.get(0).id()), Optional.of(5), singletonList(nodes.get(0).id()),
                    singletonList(nodes.get(0).id()), Collections.emptyList()));
            p.add(new MetadataResponse.PartitionMetadata(Errors.NONE, myTopicPartition4,
                    Optional.of(nodes.get(0).id()), Optional.of(5), singletonList(nodes.get(0).id()),
                    singletonList(nodes.get(0).id()), Collections.emptyList()));

            t.add(new MetadataResponse.TopicMetadata(Errors.NONE, "my_topic", false, p));

            env.kafkaClient().prepareResponse(RequestTestUtils.metadataResponse(cluster.nodes(), cluster.clusterResource().clusterId(), cluster.controller().id(), t));
            env.kafkaClient().prepareResponse(new DeleteRecordsResponse(m));

            Map<TopicPartition, RecordsToDelete> recordsToDelete = new HashMap<>();
            recordsToDelete.put(myTopicPartition0, RecordsToDelete.beforeOffset(3L));
            recordsToDelete.put(myTopicPartition1, RecordsToDelete.beforeOffset(10L));
            recordsToDelete.put(myTopicPartition2, RecordsToDelete.beforeOffset(10L));
            recordsToDelete.put(myTopicPartition3, RecordsToDelete.beforeOffset(10L));
            recordsToDelete.put(myTopicPartition4, RecordsToDelete.beforeOffset(10L));

            DeleteRecordsResult results = env.adminClient().deleteRecords(recordsToDelete);

            // success on records deletion for partition 0
            Map<TopicPartition, KafkaFuture<DeletedRecords>> values = results.lowWatermarks();
            KafkaFuture<DeletedRecords> myTopicPartition0Result = values.get(myTopicPartition0);
            long lowWatermark = myTopicPartition0Result.get().lowWatermark();
            assertEquals(lowWatermark, 3);

            // "offset out of range" failure on records deletion for partition 1
            KafkaFuture<DeletedRecords> myTopicPartition1Result = values.get(myTopicPartition1);
            try {
                myTopicPartition1Result.get();
                fail("get() should throw ExecutionException");
            } catch (ExecutionException e0) {
                assertTrue(e0.getCause() instanceof OffsetOutOfRangeException);
            }

            // "leader not available" failure on metadata request for partition 2
            KafkaFuture<DeletedRecords> myTopicPartition2Result = values.get(myTopicPartition2);
            try {
                myTopicPartition2Result.get();
                fail("get() should throw ExecutionException");
            } catch (ExecutionException e1) {
                assertTrue(e1.getCause() instanceof LeaderNotAvailableException);
            }

            // "not leader for partition" failure on records deletion for partition 3
            KafkaFuture<DeletedRecords> myTopicPartition3Result = values.get(myTopicPartition3);
            try {
                myTopicPartition3Result.get();
                fail("get() should throw ExecutionException");
            } catch (ExecutionException e1) {
                assertTrue(e1.getCause() instanceof NotLeaderOrFollowerException);
            }

            // "unknown topic or partition" failure on records deletion for partition 4
            KafkaFuture<DeletedRecords> myTopicPartition4Result = values.get(myTopicPartition4);
            try {
                myTopicPartition4Result.get();
                fail("get() should throw ExecutionException");
            } catch (ExecutionException e1) {
                assertTrue(e1.getCause() instanceof UnknownTopicOrPartitionException);
            }
        }
    }

    private int foo1() {
        int x = 0;
        return x
    }

    private int foo2()
    {
        int x = 0;
        return x
    }

    private int foo3()
    {
        {
            int y = 10;
        }
        int x = 0;
        return x;
    }

    private int foo4() {
        int x = 0;
        int y = 2;
        int sum = x + y;
        return sum; }
}