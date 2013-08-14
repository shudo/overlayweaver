/*
 * Copyright 2007-2009 Kazuyuki Shudo, and contributors.
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

public class TimerTest {
	public final static void main(String[] args) {
		Timer timer = Timer.getSingletonTimer();
//			new Timer("Timer thread");

		Runnable task0 = new Runnable() {
			public void run() {
				System.out.println("Hello!");
			}
		};

		Runnable task1 = new Runnable() {
			public void run() {
				System.out.println("Bojour!");
			}
		};

		long curTime = Timer.currentTimeMillis();
		timer.schedule(task0, curTime + 5000L, false);
		timer.schedule(task0, curTime + 6000L, false);
		timer.schedule(task0, curTime + 7000L, false);
		timer.schedule(task0, curTime + 8000L, true);

		try {
			Thread.sleep(1000L);
		}
		catch (InterruptedException e) { /*ignore*/ }

		timer.schedule(task1, curTime + 1000L, false);

		try {
			Thread.sleep(30000L);
		}
		catch (InterruptedException e) { /*ignore*/ }
	}
}
