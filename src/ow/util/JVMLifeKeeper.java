/*
 * Copyright 2009-2010 Kazuyuki Shudo, and contributors.
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

/**
 * An instance of this class keeps the JVM alive
 * if {@link JVMLifeKeeper.keep() keep(true)} method called.
 */
public final class JVMLifeKeeper {
	private final long lastingTime;
	private Thread lifeKeeper = null;
	private Thread terminator = null;

	public JVMLifeKeeper() { this(0L); }
	public JVMLifeKeeper(long lastingTime) { this.lastingTime = lastingTime; }

	public synchronized void keep(boolean keep) {
		if (keep) {
//System.out.println("JVMLifeKeeper: on");
			if (this.lifeKeeper == null) {
				Runnable r = new Runnable() {
					public void run() {
						try { Thread.sleep(Long.MAX_VALUE); } catch (InterruptedException e) {}
					}
				};

				Thread t = this.lifeKeeper = new Thread(r);	// a non-daemon thread, which keeps JVM alive
				t.setName("JVMLifeKeeper");
				t.start();
			}
			else if (this.terminator != null) {
				this.terminator.interrupt();
				this.terminator = null;
			}
		}
		else {
//System.out.println("JVMLifeKeeper: off");
			if (this.lifeKeeper != null) {
				if (this.lastingTime == 0L) {
					this.terminateLifeKeeper();
				}
				else if (this.terminator == null){
					Runnable r = new Terminator();
					Thread t = this.terminator = new Thread(r);
					t.setName("JVMLifeKeeper terminator");
					t.start();
				}
			}
		}
	}

	private synchronized void terminateLifeKeeper() {
//System.out.println("JVMLifeKeeper: terminate");
		if (this.lifeKeeper != null) {
			this.lifeKeeper.interrupt();
			this.lifeKeeper = null;
		}

		if (this.terminator != null) {
			this.terminator.interrupt();
			this.terminator = null;
		}
	}

	private final class Terminator implements Runnable {
		public void run() {
//System.out.println("teminator started.");
			try {
				Thread.sleep(JVMLifeKeeper.this.lastingTime);

				if (!Thread.interrupted()) {
					JVMLifeKeeper.this.terminateLifeKeeper();
				}
			}
			catch (InterruptedException e) {
//System.out.println("teminator interrupted.");
			}
		}
	}

	/**
	 * Test method.
	 */
	public static void main(String[] args) throws InterruptedException {
		JVMLifeKeeper sw = new JVMLifeKeeper(2000L);

		sw.keep(true);
		Thread.sleep(1000L);
		sw.keep(false);
		Thread.sleep(1000L);
		sw.keep(true);
		Thread.sleep(1000L);
		sw.keep(false);
	}
}
