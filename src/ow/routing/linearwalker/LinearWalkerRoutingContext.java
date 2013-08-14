/*
 * Copyright 2010 Kazuyuki Shudo, and contributors.
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

package ow.routing.linearwalker;

import ow.routing.RoutingContext;

public class LinearWalkerRoutingContext extends RoutingContext {
	private boolean routeToPredecessorOfTarget = false;
	private boolean onLastPhase = false;

	public LinearWalkerRoutingContext() {}

	protected LinearWalkerRoutingContext(LinearWalkerRoutingContext c) {	// for clone()
		super(c);

		this.routeToPredecessorOfTarget = c.routeToPredecessorOfTarget;
		this.onLastPhase = c.onLastPhase;
	}

	public void setRouteToPredecessorOfTarget() { this.routeToPredecessorOfTarget = true; }
	public boolean routeToPredecessorOfTarget() { return this.routeToPredecessorOfTarget; }

	public void setLastPhase() { this.onLastPhase = true; }
	public boolean inLastPhase() { return this.onLastPhase; }

	public int hashCode() {
		int ret = 0;
		ret |= (this.routeToPredecessorOfTarget ? 1 : 0);
		ret |= (this.onLastPhase ? 2 : 0);

		return ret;
	}

	public boolean equals(Object o) {
		if (o instanceof LinearWalkerRoutingContext) {
			LinearWalkerRoutingContext c = (LinearWalkerRoutingContext)o;
			if (this.routeToPredecessorOfTarget == c.routeToPredecessorOfTarget
					/*&& this.onLastPhase == c.onLastPhase*/)	{	// ignore onLastPhase when judging termination
				return true;
			}
		}

		return false;
	}

	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("[route to tgt's pred: ").append(this.routeToPredecessorOfTarget);
		sb.append(", ").append("last phase: ").append(onLastPhase);
		sb.append("]");
		return sb.toString();
	}

	public LinearWalkerRoutingContext clone() {
		return new LinearWalkerRoutingContext(this);
	}
}
