/*
 * Copyright Chris2018998
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.beecp.test.mock;

import java.sql.*;

/**
 * @author Chris.Liao
 * @version 1.0
 */
public class MockStatement extends MockBase implements Statement {
    MockResultSet resultSet;
    MockConnection connection;
    private int queryTimeout;
    private int maxFieldSize;
    private int maxRows;
    private int fetchSize;

    public MockStatement(MockConnection connection) {
        this.connection = connection;
    }

    public Connection getConnection() throws SQLException {
        return connection;
    }

    public void close() throws SQLException {
        super.close();
        resultSet = null;
    }

    public ResultSet executeQuery(String sql) throws SQLException {
        resultSet = new MockResultSet(this);
        return resultSet;
    }

    public int executeUpdate(String sql) throws SQLException {
        return 1;
    }

    public int getMaxFieldSize() throws SQLException {
        return maxFieldSize;
    }

    public void setMaxFieldSize(int max) throws SQLException {
        maxFieldSize = max;
    }

    public int getMaxRows() throws SQLException {
        return maxRows;
    }

    public void setMaxRows(int max) throws SQLException {
        maxRows = max;
    }

    public void setEscapeProcessing(boolean enable) throws SQLException {
    }

    public int getQueryTimeout() throws SQLException {
        return queryTimeout;
    }

    public void setQueryTimeout(int seconds) throws SQLException {
        queryTimeout = seconds;
    }

    public void cancel() throws SQLException {
    }

    public SQLWarning getWarnings() throws SQLException {
        return null;
    }

    public void clearWarnings() throws SQLException {
    }

    public void setCursorName(String name) throws SQLException {
    }

    public boolean execute(String sql) throws SQLException {
        return true;
    }

    public ResultSet getResultSet() throws SQLException {
        return resultSet;
    }

    public int getUpdateCount() throws SQLException {
        return 1;
    }

    public boolean getMoreResults() throws SQLException {
        return true;
    }

    public int getFetchDirection() throws SQLException {
        return 1;
    }

    public void setFetchDirection(int direction) throws SQLException {
    }

    public int getFetchSize() throws SQLException {
        return fetchSize;
    }

    public void setFetchSize(int rows) throws SQLException {
        fetchSize = rows;
    }

    public int getResultSetConcurrency() throws SQLException {
        return 1;
    }

    public int getResultSetType() throws SQLException {
        return 1;
    }

    public void addBatch(String sql) throws SQLException {
    }

    public void clearBatch() throws SQLException {
    }

    public int[] executeBatch() throws SQLException {
        return null;
    }

    public boolean getMoreResults(int current) throws SQLException {
        return false;
    }

    public ResultSet getGeneratedKeys() throws SQLException {
        return new MockResultSet(this);
    }

    public int executeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
        return 1;
    }

    public int executeUpdate(String sql, int columnIndexes[]) throws SQLException {
        return 1;
    }

    public int executeUpdate(String sql, String columnNames[]) throws SQLException {
        return 1;
    }

    public boolean execute(String sql, int autoGeneratedKeys) throws SQLException {
        return true;
    }

    public boolean execute(String sql, int columnIndexes[]) throws SQLException {
        return true;
    }

    public boolean execute(String sql, String columnNames[]) throws SQLException {
        return true;
    }

    public int getResultSetHoldability() throws SQLException {
        return 1;
    }

    public boolean isPoolable() throws SQLException {
        return false;
    }

    public void setPoolable(boolean poolable) throws SQLException {
    }

    public void closeOnCompletion() throws SQLException {
    }

    public boolean isCloseOnCompletion() throws SQLException {
        return false;
    }
}
