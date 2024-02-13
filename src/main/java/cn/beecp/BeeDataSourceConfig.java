/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * Copyright(C) Chris2018998,All rights reserved.
 *
 * Project owner contact:Chris2018998@tom.com.
 *
 * Project Licensed under GNU Lesser General Public License v2.1.
 */
package cn.beecp;

import cn.beecp.pool.ConnectionFactoryByDriver;
import cn.beecp.pool.ConnectionFactoryByDriverDs;
import cn.beecp.pool.FastConnectionPool;
import cn.beecp.pool.XaConnectionFactoryByDriverDs;

import javax.sql.DataSource;
import javax.sql.XADataSource;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static cn.beecp.TransactionIsolation.TRANS_LEVEL_CODE_LIST;
import static cn.beecp.pool.ConnectionPoolStatics.*;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Configuration of bee dataSource
 *
 * @author Chris Liao
 * @version 1.0
 */
public class BeeDataSourceConfig implements BeeDataSourceConfigJmxBean {
    //index on generating default pool name,atomic value starts with 1
    private static final AtomicInteger PoolNameIndex = new AtomicInteger(1);

    //properties map apply in jdbc driver to connect db
    private final Map<String, Object> connectProperties = new HashMap<String, Object>(2);
    //jdbc user name
    private String username;
    //jdbc password
    private String password;
    //jdbc link url
    private String jdbcUrl;
    //jdbc driver class name
    private String driverClassName;
    //a default name generated on pool starting up when this value is null or empty
    private String poolName;
    //fair boolean indicator applied to pool semaphore
    private boolean fairMode;
    //creation size of connections on starting up
    private int initialSize;
    //indicator to create initial connections by synchronization mode
    private boolean asyncCreateInitConnection;
    //max reachable size of pooled connections
    private int maxActive = Math.min(Math.max(10, NCPU), 50);
    //max permit size of pool semaphore
    private int borrowSemaphoreSize = Math.min(this.maxActive / 2, NCPU);
    //milliseconds:max wait time of a borrower to get a idle connection from pool,if not get one,then throws an exception
    private long maxWait = SECONDS.toMillis(8);
    //milliseconds:max idle time of pooled connections,if time reached and not be borrowed out,then be removed from pool
    private long idleTimeout = MINUTES.toMillis(3);
    //milliseconds:max hold time and not be active on borrowed connections,which may be force released to pool if this value greater than zero
    private long holdTimeout;
    //a test sql to validate borrowed connections whether be alive
    private String validTestSql = "SELECT 1";
    //seconds:max wait time to get validation result on testing connections
    private int validTestTimeout = 3;
    //milliseconds:max gap time between last activity time and borrowed time point,if less this gap value,assume connections in alive state,otherwise test them
    private long validAssumeTime = 500L;
    //milliseconds:working interval time of a timer thread to scan idle-timeout connections and hold-timeout connections
    private long timerCheckInterval = MINUTES.toMillis(3);
    //indicator to whether force close using connections when pool clears connections
    private boolean forceCloseUsingOnClear;
    //milliseconds:delay time for next loop clearance in pool when exits using connections and configured item<config>forceCloseUsingOnClear</config> is false
    private long delayTimeForNextClear = 3000L;
    //store some fatal sql exception code(@see field vendorCode in SQLException class),if one of these codes contains in SQLException thrown from borrowed out connections,then remove them from pool
    private List<Integer> sqlExceptionCodeList;
    //store some fatal sql exception state(@see field SQLState in SQLException class),if one of these state contains in SQLException thrown from borrowed out connections,then remove them from pool
    private List<String> sqlExceptionStateList;

    //default value to catalog<code>Connection.setCatalog(String)</code>
    private String defaultCatalog;
    //default value to schema<code>Connection.setSchema(String)</code>
    private String defaultSchema;
    //default value to readOnly<code>Connection.setReadOnly(boolean)</code>
    private Boolean defaultReadOnly;
    //default value to autoCommit<code>Connection.setAutoCommit(boolean)</code>
    private Boolean defaultAutoCommit;
    //default value to transactionIsolation<code>Connection.setTransactionIsolation(int)</code>
    private Integer defaultTransactionIsolationCode;
    //description of default transactionIsolation level code
    private String defaultTransactionIsolationName;

    //enable indicator on using default catalog(connection property)
    private boolean enableDefaultOnCatalog = true;
    //enable indicator on using default schema(connection property)
    private boolean enableDefaultOnSchema = true;
    //enable indicator on using default readOnly(connection property)
    private boolean enableDefaultOnReadOnly = true;
    //enable indicator on using default autoCommit(connection property)
    private boolean enableDefaultOnAutoCommit = true;
    //enable indicator on using default transactionIsolation(connection property)
    private boolean enableDefaultOnTransactionIsolation = true;

    //invocation on <method>Connection.setSchema</method) regards as schema dirty
    private boolean enableFastDirtyOnSchema;
    //invocation on <method>Connection.setCatalog</method) regards as catalog dirty
    private boolean enableFastDirtyOnCatalog;

    /**
     * connection factory class,which must be implement one of the below four interfaces
     * 1:<class>RawConnectionFactory</class>
     * 2:<class>RawXaConnectionFactory</class>
     * 3:<class>DataSource</class>
     * 4:<class>XADataSource</class>
     */
    private Class connectionFactoryClass;
    //connection factory class name
    private String connectionFactoryClassName;
    //connection factory instance
    private Object connectionFactory;

    //encryption decoder class on jdbc link info
    private Class jdbcLinkInfoDecoderClass;
    //encryption decoder classname on jdbc link info
    private String jdbcLinkInfDecoderClassName;
    //pool implementation class name,if not be set,a default implementation applied in bee datasource
    private String poolImplementClassName = FastConnectionPool.class.getName();

    //indicator on whether registering jmx
    private boolean enableJmx;
    //indicator on whether printing configuration items when pool starting up
    private boolean printConfigInfo;
    //indicator on printing pool runtime log,this value can be changed by calling pool<method>setPrintRuntimeLog</method>
    private boolean printRuntimeLog;

    //****************************************************************************************************************//
    //                                     1: constructors(5)                                                         //
    //****************************************************************************************************************//
    public BeeDataSourceConfig() {
    }

    //read configuration from properties file
    public BeeDataSourceConfig(File propertiesFile) {
        loadFromPropertiesFile(propertiesFile);
    }

    //read configuration from properties file
    public BeeDataSourceConfig(String propertiesFileName) {
        loadFromPropertiesFile(propertiesFileName);
    }

    //read configuration from properties
    public BeeDataSourceConfig(Properties configProperties) {
        loadFromProperties(configProperties);
    }

    public BeeDataSourceConfig(String driver, String url, String user, String password) {
        this.jdbcUrl = trimString(url);
        this.username = trimString(user);
        this.password = trimString(password);
        this.driverClassName = trimString(driver);
    }

    //****************************************************************************************************************//
    //                                     2: JDBC link configuration methods(10)                                     //
    //****************************************************************************************************************//
    public String getUsername() {
        return this.username;
    }

    public void setUsername(String username) {
        this.username = trimString(username);
    }

    public String getPassword() {
        return this.password;
    }

    public void setPassword(String password) {
        this.password = trimString(password);
    }

    public String getUrl() {
        return this.jdbcUrl;
    }

    public void setUrl(String jdbcUrl) {
        this.jdbcUrl = trimString(jdbcUrl);
    }

    public String getJdbcUrl() {
        return this.jdbcUrl;
    }

    public void setJdbcUrl(String jdbcUrl) {
        this.jdbcUrl = trimString(jdbcUrl);
    }

    public String getDriverClassName() {
        return this.driverClassName;
    }

    public void setDriverClassName(String driverClassName) {
        this.driverClassName = trimString(driverClassName);
    }


    //****************************************************************************************************************//
    //                                3: configuration about pool inner control(30)                                   //
    //****************************************************************************************************************//
    public String getPoolName() {
        return this.poolName;
    }

    public void setPoolName(String poolName) {
        this.poolName = trimString(poolName);
    }

    public boolean isFairMode() {
        return this.fairMode;
    }

    public void setFairMode(boolean fairMode) {
        this.fairMode = fairMode;
    }

    public int getInitialSize() {
        return this.initialSize;
    }

    public void setInitialSize(int initialSize) {
        if (initialSize >= 0) this.initialSize = initialSize;
    }

    public boolean isAsyncCreateInitConnection() {
        return asyncCreateInitConnection;
    }

    public void setAsyncCreateInitConnection(boolean asyncCreateInitConnection) {
        this.asyncCreateInitConnection = asyncCreateInitConnection;
    }

    public int getMaxActive() {
        return this.maxActive;
    }

    public void setMaxActive(int maxActive) {
        if (maxActive > 0) {
            this.maxActive = maxActive;
            //fix issue:#19 Chris-2020-08-16 begin
            this.borrowSemaphoreSize = maxActive > 1 ? Math.min(maxActive / 2, NCPU) : 1;
            //fix issue:#19 Chris-2020-08-16 end
        }
    }

    public int getBorrowSemaphoreSize() {
        return this.borrowSemaphoreSize;
    }

    public void setBorrowSemaphoreSize(int borrowSemaphoreSize) {
        if (borrowSemaphoreSize > 0) this.borrowSemaphoreSize = borrowSemaphoreSize;
    }

    public long getMaxWait() {
        return this.maxWait;
    }

    public void setMaxWait(long maxWait) {
        if (maxWait > 0L) this.maxWait = maxWait;
    }

    public long getIdleTimeout() {
        return this.idleTimeout;
    }

    public void setIdleTimeout(long idleTimeout) {
        if (idleTimeout > 0L) this.idleTimeout = idleTimeout;
    }

    public long getHoldTimeout() {
        return this.holdTimeout;
    }

    public void setHoldTimeout(long holdTimeout) {
        this.holdTimeout = holdTimeout;
    }

    public String getValidTestSql() {
        return this.validTestSql;
    }

    public void setValidTestSql(String validTestSql) {
        if (!isBlank(validTestSql)) this.validTestSql = trimString(validTestSql);
    }

    public int getValidTestTimeout() {
        return this.validTestTimeout;
    }

    public void setValidTestTimeout(int validTestTimeout) {
        if (validTestTimeout >= 0) this.validTestTimeout = validTestTimeout;
    }

    public long getValidAssumeTime() {
        return this.validAssumeTime;
    }

    public void setValidAssumeTime(long validAssumeTime) {
        if (validAssumeTime >= 0L) this.validAssumeTime = validAssumeTime;
    }

    public long getTimerCheckInterval() {
        return this.timerCheckInterval;
    }

    public void setTimerCheckInterval(long timerCheckInterval) {
        if (timerCheckInterval > 0L) this.timerCheckInterval = timerCheckInterval;
    }

    public boolean isForceCloseUsingOnClear() {
        return this.forceCloseUsingOnClear;
    }

    public void setForceCloseUsingOnClear(boolean forceCloseUsingOnClear) {
        this.forceCloseUsingOnClear = forceCloseUsingOnClear;
    }

    public long getDelayTimeForNextClear() {
        return this.delayTimeForNextClear;
    }

    public void setDelayTimeForNextClear(long delayTimeForNextClear) {
        if (delayTimeForNextClear >= 0L) this.delayTimeForNextClear = delayTimeForNextClear;
    }

    public List<Integer> getSqlExceptionCodeList() {
        return sqlExceptionCodeList;
    }

    public void addSqlExceptionCode(int code) {
        if (sqlExceptionCodeList == null) sqlExceptionCodeList = new ArrayList<Integer>(1);
        this.sqlExceptionCodeList.add(code);
    }

    public void removeSqlExceptionCode(int code) {
        if (sqlExceptionCodeList != null) this.sqlExceptionCodeList.remove(code);
    }

    public List<String> getSqlExceptionStateList() {
        return sqlExceptionStateList;
    }

    public void addSqlExceptionState(String state) {
        if (sqlExceptionStateList == null) sqlExceptionStateList = new ArrayList<String>(1);
        this.sqlExceptionStateList.add(state);
    }

    public void removeSqlExceptionState(String state) {
        if (sqlExceptionStateList != null) this.sqlExceptionStateList.remove(state);
    }

    public String getPoolImplementClassName() {
        return this.poolImplementClassName;
    }

    public void setPoolImplementClassName(String poolImplementClassName) {
        if (!isBlank(poolImplementClassName)) this.poolImplementClassName = trimString(poolImplementClassName);
    }

    public boolean isEnableJmx() {
        return this.enableJmx;
    }

    public void setEnableJmx(boolean enableJmx) {
        this.enableJmx = enableJmx;
    }

    public boolean isPrintConfigInfo() {
        return this.printConfigInfo;
    }

    public void setPrintConfigInfo(boolean printConfigInfo) {
        this.printConfigInfo = printConfigInfo;
    }

    public boolean isPrintRuntimeLog() {
        return this.printRuntimeLog;
    }

    public void setPrintRuntimeLog(boolean printRuntimeLog) {
        this.printRuntimeLog = printRuntimeLog;
    }

    //****************************************************************************************************************//
    //                                     4: connection default value set methods(12)                                //
    //****************************************************************************************************************//
    public String getDefaultCatalog() {
        return this.defaultCatalog;
    }

    public void setDefaultCatalog(String defaultCatalog) {
        this.defaultCatalog = trimString(defaultCatalog);
    }

    public String getDefaultSchema() {
        return this.defaultSchema;
    }

    public void setDefaultSchema(String defaultSchema) {
        this.defaultSchema = trimString(defaultSchema);
    }

    public Boolean isDefaultReadOnly() {
        return this.defaultReadOnly;
    }

    public void setDefaultReadOnly(Boolean defaultReadOnly) {
        this.defaultReadOnly = defaultReadOnly;
    }

    public Boolean isDefaultAutoCommit() {
        return this.defaultAutoCommit;
    }

    public void setDefaultAutoCommit(Boolean defaultAutoCommit) {
        this.defaultAutoCommit = defaultAutoCommit;
    }

    public Integer getDefaultTransactionIsolationCode() {
        return this.defaultTransactionIsolationCode;
    }

    public void setDefaultTransactionIsolationCode(Integer transactionIsolationCode) {
        this.defaultTransactionIsolationCode = transactionIsolationCode;//support Informix jdbc
    }

    public String getDefaultTransactionIsolationName() {
        return this.defaultTransactionIsolationName;
    }

    public void setDefaultTransactionIsolationName(String transactionIsolationName) {
        String transactionIsolationNameTemp = trimString(transactionIsolationName);
        this.defaultTransactionIsolationCode = TransactionIsolation.getTransactionIsolationCode(transactionIsolationNameTemp);
        if (this.defaultTransactionIsolationCode != null) {
            defaultTransactionIsolationName = transactionIsolationNameTemp;
        } else {
            throw new BeeDataSourceConfigException("Invalid transaction isolation name:" + transactionIsolationNameTemp + ", value is one of[" + TRANS_LEVEL_CODE_LIST + "]");
        }
    }

    //****************************************************************************************************************//
    //                                     5: connection default value set Indicator methods(10)                      //
    //****************************************************************************************************************//
    public boolean isEnableDefaultOnCatalog() {
        return enableDefaultOnCatalog;
    }

    public void setEnableDefaultOnCatalog(boolean enableDefaultOnCatalog) {
        this.enableDefaultOnCatalog = enableDefaultOnCatalog;
    }

    public boolean isEnableDefaultOnSchema() {
        return enableDefaultOnSchema;
    }

    public void setEnableDefaultOnSchema(boolean enableDefaultOnSchema) {
        this.enableDefaultOnSchema = enableDefaultOnSchema;
    }

    public boolean isEnableDefaultOnReadOnly() {
        return enableDefaultOnReadOnly;
    }

    public void setEnableDefaultOnReadOnly(boolean enableDefaultOnReadOnly) {
        this.enableDefaultOnReadOnly = enableDefaultOnReadOnly;
    }

    public boolean isEnableDefaultOnAutoCommit() {
        return enableDefaultOnAutoCommit;
    }

    public void setEnableDefaultOnAutoCommit(boolean enableDefaultOnAutoCommit) {
        this.enableDefaultOnAutoCommit = enableDefaultOnAutoCommit;
    }

    public boolean isEnableDefaultOnTransactionIsolation() {
        return enableDefaultOnTransactionIsolation;
    }

    public void setEnableDefaultOnTransactionIsolation(boolean enableDefaultOnTransactionIsolation) {
        this.enableDefaultOnTransactionIsolation = enableDefaultOnTransactionIsolation;
    }

    public boolean isEnableFastDirtyOnSchema() {
        return enableFastDirtyOnSchema;
    }

    public void setEnableFastDirtyOnSchema(boolean enableFastDirtyOnSchema) {
        this.enableFastDirtyOnSchema = enableFastDirtyOnSchema;
    }

    public boolean isEnableFastDirtyOnCatalog() {
        return enableFastDirtyOnCatalog;
    }

    public void setEnableFastDirtyOnCatalog(boolean enableFastDirtyOnCatalog) {
        this.enableFastDirtyOnCatalog = enableFastDirtyOnCatalog;
    }

    //****************************************************************************************************************//
    //                                    6: connection factory class set methods(12)                                 //
    //****************************************************************************************************************//
    public Object getConnectionFactory() {
        return this.connectionFactory;
    }

    //connection factory
    public void setRawConnectionFactory(RawConnectionFactory factory) {
        this.connectionFactory = factory;
    }

    public void setRawXaConnectionFactory(RawXaConnectionFactory factory) {
        this.connectionFactory = factory;
    }

    public Class getConnectionFactoryClass() {
        return this.connectionFactoryClass;
    }

    public void setConnectionFactoryClass(Class connectionFactoryClass) {
        this.connectionFactoryClass = connectionFactoryClass;
    }

    public String getConnectionFactoryClassName() {
        return this.connectionFactoryClassName;
    }

    public void setConnectionFactoryClassName(String connectionFactoryClassName) {
        this.connectionFactoryClassName = trimString(connectionFactoryClassName);
    }

    public Class getJdbcLinkInfoDecoderClass() {
        return this.jdbcLinkInfoDecoderClass;
    }

    public void setJdbcLinkInfoDecoderClass(Class jdbcLinkInfoDecoderClass) {
        this.jdbcLinkInfoDecoderClass = jdbcLinkInfoDecoderClass;
    }

    public String getJdbcLinkInfDecoderClassName() {
        return this.jdbcLinkInfDecoderClassName;
    }

    public void setJdbcLinkInfDecoderClassName(String jdbcLinkInfDecoderClassName) {
        this.jdbcLinkInfDecoderClassName = jdbcLinkInfDecoderClassName;
    }

    public void removeConnectProperty(String key) {
        if (!isBlank(key)) this.connectProperties.remove(key);
    }

    public void addConnectProperty(String key, Object value) {
        if (!isBlank(key) && value != null) this.connectProperties.put(key, value);
    }

    public void addConnectProperty(String connectPropertyText) {
        if (!isBlank(connectPropertyText)) {
            for (String attribute : connectPropertyText.split("&")) {
                String[] pair = attribute.split("=");
                if (pair.length == 2) {
                    this.addConnectProperty(pair[0].trim(), pair[1].trim());
                } else {
                    pair = attribute.split(":");
                    if (pair.length == 2) {
                        this.addConnectProperty(pair[0].trim(), pair[1].trim());
                    }
                }
            }
        }
    }

    //****************************************************************************************************************//
    //                                     7: properties configuration(3)                                             //
    //****************************************************************************************************************//
    public void loadFromPropertiesFile(String filename) {
        if (isBlank(filename))
            throw new IllegalArgumentException("Configuration properties file name can't be null or empty");
        this.loadFromPropertiesFile(new File(filename));
    }

    public void loadFromPropertiesFile(File file) {
        if (file == null) throw new IllegalArgumentException("Configuration properties file can't be null");
        if (!file.exists())
            throw new IllegalArgumentException("Configuration properties file not found:" + file.getAbsolutePath());
        if (!file.isFile()) throw new IllegalArgumentException("Target object is not a valid file");
        if (!file.getAbsolutePath().toLowerCase(Locale.US).endsWith(".properties"))
            throw new IllegalArgumentException("Target file is not a properties file");

        InputStream stream = null;
        try {
            stream = Files.newInputStream(Paths.get(file.toURI()));
            Properties configProperties = new Properties();
            configProperties.load(stream);

            this.loadFromProperties(configProperties);
        } catch (BeeDataSourceConfigException e) {
            throw e;
        } catch (Throwable e) {
            throw new BeeDataSourceConfigException("Failed to load configuration properties file", e);
        } finally {
            if (stream != null) try {
                stream.close();
            } catch (Throwable e) {
                CommonLog.warn("Failed to close inputStream of configuration properties file", e);
            }
        }
    }

    public void loadFromProperties(Properties configProperties) {
        if (configProperties == null || configProperties.isEmpty())
            throw new BeeDataSourceConfigException("Configuration properties can't be null or empty");

        //1:load configuration item values from outside properties
        synchronized (configProperties) {//synchronization mode
            Map<String, Object> setValueMap = new HashMap<String, Object>(configProperties.size());
            for (String propertyName : configProperties.stringPropertyNames()) {
                setValueMap.put(propertyName, configProperties.getProperty(propertyName));
            }

            //2:inject item value from map to this dataSource config object
            setPropertiesValue(this, setValueMap);

            //3:try to find 'connectProperties' config value and put to ds config object
            this.addConnectProperty(getPropertyValue(configProperties, CONFIG_CONNECT_PROP));
            String connectPropertiesSize = getPropertyValue(configProperties, CONFIG_CONNECT_PROP_SIZE);
            if (!isBlank(connectPropertiesSize)) {
                int size = Integer.parseInt(connectPropertiesSize.trim());
                for (int i = 1; i <= size; i++)//properties index begin with 1
                    this.addConnectProperty(getPropertyValue(configProperties, CONFIG_CONNECT_PROP_KEY_PREFIX + i));
            }
        }
    }


    //****************************************************************************************************************//
    //                                    8: configuration check and connection factory create methods(4)             //
    //****************************************************************************************************************//
    //check pool configuration
    public BeeDataSourceConfig check() throws SQLException {
        if (maxActive <= 0)
            throw new BeeDataSourceConfigException("maxActive must be greater than zero");
        if (initialSize < 0)
            throw new BeeDataSourceConfigException("initialSize must not be less than zero");
        if (initialSize > this.maxActive)
            throw new BeeDataSourceConfigException("initialSize must not be greater than maxActive");
        if (borrowSemaphoreSize <= 0)
            throw new BeeDataSourceConfigException("borrowSemaphoreSize must be greater than zero");
        //fix issue:#19 Chris-2020-08-16 begin
        //if (this.borrowConcurrentSize > maxActive)
        //throw new BeeDataSourceConfigException("Pool 'borrowConcurrentSize' must not be greater than pool max size");
        //fix issue:#19 Chris-2020-08-16 end
        if (idleTimeout <= 0L)
            throw new BeeDataSourceConfigException("idleTimeout must be greater than zero");
        if (holdTimeout < 0L)
            throw new BeeDataSourceConfigException("holdTimeout must be greater than zero");
        if (maxWait <= 0L)
            throw new BeeDataSourceConfigException("maxWait must be greater than zero");
        //fix issue:#1 The check of validationQuerySQL has logic problem. Chris-2019-05-01 begin
        //if (this.validationQuerySQL != null && validationQuerySQL.trim().length() == 0) {
        if (isBlank(validTestSql))
            throw new BeeDataSourceConfigException("validTestSql can't be null or empty");
        if (!validTestSql.toUpperCase(Locale.US).startsWith("SELECT ")) {
            //fix issue:#1 The check of validationQuerySQL has logic problem. Chris-2019-05-01 end
            throw new BeeDataSourceConfigException("validTestSql must be start with 'select '");
        }

        Object connectionFactory = createConnectionFactory();
        BeeDataSourceConfig checkedConfig = new BeeDataSourceConfig();
        copyTo(checkedConfig);

        //set some factories to config
        checkedConfig.connectionFactory = connectionFactory;
        if (isBlank(checkedConfig.poolName)) checkedConfig.poolName = "FastPool-" + PoolNameIndex.getAndIncrement();

        return checkedConfig;
    }

    //copy configuration info to other from local
    void copyTo(BeeDataSourceConfig config) {
        //1:primitive type copy
        String fieldName = "";
        try {
            for (Field field : BeeDataSourceConfig.class.getDeclaredFields()) {
                fieldName = field.getName();
                if (!Modifier.isFinal(field.getModifiers()) && !Modifier.isStatic(field.getModifiers()) && !"connectProperties".equals(fieldName)
                        && !"sqlExceptionCodeList".equals(fieldName) && !"sqlExceptionStateList".equals(fieldName)) {
                    Object fieldValue = field.get(this);
                    if (this.printConfigInfo) CommonLog.info("{}.{}={}", this.poolName, fieldName, fieldValue);
                    field.set(config, fieldValue);
                }
            }
        } catch (Throwable e) {
            throw new BeeDataSourceConfigException("Failed to copy field[" + fieldName + "]", e);
        }

        //2:copy  'connectProperties'
        for (Map.Entry<String, Object> entry : this.connectProperties.entrySet()) {
            if (this.printConfigInfo)
                CommonLog.info("{}.connectProperties.{}={}", this.poolName, entry.getKey(), entry.getValue());
            config.addConnectProperty(entry.getKey(), entry.getValue());
        }

        //3:copy  'sqlExceptionCodeList'
        if (this.sqlExceptionCodeList != null && !sqlExceptionCodeList.isEmpty()) {
            config.sqlExceptionCodeList = new ArrayList<Integer>(sqlExceptionCodeList);
        }

        //4:copy  'sqlExceptionStateList'
        if (this.sqlExceptionStateList != null && !sqlExceptionStateList.isEmpty()) {
            config.sqlExceptionStateList = new ArrayList<String>(sqlExceptionStateList);
        }
    }

    //create BeeJdbcLinkInfoDecoder instance
    private BeeJdbcLinkInfoDecoder createJdbcLinkInfoDecoder() {
        BeeJdbcLinkInfoDecoder jdbcLinkInfoDecoder = null;
        Class<?> jdbcLinkInfoDecoderClass = this.jdbcLinkInfoDecoderClass;
        if (jdbcLinkInfoDecoderClass == null && !isBlank(this.jdbcLinkInfDecoderClassName)) {
            try {
                jdbcLinkInfoDecoderClass = Class.forName(this.jdbcLinkInfDecoderClassName);
            } catch (Throwable e) {
                throw new BeeDataSourceConfigException("Failed to create jdbc link info decoder by class:" + this.jdbcLinkInfDecoderClassName, e);
            }
        }

        if (jdbcLinkInfoDecoderClass != null) {
            try {
                jdbcLinkInfoDecoder = (BeeJdbcLinkInfoDecoder) createClassInstance(jdbcLinkInfoDecoderClass, BeeJdbcLinkInfoDecoder.class, "jdbc link info decoder");
            } catch (Throwable e) {
                throw new BeeDataSourceConfigException("Failed to instantiate jdbc link info decoder class:" + jdbcLinkInfoDecoderClass.getName(), e);
            }
        }
        return jdbcLinkInfoDecoder;
    }

    //create Connection factory
    private Object createConnectionFactory() throws SQLException {
        //step1:if exists object factory,then return it
        if (this.connectionFactory != null) return this.connectionFactory;

        //step2:create connection factory by driver
        BeeJdbcLinkInfoDecoder jdbcLinkInfoDecoder = this.createJdbcLinkInfoDecoder();
        if (this.connectionFactoryClass == null && isBlank(this.connectionFactoryClassName)) {
            //step2.1: prepare jdbc url
            String url = this.jdbcUrl;//url must not be null
            if (isBlank(url)) url = System.getProperty("beecp.url", null);
            if (isBlank(url)) url = System.getProperty("beecp.jdbcUrl", null);
            if (isBlank(url)) throw new BeeDataSourceConfigException("jdbcUrl can't be null");
            if (jdbcLinkInfoDecoder != null) url = jdbcLinkInfoDecoder.decodeUrl(url);//decode url

            //step2.2: prepare jdbc driver
            Driver connectDriver = null;
            if (!isBlank(this.driverClassName))
                connectDriver = loadDriver(this.driverClassName);
            if (connectDriver == null)//try to resolve matched driver from DriverManager by url
                connectDriver = DriverManager.getDriver(url);
            if (connectDriver == null)
                throw new BeeDataSourceConfigException("Not found driver by url:" + url);
            if (!connectDriver.acceptsURL(url))
                throw new BeeDataSourceConfigException("jdbcUrl(" + url + ")can not match driver:" + connectDriver.getClass().getName());

            //step2.3: create a new jdbc connecting properties object and set default value from local properties
            Properties configProperties = new Properties();
            configProperties.putAll(this.connectProperties);//copy local properties

            //step2.4: set user name and password
            String userName = configProperties.getProperty("user");//read from connectProperties firstly
            String password = configProperties.getProperty("password");//read from connectProperties firstly
            if (isBlank(userName)) {
                userName = this.username;//read value from local member field
                if (isBlank(userName))
                    userName = System.getProperty("beecp.user", null);//support reading from system.properties
                if (!isBlank(userName)) configProperties.setProperty("user", userName);
            }
            if (isBlank(password)) {
                password = this.password;//read value from local member field
                if (isBlank(password))
                    password = System.getProperty("beecp.password", null);//support reading from system.properties
                if (!isBlank(password)) configProperties.setProperty("password", password);
            }

            //step2.5: decode user name and password
            if (jdbcLinkInfoDecoder != null) {//execute the decoder and reset user name and password
                if (!isBlank(userName))
                    configProperties.setProperty("user", jdbcLinkInfoDecoder.decodeUsername(userName));
                if (!isBlank(password))
                    configProperties.setProperty("password", jdbcLinkInfoDecoder.decodePassword(password));
            }

            //step2.6: create a new connection factory by a driver and jdbc link info
            return new ConnectionFactoryByDriver(url, connectDriver, configProperties);
        } else {//step3:create connection factory by connection factory class
            try {
                //1:load connection factory class by class name
                Class<?> conFactClass = this.connectionFactoryClass != null ? this.connectionFactoryClass : Class.forName(this.connectionFactoryClassName);

                //2:check connection factory class
                Class[] parentClasses = {RawConnectionFactory.class, RawXaConnectionFactory.class, DataSource.class, XADataSource.class};

                //3:create connection factory instance
                Object factory = createClassInstance(conFactClass, parentClasses, "connection factory");

                //4:copy properties to value map(inject to dataSource or factory)
                Map<String, Object> propertyValueMap = new HashMap<String, Object>(this.connectProperties);//copy

                //5: try to find out jdbc url
                String url = (String) propertyValueMap.get("url");//read from connectProperties firstly
                if (isBlank(url)) url = (String) propertyValueMap.get("URL");
                if (isBlank(url)) url = (String) propertyValueMap.get("jdbcUrl");
                if (isBlank(url)) url = this.jdbcUrl;
                if (isBlank(url)) url = System.getProperty("beecp.url", null);
                if (isBlank(url)) url = System.getProperty("beecp.URL", null);
                if (isBlank(url)) url = System.getProperty("beecp.jdbcUrl", null);

                //6: try to resolve jdbc user
                String userName = (String) propertyValueMap.get("user");//read from connectProperties firstly
                if (isBlank(userName)) {
                    userName = this.username;
                    if (isBlank(userName)) userName = System.getProperty("beecp.user", null);
                    propertyValueMap.put("user", userName);
                }

                //7: try to resolve jdbc password
                String password = (String) propertyValueMap.get("password");//read from connectProperties firstly
                if (isBlank(password)) {
                    password = this.password;
                    if (isBlank(password)) password = System.getProperty("beecp.password", null);
                    propertyValueMap.put("password", password);
                }

                //8: decode jdbc link info
                if (jdbcLinkInfoDecoder != null) {//then execute the decoder
                    if (!isBlank(url))
                        url = jdbcLinkInfoDecoder.decodeUrl(url);//reset it to map at the following step
                    if (!isBlank(userName))
                        propertyValueMap.put("user", jdbcLinkInfoDecoder.decodeUsername(userName));
                    if (!isBlank(password))
                        propertyValueMap.put("password", jdbcLinkInfoDecoder.decodePassword(password));
                }

                //9: reset jdbc url to the target map
                if (!isBlank(url)) {//reset url to properties map with three key names
                    propertyValueMap.put("url", url);
                    propertyValueMap.put("URL", url);
                    propertyValueMap.put("jdbcUrl", url);
                }

                //10: inject properties to connection factory or dataSource
                setPropertiesValue(factory, propertyValueMap);

                //11: return RawConnectionFactory or RawXaConnectionFactory
                if (factory instanceof RawConnectionFactory || factory instanceof RawXaConnectionFactory) {
                    return factory;
                } else if (factory instanceof XADataSource) {
                    return new XaConnectionFactoryByDriverDs((XADataSource) factory, userName, password);
                } else if (factory instanceof DataSource) {
                    return new ConnectionFactoryByDriverDs((DataSource) factory, userName, password);
                } else {
                    throw new BeeDataSourceConfigException("Error connection factory type:" + this.connectionFactoryClassName);
                }
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new BeeDataSourceConfigException("Failed to create connection factory by class:" + this.connectionFactoryClassName, e);
            }
        }
    }
}

