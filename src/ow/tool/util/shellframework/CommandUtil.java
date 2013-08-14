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

package ow.tool.util.shellframework;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import ow.id.ID;
import ow.id.IDAddressPair;
import ow.messaging.MessagingAddress;
import ow.routing.RoutingAlgorithmConfiguration;
import ow.routing.RoutingException;
import ow.routing.RoutingHop;
import ow.routing.RoutingResult;
import ow.routing.RoutingService;
import ow.routing.impl.IterativeRoutingDriver;
import ow.util.HighLevelService;
import ow.util.HighLevelServiceConfiguration;


public final class CommandUtil {
	public static <T extends HighLevelService>
			boolean executeHelp(ShellContext<T> context) {
		PrintStream out = context.getOutputStream();
		List<Command<T>> commandList = context.getCommandList();

		for (Command<T> command: commandList) {
			out.print(command.getHelp() + Shell.CRLF);
		}
		out.flush();

		return false;
	}

	public static boolean executeInit(ShellContext<? extends HighLevelService> context, Command<?> cmd)
			throws UnknownHostException {
		HighLevelService svc = context.getOpaqueData();
		PrintStream out = context.getOutputStream();
		String[] args = context.getArguments();
		boolean showStatus = false;
		int argIndex = 0;

		if (argIndex < args.length && args[argIndex].startsWith("-")) {
			showStatus = true;
			argIndex++;
		}

		HighLevelServiceConfiguration config = svc.getConfiguration();
		int port = config.getSelfPort();

		if (args.length - argIndex < 1) {
			out.print("usage: " + cmd.getHelp() + Shell.CRLF);
			out.flush();

			return false;
		}
		if (args.length - argIndex >= 2) {
			port = Integer.parseInt(args[argIndex + 1]);
		}

		MessagingAddress contactAddr = null;
		try {
			contactAddr = svc.joinOverlay(args[argIndex], port);
		}
		catch (UnknownHostException e) {
			out.print("Hostname resolution failed on "
					+ svc.getSelfIDAddressPair().getAddress() + Shell.CRLF);
			out.flush();
			throw e;
		}
		catch (RoutingException e) {
			out.print("routing failed on " + svc.getSelfIDAddressPair() + Shell.CRLF);
		}

		if (contactAddr != null) {
			out.print("contact: " + contactAddr.getHostAddress() + ":" + contactAddr.getPort() + Shell.CRLF);
		}
		else {
			out.print("joining failed." + Shell.CRLF);
		}

		if (showStatus) {
			out.print(CommandUtil.buildStatusMessage(context.getOpaqueData(), -1));
		}

		out.flush();

		return false;
	}

	public static <T extends HighLevelService>
			boolean executeSource(ShellContext<T> context, Command<?> cmd) {
		ShellServer<T> shellServer = context.getShellServer();
		T dht = context.getOpaqueData();
		PrintStream out = context.getOutputStream();
		String[] args = context.getArguments();

		if (args.length < 1) {
			out.print("usage: " + cmd.getHelp() + Shell.CRLF);
			out.flush();

			return false;
		}

		if (shellServer != null) {
			for (int i = 0; i < args.length; i++) {
				InputStream in;
				try {
					in = new FileInputStream(args[0]);
				}
				catch (FileNotFoundException e) {
					out.print("File not found: " + args[0] + Shell.CRLF);
					continue;
				}

				Shell<T> subShell =
					new Shell<T>(in, out, shellServer, dht, false);

				subShell.run();
			}
		}

		return false;
	}

	public static StringBuilder buildStatusMessage(HighLevelService svc, int verboseLevel) {
		RoutingService routingSvc = svc.getRoutingService();
		RoutingAlgorithmConfiguration algoConf = svc.getRoutingAlgorithmConfiguration();
		boolean iterative = (routingSvc instanceof IterativeRoutingDriver);

		// construct strings
		StringBuilder sb = new StringBuilder();

		// show self address
		sb.append("ID and address: ").append(svc.getSelfIDAddressPair().toString(verboseLevel)).append(Shell.CRLF);

		// show routing table
		sb.append("Routing table:").append(Shell.CRLF);
		String routingTableString = svc.getRoutingTableString(verboseLevel).replaceAll("\n", Shell.CRLF);
		sb.append(routingTableString).append(Shell.CRLF);

		// show last key, route and route candidates
		ID[] keys = svc.getLastKeys();
		RoutingResult[] routingResults = svc.getLastRoutingResults();

		if (keys != null && routingResults != null) {
			sb.append("Last keys & routes: ").append(Shell.CRLF);

			int numOfMsgs;
			sb.append("number of messages: ");
			numOfMsgs = calcNumOfMessagesWithoutCollectiveForwarding(routingResults, iterative);
			sb.append(numOfMsgs);
			sb.append(" -> ");
			numOfMsgs = calcNumOfMessagesWithCollectiveForwarding(routingResults, iterative);
			sb.append(numOfMsgs);
			sb.append(Shell.CRLF);

			for (int i = 0; i < keys.length; i++) {
				if (keys[i] == null || routingResults[i] == null) continue;

				sb.append("key[").append(i).append("]: " );
				sb.append(keys[i].toString(verboseLevel)).append(Shell.CRLF);

				RoutingHop[] route = routingResults[i].getRoute();

				sb.append("route[").append(i).append("] (length: ").append(route.length - 1).append("): ");
				sb.append("[");

				long timeBase = -1L;

				for (RoutingHop hop: route) {
					if (timeBase < 0L) {
						timeBase = hop.getTime();
					}

					sb.append(Shell.CRLF).append(" ");
					sb.append(hop.getIDAddressPair().toString(verboseLevel));
					sb.append(" (").append(hop.getTime() - timeBase).append(")");
				}
				sb.append(Shell.CRLF).append("]").append(Shell.CRLF);

				sb.append("responsible node candidates[").append(i).append("]: ");
				sb.append("[");
				for (IDAddressPair r: routingResults[i].getResponsibleNodeCandidates()) {
					sb.append(Shell.CRLF).append(" ");
					sb.append(r.toString(verboseLevel));
				}
				sb.append(Shell.CRLF).append("]").append(Shell.CRLF);
			}
		}

		return sb;
	}

	/**
	 * Calculates the number of messages without collective forwarding.
	 * Note that the algorithm assumes each node returns ACK.
	 */
	private static int calcNumOfMessagesWithoutCollectiveForwarding(
			RoutingResult[] results, boolean iterative) {
		int num = 0;

		for (int i = 0; i < results.length; i++) {
			if (results[i] == null) continue;
			RoutingHop[] route = results[i].getRoute();
			if (route.length <= 0) continue;

			IDAddressPair self = route[0].getIDAddressPair();

			// count {ITE,REC}_ROUTE_*
			for (int j = 0; j < route.length - 1; j++) {
				IDAddressPair h = route[j].getIDAddressPair();

				if (!self.equals(h)) num += 2;
			}

			{
				int lastIndex = route.length - 1;
				if (lastIndex < 0) lastIndex  = 0;

				IDAddressPair h = route[lastIndex].getIDAddressPair();

				if (!self.equals(h)) {
					if (iterative) {
						// count {ITE,REC}_ROUTE_*
						num += 2;
					}
					else {
						// add REC_RESULT
						num += 3;
					}
				}
			}
		}

		return num;
	}

	/**
	 * Calculates the number of messages with collective forwarding.
	 * Note that the algorithm assumes each node returns ACK.
	 */
	private static int calcNumOfMessagesWithCollectiveForwarding(
			RoutingResult[] results, boolean iterative) {
		int num = 0;
		IDAddressPair self = null;

		List<List<RoutingHop>> routes = new ArrayList<List<RoutingHop>>();
		for (int i = 0; i < results.length; i++) {
			if (results[i] == null
					|| results[i].getRoute().length <= 1)
				continue;

			List<RoutingHop> l = new ArrayList<RoutingHop>();
			boolean firstElem = true;
			for (RoutingHop h: results[i].getRoute()) {
				if (firstElem) {
					if (self == null) self = h.getIDAddressPair();

					firstElem = false;
					continue;
				}

				l.add(h);
			}

			if (!l.isEmpty()) {
				routes.add(l);
			}
		}

		num += calcNumOfMessagesWithCollectiveForwarding(routes, iterative, self);

		return num;
	}

	private static int calcNumOfMessagesWithCollectiveForwarding(
			List<List<RoutingHop>> routes, boolean iterative, IDAddressPair self) {
		Set<IDAddressPair> firstHopSet = new HashSet<IDAddressPair>();

		for (List<RoutingHop> l: routes) {
			RoutingHop h = l.get(0);
			IDAddressPair firstHop = h.getIDAddressPair();

			if (!self.equals(firstHop))
				firstHopSet.add(firstHop);
		}

		int num = 2 * firstHopSet.size();
			// In case of iterative forwarding, ITE_ROUTE_* and ITE_REPLY
			// In case of recursive forwarding, REC_ROUTE_* and REC_ACK

		List<List<RoutingHop>> partOfRoutes = new ArrayList<List<RoutingHop>>();
		for (IDAddressPair p: firstHopSet) {
			boolean reachAndCountLastHop = false;
			partOfRoutes.clear();

			for (Iterator<List<RoutingHop>> itr = routes.iterator(); itr.hasNext(); ) {
				List<RoutingHop> l = itr.next();
				RoutingHop h = l.get(0);
				IDAddressPair firstHop = h.getIDAddressPair();

				if (p.equals(firstHop)) {
					itr.remove();	// note: Iterator#remove() does not work no a HashSet.

					l.remove(0);

					if (l.isEmpty()) {
						if (!self.equals(firstHop))
							reachAndCountLastHop = true;
					}
					else
						partOfRoutes.add(l);
				}
			}

			if (!iterative && reachAndCountLastHop) {
				num++;		// REC_RESULT
			}

			if (!partOfRoutes.isEmpty()) {
				num += calcNumOfMessagesWithCollectiveForwarding(partOfRoutes, iterative, self);	// recursive call
			}
		}

		return num;
	}
}
