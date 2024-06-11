package org.stone.beecp.pool;

import junit.framework.TestCase;
import org.stone.beecp.BeeDataSource;
import org.stone.beecp.BeeDataSourceConfig;
import org.stone.beecp.config.DsConfigFactory;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;

import static org.stone.beecp.pool.ConnectionPoolStatics.oclose;

public class Tc0072ProxyResultSetFromDsMetaCloseTest extends TestCase {
    private BeeDataSource ds;

    public void setUp() {
        BeeDataSourceConfig config = DsConfigFactory.createDefault();
        config.setInitialSize(0);
        config.setAliveTestSql("SELECT 1 from dual");
        ds = new BeeDataSource(config);
    }

    public void tearDown() {
        ds.close();
    }

    public void testMetaData() throws Exception {
        Connection con = null;
        ResultSet rs;
        try {
            con = ds.getConnection();
            DatabaseMetaData dsMeta = con.getMetaData();
            rs = dsMeta.getTableTypes();
            oclose(rs);
        } finally {
            if (con != null) oclose(con);
        }
    }
}