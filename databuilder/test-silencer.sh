#!/bin/bash

echo "Running vertx.silencer test..."

# Simulated mixed log output
cat <<EOF | ./vertx.silencer
2025-04-21 11:41:20,000 INFO  [us.pol.ent.DatabaseBuilder] (Quarkus Main Thread) Normal startup.

2025-04-21 11:41:25,178 WARN  [io.ver.cor.imp.BlockedThreadChecker] (vertx-blocked-thread-checker) Thread Thread[vert.x-worker-thread-1,5,build group] has been blocked for 3642267 ms, time limit is 3600000 ms: io.vertx.core.VertxException: Thread blocked
	at java.base/jdk.internal.misc.Unsafe.park(Native Method)
	at java.base/java.util.concurrent.locks.LockSupport.parkNanos(LockSupport.java:269)
	at java.base/java.util.concurrent.locks.AbstractQueuedSynchronizer\$ConditionObject.awaitNanos(AbstractQueuedSynchronizer.java:1758)
	at java.base/java.util.concurrent.LinkedBlockingQueue.poll(LinkedBlockingQueue.java:460)
	at io.quarkus.amazon.lambda.runtime.MockEventServer\$1.call(MockEventServer.java:145)
	at io.quarkus.amazon.lambda.runtime.MockEventServer\$1.call(MockEventServer.java:134)
	at io.vertx.core.impl.ContextImpl.lambda\$executeBlocking\$0(ContextImpl.java:178)
	at io.vertx.core.impl.ContextInternal.dispatch(ContextInternal.java:279)
	at io.vertx.core.impl.ContextImpl.lambda\$internalExecuteBlocking\$2(ContextImpl.java:210)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1144)
	at java.base/java.util.concurrent.ThreadPoolExecutor\$Worker.run(ThreadPoolExecutor.java:642)
	at io.netty.util.concurrent.FastThreadLocalRunnable.run(FastThreadLocalRunnable.java:30)
	at java.base/java.lang.Thread.run(Thread.java:1583)

2025-04-21 11:41:30,000 INFO  [us.pol.ent.DatabaseBuilder] (Quarkus Main Thread) Build complete.
EOF

