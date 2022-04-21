package liquibase.ext.keyspace.database;

import com.simba.cassandra.cassandra.core.CDBJDBCConnection;
import com.simba.cassandra.jdbc.jdbc42.S42Connection;
import liquibase.Scope;
import liquibase.change.Change;
import liquibase.change.core.CreateTableChange;
import liquibase.change.core.DropTableChange;
import liquibase.changelog.DatabaseChangeLog;
import liquibase.database.AbstractJdbcDatabase;
import liquibase.database.DatabaseConnection;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.DatabaseException;
import liquibase.exception.LiquibaseException;
import liquibase.sql.visitor.SqlVisitor;

import java.sql.*;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * AWS Keyspace NoSQL database support.
 */
public class KeyspaceDatabase extends AbstractJdbcDatabase {
    public static final String PRODUCT_NAME = "Keyspace";

    private String keyspace;

    @Override
    public String getShortName() {
        return "keyspace";
    }

    @Override
    public int getPriority() {
        return PRIORITY_DEFAULT;
    }

    @Override
    protected String getDefaultDatabaseProductName() {
        return "Keyspace";
    }

    @Override
    public Integer getDefaultPort() {
        return 9142;
    }

    @Override
    public boolean supportsInitiallyDeferrableColumns() {
        return false;
    }

    @Override
    public boolean supportsSequences() {
        return false;
    }

    @Override
    public boolean isCorrectDatabaseImplementation(DatabaseConnection conn) throws DatabaseException {
        String databaseProductName = conn.getDatabaseProductName();
        return databaseProductName.equalsIgnoreCase("Cassandra") && urlMapsToKeyspace(conn.getURL());
    }

    @Override
    public String getDefaultDriver(String url) {
        if (urlMapsToKeyspace(url)) {
            return "com.simba.cassandra.jdbc42.Driver";
        }

        return null;
    }

    private boolean urlMapsToKeyspace(String url){
        return String.valueOf(url).matches("^jdbc:cassandra://cassandra(-fips)?\\.(.)+\\.amazonaws\\.com:(.)+");
    }

    @Override
    public boolean supportsTablespaces() {
        return false;
    }

    @Override
    public boolean supportsRestrictForeignKeys() {
        return false;
    }

    @Override
    public boolean supportsDropTableCascadeConstraints() {
        return false;
    }

    @Override
    public boolean isAutoCommit() {
        return true;
    }

    @Override
    public void setAutoCommit(boolean b) {
        // FIXME: figure out if this can actually be changed otherwise remove
    }

    @Override
    public boolean isCaseSensitive() {
        return true;
    }

    @Override
    public String getCurrentDateTimeFunction() {
        // no alternative in cassandra, using client time
        return String.valueOf(System.currentTimeMillis());
    }

    public String getKeyspace() {
        if (keyspace == null) {
            try {
                if (this.getConnection() instanceof JdbcConnection) {
                    keyspace = ((CDBJDBCConnection) ((S42Connection) ((JdbcConnection) (this).getConnection())
                            .getUnderlyingConnection()).getConnection()).getSession().getLoggedKeyspace();
                }
            } catch (Exception e) {
                Scope.getCurrentScope().getLog(KeyspaceDatabase.class)
                        .severe("Could not get keyspace from connection", e);

            }
        }
        return keyspace;

    }

    @Override
    public void executeStatements(Change change, DatabaseChangeLog changeLog, List<SqlVisitor> sqlVisitors) throws LiquibaseException {


        super.executeStatements(change, changeLog, sqlVisitors);

        // table creation and deletion in AWS Keyspace is not immediate like other Cassandra Platforms,
        // As AWS Keyspace has SLA is at max 30 minutes for these changes.
        // https://docs.aws.amazon.com/keyspaces/latest/devguide/working-with-tables.html#tables-create

        // In the following statements, We check the Read only DATABASECHANGELOG
        // to see what status the tables are in before we move on.
        if (change instanceof CreateTableChange || change instanceof DropTableChange) {

            String Table_Name;
            boolean creating;
            if (change instanceof CreateTableChange) {
                Table_Name = ((CreateTableChange) change).getTableName();
                creating = true;
            } else {
                Table_Name = ((DropTableChange) change).getTableName();
                creating = false;
            }

            int DBCL_GET_TABLE_ATTEMPTS = 30;
            while (DBCL_GET_TABLE_ATTEMPTS >= 0) {
                try {
                    Statement statement = getStatement();

                    ResultSet rs = statement.executeQuery("SELECT keyspace_name, table_name, status FROM " +
                            "system_schema_mcs.tables WHERE keyspace_name = '" + getDefaultCatalogName() +
                            "' AND table_name = '" + Table_Name + "'");

                    while (rs.next()) {
                        String status = rs.getString("status");
                        if (creating) {
                            if (status.equals("ACTIVE")) {
                                //table is active, we're done here
                                return;
                            } else if (status.equals("CREATING")) {
                                Scope.getCurrentScope().getLog(this.getClass()).info("table status = CREATING");
                                DBCL_GET_TABLE_ATTEMPTS--;
                                TimeUnit.SECONDS.sleep(5);
                            } else {
                                Scope.getCurrentScope().getLog(this.getClass()).severe(String.format("%s table in %s state.", Table_Name, status));
                                // something went very wrong, are we having issues with another Cassandra platform...?
                                return;
                            }
                        } else {
                            if (status == null) {
                                //table is deleted, we're done here
                                return;
                            } else if (status.equals("DELETING")) {
                                Scope.getCurrentScope().getLog(this.getClass()).info("table status = DELETING");
                                DBCL_GET_TABLE_ATTEMPTS--;
                                TimeUnit.SECONDS.sleep(5);
                            } else {
                                Scope.getCurrentScope().getLog(this.getClass()).severe(String.format("%s table in %s state.", Table_Name, status));
                                // something went very wrong, are we having issues with another Cassandra platform...?
                                return;
                            }
                        }

                    }
                } catch (InterruptedException | SQLException e) {
                    throw new DatabaseException(e);
                }

            } // while loop for attempting to check change
        } // If create or drop change

    }

    @Override
    public boolean supportsSchemas() {
        return false;
    }

    /**
     * Cassandra actually doesn't support neither catalogs nor schemas, but keyspaces.
     * As default liquibase classes don't know what is keyspace we gonna use keyspace instead of catalog
     */
    @Override
    public String getDefaultCatalogName() {
        return getKeyspace();
    }

    public Statement getStatement() throws DatabaseException {
        return ((JdbcConnection) super.getConnection()).createStatement();
    }

    public boolean hasDatabaseChangeLogLockTable() {
        boolean hasChangeLogLockTable;
        try {
            Statement statement = getStatement();
            statement.executeQuery("SELECT ID from " + getDefaultCatalogName() + ".DATABASECHANGELOGLOCK");
            statement.close();
            hasChangeLogLockTable = true;
        } catch (SQLException e) {
            Scope.getCurrentScope().getLog(getClass()).info("No DATABASECHANGELOGLOCK available in cassandra.");
            hasChangeLogLockTable = false;
        } catch (DatabaseException e) {
            e.printStackTrace();
            hasChangeLogLockTable = false;
        }

        // needs to be generated up front
        return hasChangeLogLockTable;
    }

    @Override
    public boolean jdbcCallsCatalogsSchemas() {
        return true;
    }

    @Override
    public boolean supportsNotNullConstraintNames() {
        return false;
    }

    @Override
    public boolean supportsPrimaryKeyNames() {
        return false;
    }
}
