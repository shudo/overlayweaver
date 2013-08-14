/*
 * Copyright 2006 National Institute of Advanced Industrial Science
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

package ow.tool.scenariogen;

import java.io.PrintWriter;

public class ScenarioGeneratorContext {
	private PrintWriter out;
	int numNodes = 0;
	int numJoiningNodes = 0;
	NodeStatus[] nodeStats = null;

	ScenarioGeneratorContext(int numNodes) {
		// generate an array
		this.nodeStats = new NodeStatus[numNodes];
		for (int i = 0; i < numNodes; i++) {
			this.nodeStats[i] = new NodeStatus();
		}

		this.numNodes = numNodes;

		// make the first node online
		this.setJoinStatus(0, true);
	}

	public PrintWriter getWriter() { return this.out; }
	public PrintWriter setWriter(PrintWriter out) {
		PrintWriter old = this.out;
		this.out = out;
		return old;
	}

	public int getNumberOfNodes() { return this.numNodes; }

	public void setNumberOfNodes(int num) {
		NodeStatus[] oldStats = this.nodeStats;

		if (num > this.numNodes) {
			// expand the array
			this.nodeStats = new NodeStatus[num];
			System.arraycopy(oldStats, 0, this.nodeStats, 0, this.numNodes);
			for (int i = this.numNodes; i < num; i++) {
				this.nodeStats[i] = new NodeStatus();
			}

			this.numNodes = num;
		}
	}

	public int getNumberOfJoiningNodes() { return this.numJoiningNodes; }

	public void setJoinStatusToAllNodes(boolean joined) {
		for (int i = 0; i < this.numNodes; i++) {
			this.setJoinStatus(i, true);
		}

		this.numJoiningNodes = this.numNodes;
	}

	public boolean getJoinStatus(int i) {
		NodeStatus stat = this.nodeStats[i];

		return stat.getJoinStatus();
	}

	public boolean setJoinStatus(int i, boolean joined) {
		NodeStatus stat = this.nodeStats[i];

		boolean old = stat.getJoinStatus();
		if (joined != old) {
			if (joined)
				this.numJoiningNodes++;
			else
				this.numJoiningNodes--;
		}

		return stat.setJoinStatus(joined);
	}
}
