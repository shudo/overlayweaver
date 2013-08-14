/*
 * Copyright 2006-2010,2012 Kazuyuki Shudo, and contributors.
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

package ow.tool.dhtshell;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.XmlRpcHandler;
import org.apache.xmlrpc.XmlRpcRequest;
import org.apache.xmlrpc.server.XmlRpcHandlerMapping;
import org.apache.xmlrpc.server.XmlRpcNoSuchHandlerException;
import org.apache.xmlrpc.webserver.ServletWebServer;
import org.apache.xmlrpc.webserver.WebServer;
import org.apache.xmlrpc.webserver.XmlRpcServlet;

import ow.dht.ByteArray;
import ow.dht.DHT;
import ow.dht.DHTConfiguration;
import ow.dht.ValueInfo;
import ow.id.ID;
import ow.id.IDAddressPair;
import ow.routing.RoutingAlgorithm;
import ow.routing.RoutingException;
import ow.routing.RoutingHop;
import ow.routing.RoutingResult;
import ow.tool.util.shellframework.Interruptible;
import ow.util.HTMLUtil;

/**
 * An XML-RPC DHT server.
 * The access protocol is compatible with Bamboo and OpenDHT.
 * This server is enabled by `-x' option of owdhtshell command.
 */
public final class XmlRpcDHTServer implements Interruptible {
	public final static String ENCODING = "UTF-8";

	// IPInfoDB API key
	public final static String ipInfoDBAPIKey =
		"b057995e0dd39d138d850768871ee1e70a5c87950b7e6274953d2e88d37798bf";

	// Google Maps API key
//	public final static String googleMapsAPIKey =
//		"ABQIAAAAqb57ex4QzZoN7USfDeGHZBTmq4XR8OZsrIRz8XFxocQJ7gusBxQJ1npT5jF0m6BVv3erZkoFkHAGXQ";
			// for http://pl.shudo.net
		// "ABQIAAAAqb57ex4QzZoN7USfDeGHZBQfW-q-1gGH62EQVSaqLo6F-ZxCOBTwTQmgPkaae13xuJqNZFLQ3Zov7A";
			// for http://oweaver.nyuld.net

	enum WebServerType {
		XMLRPC_WITHOUT_SERVLET,
		XMLRPC_WITH_SERVLET,
		JETTY
	};
	private final WebServerType webServerType = WebServerType.JETTY;

	//private Server jettyWebServer;		// for JETTY
	private Object jettyWebServer;		// for JETTY
	private Method jettyServerStopMtd;
	private WebServer xmlrpcWebServer;	// for XMLRPC_{WITH,WITHOUT}_SERVLET

	private DHT<String> dht;
	private int idSizeInByte;

	private int port;
	private XmlRpcHandler handler;

	private final boolean showMap;

	public XmlRpcDHTServer(DHT<String> dht) {
		this(dht, false);
	}

	public XmlRpcDHTServer(DHT<String> dht, boolean showMap) {
		this.dht = dht;
		this.idSizeInByte = dht.getRoutingAlgorithmConfiguration().getIDSizeInByte();
		this.showMap = showMap;

		// prepare an XML-RPC handler
		this.handler = new XmlRpcDHTRequestHandler();
	}

	public String start(int port, int range) throws Exception {
		return this.start(port, range, null);
	}
	private String start(int port, int range, InetAddress selfAddress) throws Exception {
		// start a web server
		this.jettyWebServer = null;
		Exception lastException = null;

		XmlRpcServlet servlet = new XmlRpcDHTServlet();

		for (int p = port; p < port + range; p++) {
			if (webServerType == WebServerType.XMLRPC_WITHOUT_SERVLET) {
				// with only XML-RPC 3.0
				// involves XmlRpcDHTServlet but doGet() is not called

				this.xmlrpcWebServer = new ServletWebServer(servlet, p);
				this.xmlrpcWebServer.start();
			}

			if (webServerType == WebServerType.XMLRPC_WITH_SERVLET) {
				// with only XML-RPC 3.0
				// does not involve XmlRpcDHTServlet and doGet() is not called

				this.xmlrpcWebServer = (selfAddress != null ?
						new WebServer(p, selfAddress) :
							new WebServer(p));

				this.xmlrpcWebServer.getXmlRpcServer().setHandlerMapping(new Mapper());

				try {
					this.xmlrpcWebServer.start();

					// succeeded to start a web server
					this.port = p;
					break;
				}
				catch (IOException e) {
					lastException = e;
					continue;
				}
			}

			if (webServerType == WebServerType.JETTY) {
				// with Jetty
				// involves XmlRpcDHTServlet and doGet is called

//				this.jettyWebServer = new Server(p);
//				Context root = new Context(this.jettyWebServer, "/", Context.SESSIONS);
//				root.addServlet(new ServletHolder(servlet), "/*");
//
//				try {
//					this.jettyWebServer.start();
//
//					// succeeded to start a web server
//					this.port = p;
//					break;
//				}
//				catch (Exception e) {
//					lastException = e;
//					continue;
//				}

				try {
					Class jettyServerClazz = Class.forName("org.eclipse.jetty.server.Server");
					Class jettyContextClazz = Class.forName("org.eclipse.jetty.servlet.ServletContextHandler");
					Class jettyServletHolderClazz = Class.forName("org.eclipse.jetty.servlet.ServletHolder");
					Class jettyHandlerContainerClazz = Class.forName("org.eclipse.jetty.server.HandlerContainer");

					Constructor jettyServerCtor = jettyServerClazz.getDeclaredConstructor(int.class);
					Constructor jettyContextCtor = jettyContextClazz.getDeclaredConstructor(jettyHandlerContainerClazz, String.class, int.class);
					Constructor jettyServletHolderCtor = jettyServletHolderClazz.getDeclaredConstructor(Servlet.class);
					Method jettyContextAddServletMtd = jettyContextClazz.getDeclaredMethod("addServlet", jettyServletHolderClazz, String.class);

					this.jettyWebServer = jettyServerCtor.newInstance(p);
					Object root = jettyContextCtor.newInstance(this.jettyWebServer, "/", 1);
					Object servletHolder = jettyServletHolderCtor.newInstance(servlet);
					jettyContextAddServletMtd.invoke(root, servletHolder, "/*");
						// jettyWebServer = new Server();
						// root = new ServletContextHandler(jettyWebServer, "/", 1);
						// servletHolder = new ServletHolder();
						// root.addServlet(servletHoldre, "/*");

					Method jettyServerStartMtd = jettyServerClazz.getMethod("start");
					this.jettyServerStopMtd = jettyServerClazz.getMethod("stop");

					jettyServerStartMtd.invoke(this.jettyWebServer);
						// jettyWebServer.start();

					// succeeded to start a web server
					this.port = p;
					break;
				}
				catch (Exception e) {
					lastException = e;
					continue;
				}
			}
		}

		if (this.jettyWebServer == null && xmlrpcWebServer == null) {
			throw lastException;
		}

		return HTMLUtil.convertMessagingAddressToURL(
				this.dht.getSelfIDAddressPair().getAddress(), this.port);
	}

	public void stop() {
		try {
			this.jettyServerStopMtd.invoke(this.jettyWebServer);
				// this.jettyWebServer.stop();
		}
		catch (Exception e) { /* ignore */ }

		this.jettyWebServer = null;
	}
	public void interrupt() {
		this.stop();
	}

	public int getPort() {
		return this.port;
	}

	private class XmlRpcDHTServlet extends XmlRpcServlet {
		protected XmlRpcHandlerMapping newXmlRpcHandlerMapping() throws XmlRpcException {
			return new Mapper();
		}

		// show node information
		protected void doGet(HttpServletRequest req, HttpServletResponse res)
				throws ServletException, IOException {
			// set request and response encoding "UTF-8"
			req.setCharacterEncoding(ENCODING);
			res.setCharacterEncoding(ENCODING);

			// parse query string
			String resultString = null;
			String op = null, key = null, value = null, ttl = null, secret = null;
			Set<ValueInfo<String>> getResult = null;
			boolean lookupPerformed = false;

			String qs = req.getQueryString();
			if (qs != null) {
				Map<String,String> paramTable = new HashMap<String,String>();

				String[] tokens = qs.split("&");
				for (String t: tokens) {
					int eqIndex = t.indexOf('=');
					if (eqIndex <= 0) continue;

					paramTable.put(t.substring(0, eqIndex), t.substring(eqIndex + 1));
				}

				op = paramTable.get("op");
				key = paramTable.get("key");
				value = paramTable.get("value");
				ttl = paramTable.get("ttl");
				secret = paramTable.get("secret");

				if (key != null) key = URLDecoder.decode(key, ENCODING);
				if (value != null) value = URLDecoder.decode(value, ENCODING);
				if (secret != null) secret = URLDecoder.decode(secret, ENCODING);

				if (op != null && key != null) {
					ID keyID = ID.parseID(key, dht.getRoutingAlgorithmConfiguration().getIDSizeInByte());

					if (op.equals("get")) {
						try {
							getResult = dht.get(keyID);

							lookupPerformed = true;

							if (getResult == null || getResult.size() <= 0) {
								resultString = "No value associated with \"" + key + "\".";
							}
							else {
								resultString = "Get results:";
							}
						}
						catch (RoutingException e) {
							resultString = "Routing failed.";
						}
					}
					else if (op.equals("put")) {
						if (value == null || value.length() <= 0 || ttl == null) {
							resultString = "Error: put requires a value and a TTL.";
						}
						else {
							int ttlNum = Integer.parseInt(ttl) * 1000;
							if (secret == null) {
								dht.setHashedSecretForPut(null);
							}
							else {
								ByteArray hashedSecret = new ByteArray(secret.getBytes(ENCODING)).hashWithSHA1();
								dht.setHashedSecretForPut(hashedSecret);
							}
							dht.setTTLForPut(ttlNum);

							try {
								dht.put(keyID, value);

								lookupPerformed = true;
								resultString = "Put succeeded.";
							}
							catch (RoutingException e) {
								resultString = "Routing failed.";
							}
							catch (Exception e) {
								resultString = "Put failed.";
							}
						}
					}
					else if (op.equals("remove")) {
						if (secret == null) {
							resultString = "Error: remove requires a secret.";
						}
						else {
							if (value == null || value.length() <= 0) {
								value = null;
							}

							ByteArray hashedSecret = new ByteArray(secret.getBytes(ENCODING)).hashWithSHA1();

							if (value == null) {
								try {
									Set<ValueInfo<String>> s = dht.remove(keyID, hashedSecret);
									lookupPerformed = true;

									if (s == null) {
										resultString = "There is no such key-value pair.";
									}
									else {
										resultString = "Removed:";
										for (ValueInfo<String> vi: s) {
											resultString += " \"" + vi.getValue() + "\"";
										}
									}
								}
								catch (RoutingException e) {
									resultString = "Routing failed.";
								}
							}
							else {
								String[] values = new String[1];
								values[0] = value;

								try {
									Set<ValueInfo<String>> s = dht.remove(keyID, values, hashedSecret);
									lookupPerformed = true;

									ValueInfo<String> vi = null;
									for (ValueInfo<String> v: s) { vi = v; }

									if (vi == null) {
										resultString = "There is no such key-value pair.";
									}
									else {
										resultString = "Removed: \"" + vi.getValue() + "\"";
									}
								}
								catch (RoutingException e) {
									resultString = "Routing failed.";
								}
							}
						}
					}
				}
			}

			// generate HTML document
			PrintWriter wtr = res.getWriter();

			DHTConfiguration dhtConf = dht.getConfiguration();
			IDAddressPair idAddr = dht.getSelfIDAddressPair();
			String url = HTMLUtil.convertMessagingAddressToURL(idAddr.getAddress());

			res.setContentType("text/html");
			res.setStatus(HttpServletResponse.SC_OK);

			wtr.println("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\">");
			wtr.println("<html>");
			wtr.println("<head>");
			wtr.println("<title>Overlay Weaver Node: " + HTMLUtil.stringInHTML(url) + "</title>");
			wtr.println("<style type=text/css>");
			wtr.println("h3 { border-left: solid 0.6em #208020; border-bottom: solid 1pt }");
			wtr.println("</style>");

			RoutingResult[] routingRes = dht.getLastRoutingResults();
			boolean showMapHere;

			if (showMap
					&& lookupPerformed
					&& routingRes != null
					&& routingRes[0] != null)
				showMapHere = true;
			else
				showMapHere = false;

			if (showMapHere) {
				wtr.println("<meta name=\"viewport\" content=\"initial-scale=1.0, user-scalable=yes\">");
				wtr.println("<script type=\"text/javascript\" src=\"http://maps.google.com/maps/api/js?sensor=false\"></script>");

				wtr.println("<script type=\"text/javascript\">");
				wtr.println("var ipAddresses = [");

				RoutingHop[] route = routingRes[0].getRoute();

				for (int i = 0; i < route.length; i++) {
					IDAddressPair p = route[i].getIDAddressPair();
					wtr.print("  '");
					wtr.print(p.getAddress().getHostAddress());
					wtr.print("'");
					if (i != route.length - 1) wtr.print(",");
					wtr.println();
				}
				wtr.println("];");

				wtr.println("var nodeIndices = new Array(ipAddresses.length);");
				wtr.println("for (var i = 0; i < ipAddresses.length; i++) {");
				wtr.println("  nodeIndices[ipAddresses[i]] = i;");
				wtr.println("}");

				wtr.println("var nodes = new Array(ipAddresses.length);");
				wtr.println("var nodeCount = 0;");
				wtr.println("var lineDrawn = false;");

				wtr.println("var map;");
				wtr.println("var shadow;");
				wtr.println("var lastInfoWindow;");
				wtr.println();
				wtr.println("function initialize() {");
				wtr.println("  // create a shadow image");
				wtr.println("  shadow = new google.maps.MarkerImage(");
				wtr.println("    'http://www.google.com/mapfiles/shadow50.png',");
				wtr.println("    new google.maps.Size(37, 34), // size");
				wtr.println("    new google.maps.Point(0, 0), // origin");
				wtr.println("    new google.maps.Point(9, 34)); // anchor");
				wtr.println();
				wtr.println("  // create a map");
				wtr.println("  var latlng = new google.maps.LatLng(25, 10);");
				wtr.println("  var mapOptions = {");
				wtr.println("    zoom: 2,");
				wtr.println("    center: latlng,");
				wtr.println("    mapTypeControl: true,");
				wtr.println("    mapTypeControlOptions: {style: google.maps.MapTypeControlStyle.DEFAULT},");
				wtr.println("    navigationControl: true,");
				wtr.println("    navigationControlOptions: {style: google.maps.NavigationControlStyle.DEFAULT},");
				wtr.println("    mapTypeId: google.maps.MapTypeId.SATELLITE");
				wtr.println("  };");
				wtr.println("  map = new google.maps.Map(document.getElementById(\"map_canvas\"), mapOptions);");
				wtr.println();
				wtr.println("  var script;");
				wtr.println("  for (var i = 0; i < ipAddresses.length; i++) {");
				wtr.println("    script = document.createElement('script');");
				wtr.println("    script.src = 'http://api.ipinfodb.com/v2/ip_query.php?key=" + ipInfoDBAPIKey + "&output=json&callback=addNode&ip=' + ipAddresses[i];");
				wtr.println("    document.body.appendChild(script);");
				wtr.println("  }");
				wtr.println("}");
				wtr.println();
				wtr.println("function addNode(geoDict) {");
				wtr.println("  if (geoDict['Status'] == 'OK') {");
				wtr.println("    var latlng = new google.maps.LatLng(geoDict['Latitude'], geoDict['Longitude']);");
				wtr.println("    var index = nodeIndices[geoDict['Ip']];");
				wtr.println("    if (nodes[index] == null) nodeCount++;");
				wtr.println("    nodes[index] = latlng;");
				wtr.println();
				wtr.println("    // add a marker");
				wtr.println("    if (index < 26) {");
				wtr.println("      var letter = String.fromCharCode(\"A\".charCodeAt(0) + index);");
				wtr.println("      var image = new google.maps.MarkerImage(");
				wtr.println("        'http://www.google.com/mapfiles/marker' + letter + '.png',");
				wtr.println("        new google.maps.Size(20, 34), // size");
				wtr.println("        new google.maps.Point(0, 0), // origin");
				wtr.println("        new google.maps.Point(9, 34)); // anchor");
				wtr.println("      var marker = new google.maps.Marker({");
				wtr.println("        position: latlng,");
				wtr.println("        map: map,");
				wtr.println("        shadow: shadow,");
				wtr.println("        icon: image,");
				wtr.println("      });");
				wtr.println("    }");
				wtr.println("    else {");
				wtr.println("      var marker = new google.maps.Marker({");
				wtr.println("        position: latlng,");
				wtr.println("        map: map,");
				wtr.println("      });");
				wtr.println("    }");
				wtr.println();
				wtr.println("    var contentString = '[' + index + '] <tt>' + geoDict['Ip'] + '</tt>:'");
				wtr.println("    var s;");
				wtr.println("    s = geoDict['City']; if (s != null && s.length > 0) contentString += '<br>' + s;");
				wtr.println("    s = geoDict['RegionName']; if (s != null && s.length > 0) contentString += '<br>' + s;");
				wtr.println("    s = geoDict['CountryName']; if (s != null && s.length > 0) contentString += '<br>' + s;");
				wtr.println("    s = geoDict['CountryCode']; if (s != null && s.length > 0) contentString += ' <img src=\"http://ipinfodb.com/img/flags/' + s.toLowerCase() + '.gif\">'");
				wtr.println("    var infoWindow = new google.maps.InfoWindow({");
				wtr.println("      content: contentString");
				wtr.println("    });");
				wtr.println("    google.maps.event.addListener(marker, 'click', function() {");
				wtr.println("      if (lastInfoWindow != null) lastInfoWindow.close();");
				wtr.println("      lastInfoWindow = infoWindow;");
				wtr.println("      infoWindow.open(map, marker);");
				wtr.println("    });");
				wtr.println("  }");
				wtr.println();
				wtr.println("  if (nodeCount >= ipAddresses.length && !lineDrawn) {");
				wtr.println("    lineDrawn = true;");
				wtr.println();
				wtr.println("    // draw a polyline");
				wtr.println("    var polyOptions = {");
				wtr.println("      path: nodes,");
				wtr.println("      strokeColor: \"#80ff40\",");
				wtr.println("      strokeOpacity: 1.0,");
				wtr.println("      strokeWeight: 5");
				wtr.println("    };");
				wtr.println("    polyline = new google.maps.Polyline(polyOptions);");
				wtr.println("    polyline.setMap(map);");
				wtr.println("  }");
				wtr.println("}");
				wtr.println("</script>");
			}

			wtr.println("</head>");
			wtr.println();

			if (showMapHere) {
				wtr.println("<body onload=\"initialize()\">");
			}
			else {
				wtr.println("<body>");
			}
			wtr.println("<h1>Overlay Weaver Node Status</h1>");
			wtr.println();
			wtr.println("<h3>Node Information</h3>");
			wtr.println("<table>");
			wtr.println("<tr><td>URL:</td><td><a href=\"" + url + "\">" + HTMLUtil.stringInHTML(url) + "</a></td></tr>");
			wtr.println("<tr><td>Node ID:</td><td>" + HTMLUtil.stringInHTML(idAddr.getID().toString()) + "</td></tr>");
			wtr.println("<tr><td>Lookup algorithm:</td><td>" + HTMLUtil.stringInHTML(dhtConf.getRoutingAlgorithm()) + "</td></tr>");
			wtr.println("<tr><td>Lookup style:</td><td>" + HTMLUtil.stringInHTML(dhtConf.getRoutingStyle()) + "</td></tr>");
			wtr.println("<tr><td># of stored keys:</td><td>" + HTMLUtil.stringInHTML(Integer.toString(dht.getGlobalKeys().size())) + "</td></tr>");
//			wtr.println("<tr><td>:</td><td>" +  + "</td></tr>");
			wtr.println("</table>");
			wtr.println();
			wtr.println("<h3>Routing Table</h3>");

			RoutingAlgorithm algo = dht.getRoutingService().getRoutingAlgorithm();
			wtr.println(algo.getRoutingTableHTMLString());

			wtr.println("<h3>Put, Get and Remove Operations</h3>");
			wtr.println("<table>");

			wtr.println("<tr><th>operation</th><th>key</th><th>value</th><th>TTL (sec)</th><th>secret</th></tr>");

			wtr.println("<form action=\"\" method=\"get\" accept-charset=\"UTF-8\">");
			wtr.println("<input type=\"hidden\" name=\"op\" value=\"get\">");
			wtr.println("<tr>");
			wtr.println("<td>get</td>");
			wtr.println("<td colspan=\"4\"><input type=\"text\" name=\"key\" size=\"10\""
					+ (key != null ? " value=\"" + HTMLUtil.stringInHTML(key) + "\"" : "") + "></td>");
			wtr.println("<td><input type=\"submit\" value=\"submit\"></td>");
			wtr.println("</tr>");
			wtr.println("</form>");

			wtr.println("<form action=\"\" method=\"get\" accept-charset=\"UTF-8\">");
			wtr.println("<input type=\"hidden\" name=\"op\" value=\"put\">");
			wtr.println("<tr>");
			wtr.println("<td>put</td>");
			wtr.println("<td><input type=\"text\" name=\"key\" size=\"10\""
					+ (key != null ? " value=\"" + HTMLUtil.stringInHTML(key) + "\"" : "") + "></td>");
			wtr.println("<td><input type=\"text\" name=\"value\" size=\"10\""
					+ (value != null ? " value=\"" + HTMLUtil.stringInHTML(value) + "\"" : "") + "></td>");
			wtr.println("<td><input type=\"text\" name=\"ttl\" size=\"6\" value=\""
					+ (ttl != null ? ttl : "600") + "\"></td>");
			wtr.println("<td><input type=\"text\" name=\"secret\" size=\"10\""
					+ (secret != null ? " value=\"" + HTMLUtil.stringInHTML(secret) + "\"" : "") + "> (option)</td>");
			wtr.println("<td><input type=\"submit\" value=\"submit\"></td>");
			wtr.println("</tr>");
			wtr.println("</form>");

			wtr.println("<form action=\"\" method=\"get\" accept-charset=\"UTF-8\">");
			wtr.println("<input type=\"hidden\" name=\"op\" value=\"remove\">");
			wtr.println("<tr>");
			wtr.println("<td>remove</td>");
			wtr.println("<td><input type=\"text\" name=\"key\" size=\"10\""
					+ (key != null ? " value=\"" + HTMLUtil.stringInHTML(key) + "\"" : "") + "></td>");
			wtr.println("<td colspan=\"2\"><input type=\"text\" name=\"value\" size=\"10\""
					+ (value != null ? " value=\"" + HTMLUtil.stringInHTML(value) + "\"" : "") + "> (option)</td>");
			wtr.println("<td><input type=\"text\" name=\"secret\" size=\"10\""
					+ (secret != null ? " value=\"" + HTMLUtil.stringInHTML(secret) + "\"" : "") + "></td>");
			wtr.println("<td><input type=\"submit\" value=\"submit\"></td>");
			wtr.println("</tr>");
			wtr.println("</form>");

			wtr.println("</table>");
			wtr.println();

			if (resultString != null) {
				wtr.println("<h3>Results</h3>");

				wtr.println("<p>");
				wtr.println(HTMLUtil.stringInHTML(resultString));
				wtr.println("</p>");

				if (getResult != null) {
					wtr.println("<table>");
					wtr.println("<tr><td>key:</td><td>" + HTMLUtil.stringInHTML(key) + "</td></tr>");
					for (ValueInfo<String> vInfo: getResult) {
						String v = vInfo.getValue();
						wtr.println("<tr><td>value:</td><td>" + HTMLUtil.stringInHTML(v) + "</td></tr>");
					}
					wtr.println("</table>");
				}
			}

			if (lookupPerformed
					&& routingRes != null
					&& routingRes[0] != null) {
				RoutingHop[] route = routingRes[0].getRoute();

				wtr.println("<h3>Route</h3>");
				wtr.println("<table>");
				wtr.println("<tr><th>Hop</th><th>Node</th><th>ID</th><th>time</th></tr>");

				int i = 0;
				long timeBase = -1L;

				for (RoutingHop hop: route) {
					if (timeBase < 0L) {
						timeBase = hop.getTime();
					}

					IDAddressPair p = hop.getIDAddressPair();

					url = HTMLUtil.convertMessagingAddressToURL(p.getAddress());

					wtr.print("<tr><td>" + HTMLUtil.stringInHTML(Integer.toString(i)) + "</td>");
					wtr.print("<td><a href=\"" + url + "\">" + HTMLUtil.stringInHTML(url) + "</a></td>");
					wtr.print("<td>" + HTMLUtil.stringInHTML(p.getID().toString()) + "</td>");
					wtr.println("<td>" + (hop.getTime() - timeBase) + "</td></tr>");

					i++;
				}
				wtr.println("</table>");
			}

			wtr.println();

			if (showMapHere) {
				wtr.println("<div id=\"map_canvas\" style=\"width: 780px; height: 420px\"></div>");
				wtr.println();
			}

			wtr.println("</body>");
			wtr.println("</html>");
		}
	}

	private class Mapper implements XmlRpcHandlerMapping {
		public XmlRpcHandler getHandler(String arg0) throws XmlRpcNoSuchHandlerException, XmlRpcException {
			return XmlRpcDHTServer.this.handler;
		}
	}

	private class XmlRpcDHTRequestHandler implements XmlRpcHandler {
		public Object execute(XmlRpcRequest req) throws XmlRpcException {
			String methodName = req.getMethodName();
			int numParams = req.getParameterCount();

			if (methodName.equals("get_details") || methodName.equals("get")) {
				if (numParams != 4) {
					throw new XmlRpcException("Invalid number of params: " + numParams);
				}

				// parse arguments
				byte[] hashedKeyBytes = (byte[])req.getParameter(0);
				int maxVals = (Integer)req.getParameter(1);
				byte[] placeMarkBytes = (byte[])req.getParameter(2);

				ID hashedKey = ID.getID(hashedKeyBytes, XmlRpcDHTServer.this.idSizeInByte);
				int placeMark = 0;
				if (placeMarkBytes != null && placeMarkBytes.length > 0) {
					for (int i = 0; i < 4; i++)
						placeMark |= (placeMarkBytes[i] & 0xff) << ((3 - i) << 3);
				}

				// get
				Set<ValueInfo<String>> values = null;
				try {
					values = XmlRpcDHTServer.this.dht.get(hashedKey);
				}
				catch (RoutingException e) { /* ignore */ }

				if (values == null) {
					values = new HashSet<ValueInfo<String>>();
				}

				if (values.size() > maxVals || placeMark != 0) {
					Set<ValueInfo<String>> truncatedValues =
						new HashSet<ValueInfo<String>>(maxVals);
					int i = 0;
					for (ValueInfo<String> v: values) {
						if (i >= placeMark + maxVals)
							break;

						if (i >= placeMark) {
							truncatedValues.add(v);
						}

						i++;
					}

					if (placeMark + maxVals >= values.size()) {
						placeMarkBytes = new byte[0];
					}
					else {
						// next placemark
						placeMark = i;
						placeMarkBytes = new byte[4];
						placeMarkBytes[0] = (byte)(placeMark >>> 24);
						placeMarkBytes[1] = (byte)(placeMark >>> 16);
						placeMarkBytes[2] = (byte)(placeMark >>> 8);
						placeMarkBytes[3] = (byte)placeMark;
					}

					values = truncatedValues;
				}

				Object[] ret = new Object[2];
				ret[1] = placeMarkBytes;

				if (methodName.equals("get")) {
					byte[][] vals = new byte[values.size()][];
					ret[0] = vals;

					int i = 0;
					for (ValueInfo<String> v: values) {
						try {
							vals[i] = v.getValue().toString().getBytes(XmlRpcDHTServer.ENCODING);
						}
						catch (UnsupportedEncodingException e) {
							// NOTREACHED
						}

						i++;
					}
				}
				else {	// "get_details"
					Object[][] vals = new Object[values.size()][];
					ret[0] = vals;

					int i = 0;
					for (ValueInfo<String> v: values) {
						vals[i] = new Object[4];

						try {
							vals[i][0] = v.getValue().toString().getBytes(XmlRpcDHTServer.ENCODING);
						}
						catch (UnsupportedEncodingException e) {
							// NOTREACHED
						}
						vals[i][1] = (Integer)(int)(v.getTTL() / 1000L);
						vals[i][2] = "SHA";
						ByteArray sec = v.getHashedSecret();
						vals[i][3] = (sec != null ? sec.getBytes() : new byte[20]);
							// 20 is the length of a hash by SHA1

						i++;
					}
				}

				return ret;
			}
			else if (methodName.equals("put_removable") || methodName.equals("put")) {
				boolean put_removable = methodName.equals("put_removable");

				if (numParams != (put_removable ? 6 : 4)) {
					throw new XmlRpcException("Invalid number of params: " + numParams);
				}

				// parse arguments
				byte[] hashedKeyBytes = (byte[])req.getParameter(0);
				byte[] plainValueBytes = (byte[])req.getParameter(1);
				int ttl;
				String hashAlgo = null;
				byte[] hashedSecretBytes = null;
				if (put_removable) {
					hashAlgo = (String)req.getParameter(2);
					hashedSecretBytes = (byte[])req.getParameter(3);
					ttl = (Integer)req.getParameter(4);
				}
				else {
					ttl = (Integer)req.getParameter(2);
				}

				ID hashedKey = ID.getID(hashedKeyBytes, XmlRpcDHTServer.this.idSizeInByte);
				String value = null;
				try {
					value = new String(plainValueBytes, XmlRpcDHTServer.ENCODING);
				}
				catch (UnsupportedEncodingException e) {
					// NOTREACHED
				}
				ByteArray hashedSecret = null;
				if (put_removable) {
					if ("SHA".equals(hashAlgo)) {
						hashedSecret = new ByteArray(hashedSecretBytes);
					}
				}

				// put
				try {
					if (put_removable) {
						XmlRpcDHTServer.this.dht.setHashedSecretForPut(hashedSecret);
					}
					else {
						XmlRpcDHTServer.this.dht.setHashedSecretForPut(null);
					}

					XmlRpcDHTServer.this.dht.setTTLForPut(ttl * 1000);
					XmlRpcDHTServer.this.dht.put(hashedKey, value);
				}
				catch (Exception e) {
					System.err.println("An Exception thrown during putting:");
					e.printStackTrace();

					return 2;	// try again
				}

				return 0;
			}
			else if (methodName.equals("rm")) {
				if (numParams != 6) {
					throw new XmlRpcException("Invalid number of params: " + numParams);
				}

				// parse arguments
				byte[] hashedKeyBytes = (byte[])req.getParameter(0);
				byte[] valueHashBytes = (byte[])req.getParameter(1);
				String hashAlgo = (String)req.getParameter(2);
				byte[] secretBytes = (byte[])req.getParameter(3);
				int ttl = (Integer)req.getParameter(4);

				ID hashedKey = ID.getID(hashedKeyBytes, XmlRpcDHTServer.this.idSizeInByte);
				ID valueHash = ID.getID(valueHashBytes, XmlRpcDHTServer.this.idSizeInByte);
				ByteArray hashedSecret = null;
				if ("SHA".equals(hashAlgo)) {
					ByteArray plainSecret = new ByteArray(secretBytes);
					hashedSecret = plainSecret.hashWithSHA1();
				}

				// remove
				ID[] valueHashArray = new ID[1];
				valueHashArray[0] = valueHash;

				try {
					XmlRpcDHTServer.this.dht.remove(hashedKey, valueHashArray, hashedSecret);
				}
				catch (RoutingException e) { /* ignore */ }

				return 0;
			}
			else {
				throw new XmlRpcException("No such method: " + methodName);
			}
		}
	}
}
