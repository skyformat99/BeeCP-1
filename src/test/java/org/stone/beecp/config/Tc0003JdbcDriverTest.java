/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * Copyright(C) Chris2018998,All rights reserved.
 *
 * Project owner contact:Chris2018998@tom.com.
 *
 * Project Licensed under Apache License v2.0
 */
package org.stone.beecp.config;

import junit.framework.TestCase;
import org.junit.Assert;
import org.stone.beecp.BeeDataSourceConfig;
import org.stone.beecp.BeeDataSourceConfigException;

import java.sql.SQLException;

import static org.stone.beecp.config.DsConfigFactory.JDBC_DRIVER;
import static org.stone.beecp.config.DsConfigFactory.JDBC_URL;

/**
 * @author Chris Liao
 */

public class Tc0003JdbcDriverTest extends TestCase {

    public void testNoSuitableDriver() {
        try {
            BeeDataSourceConfig config = DsConfigFactory.createEmpty();
            config.setUrl("jdbc:beecp://localhost/testdb");
            config.check();
        } catch (SQLException e) {//thrown from DriverManager
            String message = e.getMessage();
            Assert.assertTrue(message != null && message.contains("No suitable driver"));
        }
    }

    public void testUrlNotMatchDriver() throws Exception {
        try {
            BeeDataSourceConfig config = DsConfigFactory.createEmpty();
            config.setJdbcUrl("Test:" + JDBC_URL);
            config.setDriverClassName(JDBC_DRIVER);
            config.check();
        } catch (BeeDataSourceConfigException e) {//thrown from Config.check()
            String message = e.getMessage();
            Assert.assertTrue(message != null && message.contains("can not match configured driver"));
        }
    }
}