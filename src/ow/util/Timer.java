/*
 * Copyright 2007-2010 Kazuyuki Shudo, and contributors.
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

package ow.util;

import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

import ow.util.concurrent.ExecutorBlockingMode;
import ow.util.concurrent.SingletonThreadPoolExecutors;

/**
 * An alternative to {@link java.util.Timer Timer} class.
 */
public final class Timer {
	final static Logger logger = Logger.getLogger("util");

	//
	// parameters
	//

	// thread pool
	public final static boolean USE_THREAD_POOL = true;

	// JVM lasts for 5000 msec though all non-daemon threads finished
	public final static long JVM_LASTING_TIME = 5000L;

	// adaption to scheduling delay
	public final static boolean ADAPT_TIMER_TO_SCHEDULING_DELAY = true;
	public final static long ALLOWED_DELAY_TIME = 1000L;
	public final static long ADDITIONAL_WAIT = 0L;	// 0 msec

	// real time or event-driven
	public volatile boolean eventDrivenMode = false;

	private SortedSet<ScheduledTask> taskSet;
	private Map<Runnable,ScheduledTask> taskTable;
	private int numNonDaemonTask;

	private TimerRunner timerRunner;
	private Thread timerThread = null;
	private String timerThreadName;
	private int timerThreadPriority;

	private final JVMLifeKeeper jvmLifeKeeper;

	private long expeditedTime = 0L;

	private static Timer singletonTimer = null;

	public static Timer getSingletonTimer() {
		synchronized (Timer.class) {
			if (singletonTimer == null) {
				singletonTimer = new Timer("Singleton Timer", Thread.NORM_PRIORITY);
			}
		}

		return singletonTimer;
	}

//	public Timer() {
//		this("Timer thread");
//	}
//
//	public Timer(String threadName) {
//		this(threadName, Thread.currentThread().getPriority());
//	}

	private Timer(String threadName, int threadPriority) {
		this.timerThreadName = threadName;
		this.timerThreadPriority = threadPriority;

		if (this.timerThreadPriority > Thread.MAX_PRIORITY) this.timerThreadPriority = Thread.MAX_PRIORITY;
		else if (this.timerThreadPriority < Thread.MIN_PRIORITY) this.timerThreadPriority = Thread.MIN_PRIORITY;

		// initialize
		this.taskSet = new TreeSet<ScheduledTask>();
		this.taskTable = new HashMap<Runnable,ScheduledTask>();
		this.numNonDaemonTask = 0;

		this.timerRunner = new TimerRunner();

		this.jvmLifeKeeper = new JVMLifeKeeper(JVM_LASTING_TIME);
	}

	public boolean setEventDrivenMode(boolean mode) {
		boolean old = this.eventDrivenMode;
		this.eventDrivenMode = mode;
		return old;
	}

	private synchronized void ensureTimerThreadRunning() {
		// instantiate a thread
		if (this.timerThread == null) {
			Thread t = this.timerThread = new Thread(this.timerRunner);
			t.setName(this.timerThreadName);
			t.setDaemon(true);
			try {
				t.setPriority(this.timerThreadPriority);
			}
			catch (Exception e) {
				logger.log(Level.WARNING, "Could not set thread priority: " + this.timerThreadPriority, e);
			}

			t.start();
		}
	}

	/**
	 * Schedules the specified task for execution at the specified (absolute) time.
	 */
	public void schedule(Runnable r, long absoluteTime) {
		this.schedule(r, absoluteTime, false, false);
	}

	public void schedule(Runnable r, long absoluteTime, boolean isDaemon) {
		this.schedule(r, absoluteTime, isDaemon, false);
	}

	public void schedule(Runnable r, long absoluteTime, boolean isDaemon, boolean executeConcurrently) {
		ScheduledTask task = new ScheduledTask(r, absoluteTime, 0L, isDaemon, executeConcurrently);

		synchronized (this.taskSet) {
			this.taskSet.add(task);
			this.taskTable.put(r, task);

			if (!isDaemon) {
				this.numNonDaemonTask++;
				this.jvmLifeKeeper.keep(true);
			}

			this.taskSet.notify();
		}

		this.ensureTimerThreadRunning();
	}

	/**
	 * Schedules the specified task for repeated execution, beginning at the specified (absolute) time.
	 */
	public void scheduleAtFixedRate(Runnable r, long absoluteTime, long interval) {
		this.scheduleAtFixedRate(r, absoluteTime, interval, false, false);
	}

	public void scheduleAtFixedRate(Runnable r, long absoluteTime, long interval, boolean isDaemon) {
		this.scheduleAtFixedRate(r, absoluteTime, interval, isDaemon, false);
	}

	public void scheduleAtFixedRate(Runnable r, long absoluteTime, long interval, boolean isDaemon, boolean executeConcurrently) {
		ScheduledTask task = new ScheduledTask(r, absoluteTime, interval, isDaemon, executeConcurrently);

		synchronized (this.taskSet) {
			this.taskSet.add(task);
			this.taskTable.put(r, task);

			if (!isDaemon) {
				this.numNonDaemonTask++;
				this.jvmLifeKeeper.keep(true);
			}

			this.taskSet.notify();
		}

		this.ensureTimerThreadRunning();
	}

	/**
	 * Cancels the specified {@link Runnable Runnable} instance.
	 */
	public boolean cancel(Runnable r) {
		boolean scheduled = false;

		synchronized (this.taskSet) {
			ScheduledTask task = this.taskTable.get(r);
			if (task != null) {
				this.taskSet.remove(task);
				this.taskTable.remove(task.getTask());

				if (!task.isDaemon) {
					this.numNonDaemonTask--;
					if (this.numNonDaemonTask <= 0) this.jvmLifeKeeper.keep(false);
				}

				scheduled = true;
			}

			this.taskSet.notify();
		}

		return scheduled;
	}

	/*
	 * Returns (absolute) scheduled time of the specified {@link Runnable Runnable} instance.
	 */
	public long getScheduledTime(Runnable r) {
		ScheduledTask task = this.taskTable.get(r);

		if (task != null)
			return task.getScheduledTime();
		else
			return -1L;
	}

	public void stop() {
		synchronized (this) {
			if (this.timerRunner != null) {
				this.timerRunner.stopped = true;
			}

			if (this.timerThread != null) {
				this.timerThread.interrupt();
				this.timerThread = null;
			}
		}

		synchronized (Timer.class) {
			if (this == singletonTimer) {
				singletonTimer = null;
			}
		}
	}

	//
	// Time-related alternative methods
	//

	public static long currentTimeMillis() {
		long t = System.currentTimeMillis();

		if (singletonTimer != null) {
			t = t + singletonTimer.expeditedTime;
		}

		return t;
	}

	// task representation
	private final class ScheduledTask implements Comparable<ScheduledTask> {
		private final Runnable task;
		private final long time;
		private final long interval;
		private final boolean isDaemon;
		private final boolean executedConcurrently;

		private ScheduledTask(Runnable task, long absoluteTime,
				boolean isDaemon, boolean executedConcurrently) {
			this(task, absoluteTime, 0L, isDaemon, executedConcurrently);
		}

		private ScheduledTask(Runnable task, long absoluteTime, long interval,
				boolean isDaemon, boolean executedConcurrently) {
			this.task = task;
			this.time = absoluteTime;
			this.interval = interval;
			this.isDaemon = isDaemon;
			this.executedConcurrently = executedConcurrently;
		}

		// accessors for time and interval
		private Runnable getTask() { return this.task; }
		private long getScheduledTime() { return this.time; }
		private long getInterval() { return this.interval; }
		private boolean isDaemon() { return this.isDaemon; }
		private boolean executedConcurrently() { return this.executedConcurrently; }

		// implements Comparable
		public int compareTo(ScheduledTask o) {
			int order = Long.signum(this.time - o.time);

			if (order != 0) return order;

			order = System.identityHashCode(o) - System.identityHashCode(this); 
				// 0 in case that `this' and `o' are the same ScheduledTask instance.

			return order;
		}
	}

	private class TimerRunner implements Runnable {
		private volatile boolean stopped = false;

		public void run() {
			boolean delayed = false;

			outerLoop:
			while (true) {
				ScheduledTask currentTask;

				// obtain a task
				// Note that this loop is required to support insertion of a task into
				while (true) {
					currentTask = null;

					synchronized (Timer.this.taskSet) {
						synchronized (Timer.this) {
							if (Timer.this.taskSet.isEmpty()) {
								// finish in case of empty
								Timer.this.timerThread = null;
								break outerLoop;
							}

							currentTask = Timer.this.taskSet.first();
						}
					}

					// sleep
					long sleepPeriod = currentTask.getScheduledTime() - Timer.currentTimeMillis();
/*
String cname = currentTask.getTask().getClass().getName();
cname = cname.substring(cname.lastIndexOf('.') + 1);
System.out.println("(sleep " + sleepPeriod + " ms: " + cname + " @ " + Integer.toHexString(System.identityHashCode(currentTask)) + ")");
System.out.flush();
*/
					if (Timer.this.eventDrivenMode) {
						Timer.this.expeditedTime += sleepPeriod;
						break;
					}
					else {
						long delayedTime;

						if (sleepPeriod > 0L) {
							try {
								synchronized (Timer.this.taskSet) {
									Timer.this.taskSet.wait(sleepPeriod);
								}
							}
							catch (InterruptedException e) { /*ignore*/ }

							delayedTime = Timer.currentTimeMillis() - currentTask.getScheduledTime();
						}
						else {
							delayedTime = -sleepPeriod;
						}

						// check if task scheduling was delayed
						if (delayedTime > ALLOWED_DELAY_TIME) {
							// task scheduled with delay
							if (ADAPT_TIMER_TO_SCHEDULING_DELAY) {
								delayedTime += ADDITIONAL_WAIT;

								//synchronized (Timer.this) {	// not required because there is only one Timer thread
									Timer.this.expeditedTime -= delayedTime;
								//}
							}

							if (ADAPT_TIMER_TO_SCHEDULING_DELAY || !delayed) {
								delayed = true;

								System.out.println("[Task sch'ed w/ delay, " + delayedTime + " msec: "
										+ currentTask.getTask().getClass()
										+ " @ " + Integer.toHexString(System.identityHashCode(currentTask)));
								System.out.flush();

							}
						}
						else {
							if (!ADAPT_TIMER_TO_SCHEDULING_DELAY && delayed) {
								delayed = false;

								System.out.println("[Task sch'ed on time: "
										+ currentTask.getTask().getClass()
										+ " @ " + Integer.toHexString(System.identityHashCode(currentTask)));
								System.out.flush();
							}
						}

						if (delayedTime >= 0) break;
					}	// if (Timer.this.eventDrivenMode)

					// check whether being stopped
					if (this.stopped) {
						this.stopped = false;	// reset
						break outerLoop;
					}
				} // while (true)

				// remove current task from the queue
				synchronized (Timer.this.taskSet) {
					Timer.this.taskSet.remove(currentTask);
					Timer.this.taskTable.remove(currentTask.getTask());

					// Note: update the state of JVM life keeper later
				}

				// execute
				Runnable r = currentTask.getTask();

				if (!Timer.this.eventDrivenMode
						&& USE_THREAD_POOL && currentTask.executedConcurrently()) {
					ExecutorService ex = SingletonThreadPoolExecutors.getThreadPool(
							ExecutorBlockingMode.CONCURRENT_BLOCKING, currentTask.isDaemon());
					ex.submit(r);
				}
				else {
					try {
						r.run();
					}
					catch (Throwable e) {
						logger.log(Level.WARNING, "A task threw an exception: " + e, e);
					}
				}

				// re-submit a periodic task
				long interval = currentTask.getInterval();
				if (interval > 0L) {
					Timer.this.scheduleAtFixedRate(r,
							currentTask.getScheduledTime() + interval, interval,
							currentTask.isDaemon(), currentTask.executedConcurrently());
				}

				r = null;

				// update the state of JVM life keeper
				if (!currentTask.isDaemon) {
					synchronized (Timer.this.taskSet) {
						Timer.this.numNonDaemonTask--;
						if (Timer.this.numNonDaemonTask <= 0) {
							Timer.this.jvmLifeKeeper.keep(false);
						}
					}
				}
			}	// outerLoop: while (true)
		}
	}
}
