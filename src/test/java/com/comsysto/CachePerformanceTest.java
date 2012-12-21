package com.comsysto;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import etm.core.configuration.BasicEtmConfigurator;
import etm.core.configuration.EtmManager;
import etm.core.monitor.EtmMonitor;
import etm.core.monitor.EtmPoint;
import etm.core.renderer.SimpleTextRenderer;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import org.infinispan.manager.DefaultCacheManager;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * @author zutherb
 */
public class CachePerformanceTest {

    private static final EtmMonitor monitor = EtmManager.getEtmMonitor();

    private static final Logger logger = LoggerFactory.getLogger(CachePerformanceTest.class);
    private static final int SIZE = 100000;
    private static final int TEST_RUNS = 20;

    private static Map<Integer, Integer> javaMap;
    private static HazelcastInstance hazelcastInstance;
    private static DefaultCacheManager infinispanInstance;
    private static CacheManager ehCacheManager;

    @BeforeClass
    public static void setup() {
        Config config = new Config();
        Properties properties = new Properties();
        properties.setProperty("hazelcast.logging.type", "none");
        config.setProperties(properties);

        javaMap = new HashMap<Integer, Integer>();

        hazelcastInstance = Hazelcast.newHazelcastInstance(config);
        while (!hazelcastInstance.getLifecycleService().isRunning()){}

        infinispanInstance = new DefaultCacheManager();
        infinispanInstance.getCache();

        ehCacheManager =  CacheManager.getInstance();

        BasicEtmConfigurator.configure();
        monitor.start();
    }

    @Test
    public void testPerformance(){
        measure();
    }

    @AfterClass
    public static void tearDown(){
        hazelcastInstance.getLifecycleService().kill();
        monitor.stop();
    }

    private static void measure() {

        logger.info("Run Hash-Map Benchmark");
        new Runnable(CacheType.HashMap) {
            @Override
            protected void read(int i) {
                javaMap.get(i);
            }

            @Override
            protected void write(int i) {
                javaMap.put(i, i);
            }
        }.run();

        logger.info("Run EhCache Benchmark");
        new Runnable(CacheType.EhCache) {
            @Override
            protected void read(int i) {
                ehCacheManager.getCache("performance").get(i);
            }

            @Override
            protected void write(int i) {
                ehCacheManager.getCache("performance").put(new Element(i, i));
            }
        }.run();

        logger.info("Run Hazelcast Benchmark");
        new Runnable(CacheType.Hazelcast) {
            @Override
            protected void read(int i) {
                hazelcastInstance.getMap("performance").get(i);
            }

            @Override
            protected void write(int i) {
                hazelcastInstance.getMap("performance").put(i, i);
            }
        }.run();

        logger.info("Run Infinispan Benchmark");
        new Runnable(CacheType.Infinispan) {
            @Override
            protected void read(int i) {
                infinispanInstance.getCache().get(i);
            }

            @Override
            protected void write(int i) {
                infinispanInstance.getCache().put(i, i);
            }
        }.run();



        //visualize results
        monitor.render(new SimpleTextRenderer());
    }

    private static abstract class Runnable {

        private CacheType cacheType;

        public Runnable(CacheType cacheType){
            this.cacheType = cacheType;
        }

        public void run (){
            for (int run = 1; run <= TEST_RUNS; run++){
                write();
                read();
            }
        }

        private void write() {
            EtmPoint writePoint = monitor.createPoint(cacheType + ":write");
            for (int i = 0; i < SIZE; i++) {
                    write(i);
            }
            writePoint.collect();
        }

        private void read() {
            EtmPoint readPoint = monitor.createPoint(cacheType + ":read");
            for (int i = 0; i < SIZE; i++) {
                read(i);
            }
            readPoint.collect();
        }

        protected abstract void read(int i);

        protected abstract void write(int i);
    }



    private static enum CacheType {HashMap, EhCache, Hazelcast,Infinispan}
}
