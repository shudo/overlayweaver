/*
 * Copyright 2006-2010 National Institute of Advanced Industrial Science
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

package ow.messaging.emulator;

import ow.messaging.MessagingConfiguration;

public class EmuMessagingConfiguration extends MessagingConfiguration {
	public final static int DEFAULT_INITIAL_ID = 0;		// initial host name is emu0
	public final static boolean DEFAULT_USE_TIMER_FOR_TIMEOUT = false;
	public final static int DEFAULT_ADDITIONAL_LATENCY_MICROS = 0;	// usec
	public final static double DEFAULT_COMMUNICATION_FAILURE_RATE = 0.0;

	// overrides corresponding fields of MessagingConfiguratoin
	public final static boolean DEFAULT_DO_TIMEOUT_CALCULATION = false;
	public final static int DEFAULT_STATIC_TIMEOUT = 500;	// msec

	private int initialID = DEFAULT_INITIAL_ID;
	public final int getInitialID() { return this.initialID; }
	public final int setInitialID(int id) {
		int old = this.initialID;
		this.initialID = id;
		return old;
	}

	private boolean useTimerForTimeout = DEFAULT_USE_TIMER_FOR_TIMEOUT;
	public final boolean getUseTimerForTimeout() { return this.useTimerForTimeout; }
	public final boolean setUseTimerForTimeout(boolean use) {
		boolean old = this.useTimerForTimeout;
		this.useTimerForTimeout = use;
		return old;
	}

	private int additionalLatencyMicros = DEFAULT_ADDITIONAL_LATENCY_MICROS;
	public final int getAdditionalLatencyMicros() { return this.additionalLatencyMicros; }
	public final int setAdditionalLatencyMicros(int t) {
		int old = this.additionalLatencyMicros;
		this.additionalLatencyMicros = t;
		return old;
	}

	private double communicationFailureRate = DEFAULT_COMMUNICATION_FAILURE_RATE;
	public final double getCommunicationFailureRate() { return this.communicationFailureRate; }
	public final double setCommunicationFailureRate(double r) {
		double old = this.communicationFailureRate;
		this.communicationFailureRate = r;
		return old;
	}

	//
	// overrides corresponding methods of MessagingConfiguration
	//
	private boolean doTimeoutCalculation = DEFAULT_DO_TIMEOUT_CALCULATION;
	public final boolean getDoTimeoutCalculation() { return this.doTimeoutCalculation; }
	public final boolean setDoTimeoutCalculation(boolean flag) {
		boolean old = this.doTimeoutCalculation;
		this.doTimeoutCalculation = flag;
		return old;
	}

	//
	// overrides corresponding methods of MessagingConfiguration
	//
	private int staticTimeout = DEFAULT_STATIC_TIMEOUT;
	public int getStaticTimeout() { return this.staticTimeout; }
	public int setStaticTimeout(int timeout) {
		int old = this.staticTimeout;
		this.staticTimeout = timeout;
		return old;
	}
}
