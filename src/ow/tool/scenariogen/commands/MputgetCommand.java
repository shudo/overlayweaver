/*
 * Copyright 2008-2009 Kazuyuki Shudo, and contributors.
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

package ow.tool.scenariogen.commands;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.LinkedHashSet;
import java.util.Random;
import java.util.Set;

import ow.routing.RoutingAlgorithm;
import ow.tool.scenariogen.Main;
import ow.tool.scenariogen.ScenarioGeneratorContext;
import ow.tool.util.shellframework.Command;
import ow.tool.util.shellframework.ShellContext;

public final class MputgetCommand implements Command<ScenarioGeneratorContext> {
	private final static String[] NAMES = {"mputget"};

	public String[] getNames() { return NAMES; }

	public String getHelp() {
		return "mputget <start time (ms)> <interval per a req (ms)> <# of put/gets in a req> <# of req> {raw,{{clustered,node} <algorithm name> <size of id (byte)>}}";
		// e.g. mputget 0 10 10 100 raw
		// e.g. mputget 0 10 10 100 clustered Kademlia 20
		// e.g. mputget 0 10 10 100 node Kademlia 20
	}

	private enum Style { RAW, CLUSTERED, NODE };

	public boolean execute(ShellContext<ScenarioGeneratorContext> context) {
		ScenarioGeneratorContext cxt = context.getOpaqueData();
		PrintStream out = context.getOutputStream();
		String[] args = context.getArguments();

		// parse arguments
		if (args.length < 5) {
			out.println("Usage: " + this.getHelp());
			return false;
		}

		Style style;
		if (args[4].startsWith("c")) style = Style.CLUSTERED;
		else if (args[4].startsWith("n")) style = Style.NODE;
		else style = Style.RAW;

		if (style != Style.RAW && args.length < 7) {
			out.println("Usage: " + this.getHelp());
			return false;
		}

		long startTime = (long)Double.parseDouble(args[0]);
		long interval = (long)Double.parseDouble(args[1]);
		int clusterSize = Integer.parseInt(args[2]);
		int repeat = Integer.parseInt(args[3]);

		int numNodes = cxt.getNumberOfNodes();
		String algoName = null;
		int idSizeInByte = 0;
		if (style != Style.RAW) {
			algoName = args[5];
			idSizeInByte = Integer.parseInt(args[6]);
		}

		// prepare key-value pairs
		Set<Entry> kvPairSet = new LinkedHashSet<Entry>();
		Set<Set<Entry>> requestSet = new LinkedHashSet<Set<Entry>>();

		int nPairs = clusterSize * repeat;
		for (int i = 0; i < nPairs; i++) {
			kvPairSet.add(new Entry("k" + i, "v" + i, idSizeInByte));
		}

		// cluster the pairs
		long clusteringTime = System.currentTimeMillis();

		if (style != Style.RAW) {
			// prepare RoutingAlgorithm instance
			RoutingAlgorithm algo = IDClusteringAlgorithm.getRoutingAlgorithm(out, algoName, idSizeInByte);
			if (algo == null) return false;

			if (style == Style.CLUSTERED) {
				IDClusteringAlgorithm.cluster(out, requestSet, kvPairSet, clusterSize, algo);
			}
			else {	// style == Style.NODE
				IDClusteringAlgorithm.alignWithNodes(out, requestSet, kvPairSet, clusterSize, algo, numNodes);
			}
		}
		else {	// Style.RAW
			IDClusteringAlgorithm.copy(out, requestSet, kvPairSet, clusterSize);
		}

		clusteringTime = System.currentTimeMillis() - clusteringTime;

		// write out
		PrintWriter writer = cxt.getWriter();
		long emulationTime;

		writer.println("# clustering time: " + (clusteringTime * 0.001) + " sec");
		emulationTime = this.writePutsOrGets(true, writer, requestSet, numNodes, startTime, interval);

		String fname = null;
		try {
			fname = Main.DEFAULT_SCENARIO_FILENAME + ".1";	// "scenario.1"
			writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(
					new FileOutputStream(fname), Main.ENCODING)));
		}
		catch (Exception e) {
			out.println("Could not open file: " + fname);
			return false;
		}

		writer.println("# clustering time: " + (clusteringTime * 0.001) + " sec");
		emulationTime = this.writePutsOrGets(false, writer, requestSet, numNodes, startTime, interval);

		out.println("finish at " + ((double)emulationTime / 1000.0));

		return false;
	}

	private long writePutsOrGets(boolean writePuts,
			PrintWriter writer, Set<Set<Entry>> requestSet,
			int numNodes, long startTime, long interval) {
		Random rnd = new Random();

		long time = startTime;
		for (Set<Entry> s: requestSet) {
			// e.g. schedule 1000 control 2 put k0 v0 - k1 ...
			// e.g. schedule 1000 control 2 get k0 k1 ...
			StringBuilder sb = new StringBuilder();
			sb.append("schedule ");
			sb.append(time);
			sb.append(" control ");
			sb.append(rnd.nextInt(numNodes) + 1);
			if (writePuts)
				sb.append(" put");
			else
				sb.append(" get");

			boolean firstElem = true;
			for (Entry e: s) {
				if (writePuts) {
					if (firstElem)
						firstElem = false;
					else
						sb.append(" -");
				}

				sb.append(" ");
				sb.append(e.getKey());
				if (writePuts) {
					sb.append(" ");
					sb.append(e.getValue());
				}
			}

			writer.println(sb.toString());

			time += interval * s.size();
		}
		writer.flush();

		return time;
	}
}
