// This file is part of OpenTSDB.
// Copyright (C) 2010  StumbleUpon, Inc.
//
// This program is free software: you can redistribute it and/or modify it
// under the terms of the GNU Lesser General Public License as published by
// the Free Software Foundation, either version 3 of the License, or (at your
// option) any later version.  This program is distributed in the hope that it
// will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty
// of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser
// General Public License for more details.  You should have received a copy
// of the GNU Lesser General Public License along with this program.  If not,
// see <http://www.gnu.org/licenses/>.
package net.opentsdb.tools;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;

import net.opentsdb.BuildData;
import net.opentsdb.core.TSDB;
import net.opentsdb.tsd.PipelineFactory;

/**
 * Main class of the TSD, the Time Series Daemon.
 */
final class TSDMain {

  /** Prints usage and exits with the given retval. */
  static void usage(final ArgP argp, final String errmsg, final int retval) {
    System.err.println(errmsg);
    System.err.println("Usage: tsd --port=PORT\n"
      + "Starts the TSD, the Time Series Daemon");
    if (argp != null) {
      System.err.print(argp.usage());
    }
    System.exit(retval);
  }

  public static void main(String[] args) throws IOException {
    Logger log = LoggerFactory.getLogger(TSDMain.class);
    log.info("Starting.");
    log.info(BuildData.revisionString());
    log.info(BuildData.buildString());
    try {
      System.in.close();  // Release a FD we don't need.
    } catch (Exception e) {
      log.warn("Failed to close stdin", e);
    }

    final ArgP argp = new ArgP();
    CliOptions.addCommon(argp);
    argp.addOption("--port", "NUM", "TCP port to listen on.");
    args = CliOptions.parse(argp, args);
    if (args == null || !argp.has("--port")) {
      usage(argp, "Invalid usage.", 1);
    } else if (args.length != 0) {
      usage(argp, "Too many arguments.", 2);
    }
    args = null;  // free().

    final NioServerSocketChannelFactory factory =
        new NioServerSocketChannelFactory(Executors.newCachedThreadPool(),
                                          Executors.newCachedThreadPool());
    final TSDB tsdb = new TSDB(argp.get("--table", "tsdb"),
                               argp.get("--uidtable", "tsdb-uid"));
    final ServerBootstrap server = new ServerBootstrap(factory);

    server.setPipelineFactory(new PipelineFactory(tsdb));
    server.setOption("child.tcpNoDelay", true);
    server.setOption("child.keepAlive", true);
    server.setOption("reuseAddress", true);

    final InetSocketAddress addr =
      new InetSocketAddress(Integer.parseInt(argp.get("--port")));
    server.bind(addr);
    log.info("Ready to serve on " + addr);
    // The server is now running in separate threads, we can exit main.
  }

}