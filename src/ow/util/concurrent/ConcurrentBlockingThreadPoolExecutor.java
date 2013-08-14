/*
 * Copyright 2010 Kazuyuki Shudo, and contributors.
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
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * A ThreadPoolExecutor on which a task submitting thread is blocked if no thread available in the pool
 */
public final class ConcurrentBlockingThreadPoolExecutor extends ThreadPoolExecutor {
	private final Object submissionLock = new Object();

    public ConcurrentBlockingThreadPoolExecutor(int corePoolSize, int maximumPoolSize,
            long keepAliveTime, TimeUnit unit,
            ThreadFactory threadFactory) {
		super(corePoolSize, maximumPoolSize, keepAliveTime, unit, new SynchronousQueue<Runnable>(), threadFactory);
	}

    protected ConcurrentBlockingThreadPoolExecutor(int corePoolSize, int maximumPoolSize,
            long keepAliveTime, TimeUnit unit,
            BlockingQueue<Runnable> queue,
            ThreadFactory threadFactory) {
		super(corePoolSize, maximumPoolSize, keepAliveTime, unit, queue, threadFactory);
	}

//	private int numTasksBeingExecuted = 0;
//	public synchronized int getNumTasksBeingExecuted() { return this.numTasksBeingExecuted; }

	protected void afterExecute(Runnable r, Throwable t) {
		synchronized (this.submissionLock) {
//			this.numTasksBeingExecuted--;
			this.submissionLock.notify();
		}
	}

	public void execute(Runnable command) {
		synchronized (this.submissionLock) {
//			this.numTasksBeingExecuted++;

			while (true) {
				try {
					super.execute(command);
					break;
				}
				catch (RejectedExecutionException e) {
					try {
						this.submissionLock.wait();
					}
					catch (InterruptedException e1) { /*ignore*/ }
				}
			}
		}
	}
}
