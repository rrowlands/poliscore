#!/usr/bin/env bash
echo "Running vertx.silencer colored‐segment test…"

# ANSI color codes
CSI=$'\033['
YELLOW="${CSI}33m"
BLUE="${CSI}34m"
GREEN="${CSI}32m"
RED="${CSI}31m"
RESET="${CSI}0m"

# What we expect after filtering:
expected="2025-04-26 14:27:32,000 INFO  [us.pol.ent.Main] (main) Done"

actual=$(cat <<EOF | ./vertx.silencer
${YELLOW}2025-04-26 14:27:31,946 WARN${RESET}  [${BLUE}io.ver.cor.imp.BlockedThreadChecker${RESET}] (${GREEN}vertx-blocked-thread-checker) Thread Thread[vert.x-worker-thread-1,5,build group] has been blocked for 5263223 ms, time limit is 3600000 ms${RESET}
${RED}	at java.base/jdk.internal.misc.Unsafe.park(Native Method)
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
	at java.base/java.lang.Thread.run(Thread.java:1583)${RESET}

2025-04-26 14:27:32,000 INFO  [us.pol.ent.Main] (main) Done
EOF
)

if [[ "$actual" == "$expected" ]]; then
  echo "✅ Colored‐segment test PASSED"
  exit 0
else
  echo "❌ Colored‐segment test FAILED"
  echo
  echo "Expected:"
  echo "$expected"
  echo
  echo "Got:"
  echo "$actual"
  exit 1
fi

