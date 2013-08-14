/*
 * Copyright 2008 Kazuyuki Shudo, and contributors.
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

package ow.messaging.emulator;

public class SleepPeriodMeasure {
	private long error = 0L;
	private int times = 0;

	public void sleep(long millis, int nanos) throws InterruptedException {
		long t = -System.nanoTime();

		Thread.sleep(millis, nanos);

		t += System.nanoTime();

		synchronized (this) {
			this.error += t;
			this.times++;
		}
	}

	public double getAverageOfErrorNanos() {
		return ((double)error) / times;
	}

	protected void finalize() throws Throwable {
		System.out.println("Average of error: "
				+ (this.getAverageOfErrorNanos() / 1000000.0) + " msec");
		System.out.flush();
	}
}
