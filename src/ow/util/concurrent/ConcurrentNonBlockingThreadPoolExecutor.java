/*
 * Copyright 2009 Kazuyuki Shudo, and contributors.
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

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * A ThreadPoolExecutor creates threads before queueing tasks.
 * Java SE 6 API provides such function. This type of ThreadPoolExecutor can be created
 * with {@link ThreadPoolExecutor#allowCoreThreadTimeOut(boolean) ThreadPoolExecutor#allowCoreThreadTimeOut(boolean))}.
 * Java SE 5 API does not provide and this class is required.
 */
public final class ConcurrentNonBlockingThreadPoolExecutor extends ThreadPoolExecutor {
	private final BlockingQueue<Runnable> taskQueue = new LinkedBlockingQueue<Runnable>();
	private final Object submissionLock = new Object();
	private Thread submittingThread = null;
	private boolean stopped = false;

    public ConcurrentNonBlockingThreadPoolExecutor(int corePoolSize, int maximumPoolSize,
            long keepAliveTime, TimeUnit unit,
            ThreadFactory threadFactory) {
		super(corePoolSize, maximumPoolSize, keepAliveTime, unit, new SynchronousQueue<Runnable>(), threadFactory);

		Runnable r = new Runnable() {
			public void run() {
				while (true) {
					Runnable task = null;;
					if (stopped) break;

					synchronized (taskQueue) {
						while (true) {
							if ((task = taskQueue.poll()) != null) break;

							try {
								taskQueue.wait();
							}
							catch (InterruptedException e) { /*ignore*/ }
						}
					}

					synchronized (submissionLock) {
						while (true) {
							try {
								ConcurrentNonBlockingThreadPoolExecutor.super.execute(task);
								break;
							}
							catch (RejectedExecutionException e) {}

							try {
								submissionLock.wait();
							}
							catch (InterruptedException e) { /*ignore*/ }
						}
					}
				}
			}
		};

		synchronized (this) {
			Thread t = new Thread(r);
			t.setName("Task submitting thread in ThreadsFirstThreadPoolExecutor");
			t.setDaemon(true);
			t.start();
			this.submittingThread = t;
		}
	}

	public void execute(Runnable command) {
		synchronized (this.taskQueue) {
			this.taskQueue.offer(command);
			this.taskQueue.notify();
		}
	}

	protected void afterExecute(Runnable r, Throwable t) {
		synchronized (this.submissionLock) {
			this.submissionLock.notify();
		}
	}

    protected synchronized void terminated() {
    	this.stopped = true;
    	if (this.submittingThread != null) {
    		this.submittingThread.interrupt();
    		this.submittingThread = null;
    	}
    }
}
