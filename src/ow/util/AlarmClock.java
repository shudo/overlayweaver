/*
 * Copyright 2006-2007,2009 National Institute of Advanced Industrial Science
 * and Technology (AIST), and contributors.
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

import java.nio.channels.ClosedByInterruptException;
import java.util.HashMap;
import java.util.Map;
import java.util.TimerTask;

/**
 * A timer interrupts the calling thread after the specified period.
 */
public final class AlarmClock extends TimerTask {
	private static Timer timer = Timer.getSingletonTimer();
//		new Timer("Alarm Timer");
	private static Map<Thread,TimerTask> timerTable = new HashMap<Thread,TimerTask>();

	private Thread target;
	private boolean oneShot;

	private AlarmClock(Thread target, boolean oneShot) {
		this.target = target;
		this.oneShot = oneShot;
	}

	public static void setAlarm(long timeout) {
		setAlarm(timeout, 0);
	}

	public static void setAlarm(long timeout, long interval) {
		boolean oneShot = (interval <= 0);

		Thread curThread = Thread.currentThread();
		TimerTask task = new AlarmClock(curThread, oneShot);

		synchronized (AlarmClock.timerTable) {
			AlarmClock.timerTable.put(curThread, task);
		}

		long absoluteTimeout = Timer.currentTimeMillis() + timeout;	// for ow.util.Timer
		if (oneShot) {
			timer.schedule(task, absoluteTimeout, true /*isDaemon*/);
		}
		else {
			timer.scheduleAtFixedRate(task, absoluteTimeout, interval, true /*isDaemon*/);
		}
	}

	public static void clearAlarm() throws InterruptedException, ClosedByInterruptException {
		TimerTask task = null;
		synchronized (AlarmClock.timerTable) {
			task = AlarmClock.timerTable.remove(Thread.currentThread());
		}

		if (task != null) {
			AlarmClock.timer.cancel(task);	// for ow.util.Timer
			//task.cancel();	// for java.util.Timer
		}

		if (Thread.interrupted()) {
			// current thread has been interrupted
			throw new InterruptedException("Timer#clearTimer() detected current thread has been interrupted.");
		}
	}

	public void run() {
		this.target.interrupt();

		synchronized (AlarmClock.timerTable) {
			if (this.oneShot) {
				AlarmClock.timerTable.remove(this.target);
			}
		}
	}
}
