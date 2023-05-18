# Classification of Extract Method suggestions

1. **Optimal region**

Suggestion is "clean" and covers the statements at the same level that could be extracted in a new function.

Examples:
* https://github.com/apache/kafka/blob/trunk/clients/src/test/java/org/apache/kafka/clients/admin/KafkaAdminClientTest.java#L2317-L2331
* https://github.com/apache/kafka/blob/trunk/clients/src/test/java/org/apache/kafka/clients/admin/KafkaAdminClientTest.java#L2338-L2343

2. **Region not including preceding comment line**

Suggestion is "clean" but it does not include the preceding comment line.

Examples:
* https://github.com/apache/kafka/blob/trunk/clients/src/test/java/org/apache/kafka/clients/admin/KafkaAdminClientTest.java#L2363-L2369

3. **Region including preceding comment line**

Suggestion is "clean" and it includes the preceding comment line

Examples:
* https://github.com/apache/kafka/blob/trunk/clients/src/test/java/org/apache/kafka/clients/admin/KafkaAdminClientTest.java#L2347-L2351

4. **End line falls short**

Suggestion's end line falls short of the desired region

Examples:
* https://github.com/apache/kafka/blob/trunk/clients/src/test/java/org/apache/kafka/clients/admin/KafkaAdminClientTest.java#L2380-L2386

5. **Region contains the entire function**

Suggested region contains the entire function, including the function definition statement (not just the body of the function).

Examples:
* https://github.com/apache/kafka/blob/trunk/clients/src/test/java/org/apache/kafka/clients/admin/KafkaAdminClientTest.java#L4349-L4485
* https://github.com/apache/kafka/blob/trunk/clients/src/test/java/org/apache/kafka/clients/consumer/KafkaConsumerTest.java#L2614-L2724

6. **End line is too far**

Suggestion's end line goes too far into the "parent" element.

Examples:
* https://github.com/apache/kafka/blob/trunk/clients/src/test/java/org/apache/kafka/clients/admin/KafkaAdminClientTest.java#L4467-L4484
* https://github.com/apache/kafka/blob/trunk/clients/src/test/java/org/apache/kafka/clients/consumer/internals/FetcherTest.java#L2922-L2937
* https://github.com/apache/kafka/blob/trunk/clients/src/test/java/org/apache/kafka/common/message/MessageTest.java#L830-L832


7. **Region contains entire function and end line is too far**

Suggested region contains the entire function, including the function definition, and also the end line goes too far
into the "parent" element.

Examples:
* https://github.com/apache/kafka/blob/trunk/clients/src/test/java/org/apache/kafka/clients/consumer/internals/FetcherTest.java#L2887-L2903

8. **Multiple lines, but one statement**
* https://github.com/apache/kafka/blob/trunk/clients/src/test/java/org/apache/kafka/clients/consumer/KafkaConsumerTest.java#L2685-L2692
* https://github.com/apache/kafka/blob/trunk/clients/src/test/java/org/apache/kafka/clients/consumer/KafkaConsumerTest.java#L2693-L2701
* https://github.com/apache/kafka/blob/trunk/clients/src/test/java/org/apache/kafka/common/message/MessageTest.java#L819-L821

9. **One liner**
* https://github.com/apache/kafka/blob/trunk/clients/src/test/java/org/apache/kafka/clients/consumer/KafkaConsumerTest.java#L2702-L2702

10. **Multiple variables to return**

Examples:
* https://github.com/apache/kafka/blob/trunk/clients/src/test/java/org/apache/kafka/test/Microbenchmarks.java#L36-L41
* https://github.com/apache/kafka/blob/trunk/clients/src/test/java/org/apache/kafka/common/message/MessageTest.java#L772-L791
* https://github.com/apache/kafka/blob/trunk/clients/src/test/java/org/apache/kafka/common/message/MessageTest.java#L793-L812
* https://github.com/apache/kafka/blob/trunk/clients/src/test/java/org/apache/kafka/common/message/MessageTest.java#L835-L890

11. **To be further analyzed why extraction failed**

* https://github.com/apache/kafka/blob/trunk/clients/src/main/java/org/apache/kafka/clients/admin/KafkaAdminClient.java#L3866-L3876
* 