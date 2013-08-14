/*
 * Copyright 2007-2009,2012 Kazuyuki Shudo, and contributors.
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

package ow.dht;

import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.Random;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;

import ow.id.ID;
import ow.messaging.Signature;
import ow.routing.RoutingException;
import ow.tool.emulator.EmulatorControllable;
import ow.tool.util.toolframework.AbstractDHTBasedTool;

/**
 * A test and benchmarking tool for a DHT instance locally.
 * It is assumed to be invoked from a scenario DHTBenchmarkScenario.
 */
public class DHTBenchmark extends AbstractDHTBasedTool<String> {
	private final static String COMMAND = "java ow.dht.DHTBenchmark";

	public final static int NUM_VALUES = 1000;
	public final static int TTL = 5 * 60 * 1000;	// 5 min
	public final static String ENCODING = "UTF-8";
	private final static String KEY_PREFIX = "key";
	private final static String VALUE_PREFIX = "value";

	protected void usage(String command) {
		super.usage(command, null); 
	}

	public static void main(String[] args) {
		(new DHTBenchmark()).start(args);
	}

	protected void start(String[] args) {
		this.invoke(args, System.out);
	}

	/**
	 * Implements {@link EmulatorControllable#invoke(String[], PrintStream)
	 * EmulatorControllableApplication#start}.
	 */
	public Writer invoke(String[] args, PrintStream out) {
		int repeat = 0;

		// parse command-line arguments
		Options opts = this.getInitialOptions();
		opts.addOption("b", "benchmark", true, "do benchmark");

		CommandLineParser parser = new PosixParser();
		CommandLine cmd = null;
		try {
			cmd = parser.parse(opts, args);
		}
		catch (ParseException e) {
			System.out.println("There is an invalid option.");
			e.printStackTrace();
			System.exit(1);
		}

		parser = null;
		opts = null;

		String optVal;
		optVal = cmd.getOptionValue('b');
		if (optVal != null) {
			repeat = Integer.parseInt(optVal);
		}

		// parse remaining arguments
		// and initialize DHT
		DHT<String> dht = null;
		try {
			dht = super.initialize(Signature.APPLICATION_ID_DHT_SHELL, (short)0x10000,
					DHTFactory.getDefaultConfiguration(),
					COMMAND, cmd);
		}
		catch (Exception e) {
			System.err.println("An Exception thrown:");
			e.printStackTrace();
			return null;
		}

		cmd = null;

		if (repeat <= 0) {
			try {
				Thread.sleep(Long.MAX_VALUE);
			}
			catch (InterruptedException e) { /* ignore */ }

			return null;
		}

		// benchmark
		out.println("preparation: putting " + NUM_VALUES + " values.");

		dht.setTTLForPut(TTL);

		int idSize = dht.getRoutingAlgorithmConfiguration().getIDSizeInByte();
		for (int i = 0; i < NUM_VALUES; i++) {
			String key = KEY_PREFIX + i;
			String value = VALUE_PREFIX + i;

			ID id = null;
			try {
				id = ID.getID(key.getBytes(ENCODING), idSize);
			}
			catch (UnsupportedEncodingException e) { /* NOTREACHED */ }

			try {
				dht.put(id, value);
			}
			catch (Exception e) {
				out.println("put failed: " + key);
			}
		}

		out.println("benchmark: getting " + repeat + " times.");

		Random rnd = new Random();
		int numSuccess = 0;

		long time = System.currentTimeMillis();

		for (int i = 0; i < repeat; i++) {
			String key = KEY_PREFIX + rnd.nextInt(NUM_VALUES);

			ID id = null;
			try {
				id = ID.getID(key.getBytes(ENCODING), idSize);
			}
			catch (UnsupportedEncodingException e) { /* NOTREACHED */ }

			try {
				Set<ValueInfo<String>> valueSet = dht.get(id);
				if (valueSet != null) {
					numSuccess++;
				}
			}
			catch (RoutingException e) { /* ignore */ }
		}

		time = System.currentTimeMillis() - time;

		System.out.println("time (msec): " + time);
		System.out.println("num of successful get: " + numSuccess + " / " + repeat);

		return null;
	}
}
