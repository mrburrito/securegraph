package com.altamiracorp.securegraph.accumulo.helpers;

import com.altamiracorp.securegraph.accumulo.AccumuloGraphConfiguration;
import org.apache.accumulo.minicluster.MiniAccumuloCluster;
import org.apache.accumulo.minicluster.MiniAccumuloConfig;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class TestAccumuloCluster {
    private static final String ACCUMULO_USERNAME = "root";
    private static final String ACCUMULO_PASSWORD = "test";
    private static File tempDir;
    private static AccumuloGraphConfiguration config;
    private static MiniAccumuloCluster accumulo;

    public static AccumuloGraphConfiguration getConfig() {
        return config;
    }

    public static void start() throws IOException, InterruptedException {
        if (accumulo != null) {
            return;
        }

        tempDir = File.createTempFile("blueprints-accumulo-temp", Long.toString(System.nanoTime()));
        tempDir.delete();
        tempDir.mkdir();

        MiniAccumuloConfig miniAccumuloConfig = new MiniAccumuloConfig(tempDir, ACCUMULO_PASSWORD);
        accumulo = new MiniAccumuloCluster(miniAccumuloConfig);
        accumulo.start();

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                try {
                    TestAccumuloCluster.stop();
                } catch (Exception e) {
                    System.out.println("Failed to stop Accumulo test cluster");
                }
            }
        });

        Map configMap = new HashMap();
        configMap.put(AccumuloGraphConfiguration.ZOOKEEPER_SERVERS, accumulo.getZooKeepers());
        configMap.put(AccumuloGraphConfiguration.ACCUMULO_INSTANCE_NAME, accumulo.getInstanceName());
        configMap.put(AccumuloGraphConfiguration.ACCUMULO_USERNAME, ACCUMULO_USERNAME);
        configMap.put(AccumuloGraphConfiguration.ACCUMULO_PASSWORD, ACCUMULO_PASSWORD);
        configMap.put(AccumuloGraphConfiguration.AUTO_FLUSH, true);
        config = new AccumuloGraphConfiguration(configMap);
    }

    private static void stop() throws IOException, InterruptedException {
        if (accumulo != null) {
            accumulo.stop();
            accumulo = null;
        }
        tempDir.delete();
    }
}
