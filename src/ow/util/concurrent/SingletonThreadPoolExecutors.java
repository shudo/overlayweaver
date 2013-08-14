/*
 * Copyright 2009-2010,2012 Kazuyuki Shudo, and contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ow.util.concurrent;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Factory and utility methods for {@link ExecutorService ExecutorService}.
 * Provided methods return an {@link ExecutorService ExecutorService} set up
 * with commonly useful configuration settings. 
 */
public final class SingletonThreadPoolExecutors {
	public final static int NUM_THREADS_FOR_POOL = 32;
	public final static String POOLED_THREAD_NAME = "A pooled thread";
	public final static long KEEP_ALIVE_TIME = 3L;	// second

	private final static ThreadGroup threadGroup;
	private static ExecutorService concurrentBlockingNonDaemonEx;
	private static ExecutorService concurrentBlockingDaemonEx;
	private static ExecutorService concurrentNonBlockingNonDaemonEx;
	private static ExecutorService concurrentNonBlockingDaemonEx;
	private static ExecutorService concurrentRejectingNonDaemonEx;
	private static ExecutorService concurrentRejectingDaemonEx;
//	private static ExecutorService serialBlockingNonDaemonEx;
//	private static ExecutorService serialBlockingDaemonEx;
//	private static ExecutorService serialNonBlockingNonDaemonEx;
//	private static ExecutorService serialNonBlockingDaemonEx;
	// accept unlimited number of threads
	private static ExecutorService unlimitedNonDaemonEx;
	private static ExecutorService unlimitedDaemonEx;

	private static Set<ExecutorService> executorSet;

	static {
		threadGroup = new STPThreadGroup("SingletonThreadPool thread group");

		init();
	}

	public static void init() {
		// concurrent blocking
		concurrentBlockingNonDaemonEx =
				new ConcurrentBlockingThreadPoolExecutor(0, NUM_THREADS_FOR_POOL,
						KEEP_ALIVE_TIME, TimeUnit.SECONDS,
						new SynchronousQueue<Runnable>(),
						new NonDaemonThreadFactory("Pooled thread: concurrent blocking"));

		concurrentBlockingDaemonEx =
			new ConcurrentBlockingThreadPoolExecutor(0, NUM_THREADS_FOR_POOL,
					KEEP_ALIVE_TIME, TimeUnit.SECONDS,
					new SynchronousQueue<Runnable>(),
					new DaemonThreadFactory("Pooled thread: concurrent blocking"));

		// concurrent non-blocking
		concurrentNonBlockingNonDaemonEx =
			new ConcurrentNonBlockingThreadPoolExecutor(0, NUM_THREADS_FOR_POOL,
					KEEP_ALIVE_TIME, TimeUnit.SECONDS,
					new NonDaemonThreadFactory("Pooled thread: concurrent non-blocking"));

		concurrentNonBlockingDaemonEx =
				new ConcurrentNonBlockingThreadPoolExecutor(0, NUM_THREADS_FOR_POOL,
						KEEP_ALIVE_TIME, TimeUnit.SECONDS,
						new DaemonThreadFactory("Pooled thread: concurrent non-blocking"));

		// concurrent rejecting
		concurrentRejectingNonDaemonEx =
				new ThreadPoolExecutor(0, NUM_THREADS_FOR_POOL,
					KEEP_ALIVE_TIME, TimeUnit.SECONDS,
					new SynchronousQueue<Runnable>(),
					new NonDaemonThreadFactory("Pooled thread: concurrent rejecting"));

		concurrentRejectingDaemonEx =
			new ThreadPoolExecutor(0, NUM_THREADS_FOR_POOL,
				KEEP_ALIVE_TIME, TimeUnit.SECONDS,
				new SynchronousQueue<Runnable>(),
				new DaemonThreadFactory("Pooled thread: concurrent rejecting"));

		// serial blocking
//		serialBlockingNonDaemonEx =
//			new ThreadPoolExecutor(0, 1,
//				KEEP_ALIVE_TIME, TimeUnit.SECONDS,
//				new SynchronousQueue<Runnable>(),
//				new NonDaemonThreadFactory());
//
//		serialBlockingDaemonEx =
//			new ThreadPoolExecutor(0, 1,
//					KEEP_ALIVE_TIME, TimeUnit.SECONDS,
//					new SynchronousQueue<Runnable>(),
//					new DaemonThreadFactory());
//
//		// serial non-blocking
//		serialNonBlockingNonDaemonEx =
//			new ThreadPoolExecutor(0, 1,
//					KEEP_ALIVE_TIME, TimeUnit.SECONDS,
//					new LinkedBlockingQueue<Runnable>(),
//					new NonDaemonThreadFactory());
//
//		serialNonBlockingDaemonEx =
//			new ThreadPoolExecutor(0, 1,
//				KEEP_ALIVE_TIME, TimeUnit.SECONDS,
//				new LinkedBlockingQueue<Runnable>(),
//				new DaemonThreadFactory());
//

		// unlimited number of threads
		unlimitedNonDaemonEx =
			new ThreadPoolExecutor(0, Integer.MAX_VALUE,
				KEEP_ALIVE_TIME, TimeUnit.SECONDS,
				new SynchronousQueue<Runnable>(),
				new NonDaemonThreadFactory("Pooled thread: unlimited"));
		unlimitedDaemonEx =
			new ThreadPoolExecutor(0, Integer.MAX_VALUE,
				KEEP_ALIVE_TIME, TimeUnit.SECONDS,
				new SynchronousQueue<Runnable>(),
				new DaemonThreadFactory("Pooled thread: unlimited"));

		executorSet = new HashSet<ExecutorService>();
		executorSet.add(concurrentBlockingNonDaemonEx);
		executorSet.add(concurrentBlockingDaemonEx);
		executorSet.add(concurrentNonBlockingNonDaemonEx);
		executorSet.add(concurrentNonBlockingDaemonEx);
		executorSet.add(concurrentRejectingNonDaemonEx);
		executorSet.add(concurrentRejectingDaemonEx);
//		executorSet.add(serialBlockingNonDaemonEx);
//		executorSet.add(serialBlockingDaemonEx);
//		executorSet.add(serialNonBlockingNonDaemonEx);
//		executorSet.add(serialNonBlockingDaemonEx);
		executorSet.add(unlimitedNonDaemonEx);
		executorSet.add(unlimitedDaemonEx);
	}

	private final static class DaemonThreadFactory implements ThreadFactory {
		private final String threadName;

//		public DaemonThreadFactory() { this.threadName = POOLED_THREAD_NAME; }
		public DaemonThreadFactory(String threadName) { this.threadName = threadName; }

		public Thread newThread(Runnable r) {
			Thread t = new Thread(threadGroup, r);
			t.setName(this.threadName);
			t.setDaemon(true);

			return t;
		}
	}

	private final static class NonDaemonThreadFactory implements ThreadFactory {
		private final String threadName;

//		public NonDaemonThreadFactory() { this.threadName = POOLED_THREAD_NAME; }
		public NonDaemonThreadFactory(String threadName) { this.threadName = threadName; }

		public Thread newThread(Runnable r) {
			Thread t = new Thread(threadGroup, r);
			t.setName(this.threadName);
			t.setDaemon(false);

			return t;
		}
	}

	public static void shutdown() {
		for (ExecutorService ex: executorSet) {
			ex.shutdown();
		}
	}

	public static boolean awaitTermination(long timeout, TimeUnit unit)
			throws InterruptedException {
		long limit = System.currentTimeMillis() + TimeUnit.MILLISECONDS.convert(timeout, unit);

		boolean terminated = true;

		for (ExecutorService ex: executorSet) {
			long timeoutMillis = limit - System.currentTimeMillis();
			if (timeoutMillis < 0L) return false;

			terminated = ex.awaitTermination(timeoutMillis, TimeUnit.MILLISECONDS);
			if (!terminated) return terminated;
		}

		return terminated;
	}

	private final static class STPThreadGroup extends ThreadGroup {
		public STPThreadGroup(String name) {
			super(name);
		}

		public void uncaughtException(Thread t, Throwable e) {
			if (!(e instanceof ThreadDeath)) {
				System.err.print("Exception in thread " + t.getName() + ": ");
				e.printStackTrace(System.err);
			}

			if (e instanceof VirtualMachineError) {
				System.err.println("A VirtualMachineError thrown and exit.");
				System.exit(1);
			}
		}
	}

	public static ExecutorService getThreadPool(
			ExecutorBlockingMode blockingMode, boolean daemon) {
		ExecutorService ex = null;

		switch (blockingMode) {
		case CONCURRENT_BLOCKING:
			if (daemon)
				ex = concurrentBlockingDaemonEx;
			else
				ex = concurrentBlockingNonDaemonEx;
			break;
		case CONCURRENT_NON_BLOCKING:
			if (daemon)
				ex = concurrentNonBlockingDaemonEx;
			else
				ex = concurrentNonBlockingNonDaemonEx;
			break;
		case CONCURRENT_REJECTING:
			if (daemon)
				ex = concurrentRejectingDaemonEx;
			else
				ex = concurrentRejectingNonDaemonEx;
			break;
//		case SERIAL_BLOCKING:
//			if (!daemon)
//				ex = serialBlockingNonDaemonEx;
//			else
//				ex = serialBlockingDaemonEx;
//			break;
//		case SERIAL_NON_BLOCKING:
//			if (!daemon)
//				ex = serialNonBlockingNonDaemonEx;
//			else
//				ex = serialNonBlockingDaemonEx;
//			break;
		case UNLIMITED:
			if (!daemon)
				ex = unlimitedNonDaemonEx;
			else
				ex = unlimitedDaemonEx;
			break;
		}

		return ex;
	}
}
