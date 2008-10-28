package com.meidusa.amoeba.oracle.server;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import org.apache.log4j.Logger;

import com.meidusa.amoeba.config.ConfigUtil;
import com.meidusa.amoeba.context.ProxyRuntimeContext;
import com.meidusa.amoeba.log4j.DOMConfigurator;
import com.meidusa.amoeba.net.ConnectionManager;
import com.meidusa.amoeba.net.FrontendConnectionFactory;
import com.meidusa.amoeba.oracle.context.OracleProxyRuntimeContext;
import com.meidusa.amoeba.oracle.net.OracleClientConnectionFactory;
import com.meidusa.amoeba.oracle.net.OracleClientConnectionManager;
import com.meidusa.amoeba.util.Reporter;
import com.meidusa.amoeba.util.StringUtil;

public class OracleProxyServer {

    private final static String          serverName      = "Oracle Proxy Server";

    private static Logger                projectLog      = Logger.getLogger(OracleProxyServer.class);
    private static Logger                repoterLog      = Logger.getLogger("report");

    /** Used to generate "state of server" reports. */
    protected static ArrayList<Reporter> reporters       = new ArrayList<Reporter>();
    /** The time at which the server was started. */
    protected static long                serverStartTime = System.currentTimeMillis();

    /** The last time at which {@link #generateReport} was run. */
    protected static long                lastReportStamp = serverStartTime;

    public static void registerReporter(Reporter reporter) {
        reporters.add(reporter);
    }

    /**
     * Generates a report for all system services registered as a {@link Reporter}.
     */
    public static String generateReport() {
        return generateReport(System.currentTimeMillis(), false);
    }

    /**
     * Generates and logs a "state of server" report.
     */
    protected static String generateReport(long now, boolean reset) {
        long sinceLast = now - lastReportStamp;
        long uptime = now - serverStartTime;
        StringBuilder report = new StringBuilder(" State of server report:\n");

        report.append("- Uptime: ");
        report.append(StringUtil.intervalToString(uptime)).append("\n");
        report.append("- Report period: ");
        report.append(StringUtil.intervalToString(sinceLast)).append("\n");

        // report on the state of memory
        Runtime rt = Runtime.getRuntime();
        long total = rt.totalMemory(), max = rt.maxMemory();
        long used = (total - rt.freeMemory());
        report.append("- Memory: ").append(used / 1024).append("k used, ");
        report.append(total / 1024).append("k total, ");
        report.append(max / 1024).append("k max\n");

        for (int ii = 0; ii < reporters.size(); ii++) {
            Reporter rptr = reporters.get(ii);
            try {
                rptr.appendReport(report, now, sinceLast, reset, repoterLog.getLevel());
            } catch (Throwable t) {
                projectLog.error("Reporter choked [rptr=" + rptr + "].", t);
            }
        }

        // only reset the last report time if this is a periodic report
        if (reset) {
            lastReportStamp = now;
        }

        return report.toString();
    }

    protected static void logReport(String report) {
        repoterLog.info(report);
    }

    public static void main(String[] args) throws IOException {
        // load log4j config
        String log4jConf = ConfigUtil.filter(System.getProperty("log4j.conf", "${amoeba.home}/conf/log4j.xml"));
        File log = new File(log4jConf);
        if (log.exists() && log.isFile()) {
            DOMConfigurator.configureAndWatch(log.getAbsolutePath(), System.getProperties());
        }

        // load amoeba config
        Logger logger = Logger.getLogger(OracleProxyServer.class);
        ProxyRuntimeContext context = new OracleProxyRuntimeContext();
        String amoebaConf = ConfigUtil.filter(System.getProperty("amoeba.conf", "${amoeba.home}/conf/amoeba.xml"));
        File amoeba = new File(amoebaConf);
        if (amoebaConf == null || !amoeba.exists()) {
            if (logger.isInfoEnabled()) {
                logger.error("could not find config file:" + amoeba.getAbsolutePath());
            }
            System.exit(-1);
        } else {
            ProxyRuntimeContext.getInstance().init(amoeba.getAbsolutePath());
        }

        registerReporter(context);
        for (ConnectionManager connMgr : context.getConnectionManagerList().values()) {
            registerReporter(connMgr);
        }

        // config proxy server
        OracleClientConnectionManager proxy = new OracleClientConnectionManager(serverName,
                                                                                context.getConfig().getIpAddress(),
                                                                                context.getConfig().getPort());
        registerReporter(proxy);
        FrontendConnectionFactory factory = new OracleClientConnectionFactory();
        factory.setPassword(context.getConfig().getPassword());
        factory.setUser(context.getConfig().getUser());
        proxy.setConnectionFactory(factory);
        factory.setConnectionManager(proxy);
        /*
         * MysqlAuthenticator authen = new MysqlAuthenticator(); String accessConf =
         * System.getProperty("access.conf","${amoeba.home}/conf/access_list.conf"); accessConf =
         * ConfigUtil.filter(accessConf); authen.addAuthenticateFilter(new IPAccessController(accessConf));
         * oracleProxyServerconMger.setAuthenticator(authen);
         */

        proxy.setExecutor(context.getReadExecutor());
        proxy.start();

        new Thread() {

            {
                this.setDaemon(true);
            }

            public void run() {
                while (true) {
                    try {
                        Thread.sleep(10000L);
                    } catch (InterruptedException e) {
                    }
                    logReport(generateReport());
                }
            }
        }.start();
    }
}
