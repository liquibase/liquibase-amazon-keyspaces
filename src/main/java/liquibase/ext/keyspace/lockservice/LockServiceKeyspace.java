package liquibase.ext.keyspace.lockservice;

import liquibase.Scope;
import liquibase.database.Database;
import liquibase.database.ObjectQuotingStrategy;
import liquibase.database.core.DB2Database;
import liquibase.database.core.DerbyDatabase;
import liquibase.database.core.MSSQLDatabase;
import liquibase.exception.DatabaseException;
import liquibase.exception.LiquibaseException;
import liquibase.exception.LockException;
import liquibase.exception.UnexpectedLiquibaseException;
import liquibase.executor.Executor;
import liquibase.executor.ExecutorService;
import liquibase.executor.LoggingExecutor;
import liquibase.ext.keyspace.database.KeyspaceDatabase;
import liquibase.lockservice.StandardLockService;
import liquibase.snapshot.SnapshotGeneratorFactory;
import liquibase.sql.Sql;
import liquibase.sqlgenerator.SqlGeneratorFactory;
import liquibase.statement.core.*;
import liquibase.structure.core.Table;

import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class LockServiceKeyspace extends StandardLockService {

    private boolean isDatabaseChangeLogLockTableInitialized;
    private ObjectQuotingStrategy quotingStrategy;

    private Boolean hasKeyspaceChangeLogLockTable;
    private final SecureRandom random = new SecureRandom();

    // Keyspace does not work with aggregation functions thus we must use a different way to check locks
    @Override
    public void init() throws DatabaseException {
        boolean createdTable = false;
        Executor executor = Scope.getCurrentScope().getSingleton(ExecutorService.class).getExecutor("jdbc",  database);

        int maxIterations = 20;
        if (executor instanceof LoggingExecutor) {
            //can't / don't have to re-check
            if (hasDatabaseChangeLogLockTable()) {
                maxIterations = 0;
            } else {
                maxIterations = 1;
            }
        }
        for (int i = 0; i < maxIterations; i++) {
            try {
                if (!hasDatabaseChangeLogLockTable(true)) {
                    executor.comment("Create Database Lock Table");
                    executor.execute(new CreateDatabaseChangeLogLockTableStatement());
                    database.commit();
                    Scope.getCurrentScope().getLog(getClass()).fine(
                            "Created database lock table with name: " +
                                    database.escapeTableName(
                                            database.getLiquibaseCatalogName(),
                                            database.getLiquibaseSchemaName(),
                                            database.getDatabaseChangeLogLockTableName()
                                    )
                    );
                    this.hasKeyspaceChangeLogLockTable = true;
                    createdTable = true;
                }

                if (!isDatabaseChangeLogLockTableInitialized(createdTable, true)) {
                    executor.comment("Initialize Database Lock Table");
                    // Can not use trunkate thus we have to look through each item and delete it
                    // Otherwise we would have to wait max of 1 hour to delete and recreate the table.
                    //executor.execute(new InitializeDatabaseChangeLogLockTableStatement());

                    List<Map<String, ?>> ret = executor.queryForList(
                            new RawSqlStatement(
                                "Select * from "+ database.escapeTableName(
                                        database.getLiquibaseCatalogName(),
                                        database.getLiquibaseSchemaName(),
                                        database.getDatabaseChangeLogLockTableName()) + ";"
                            )
                    );

                    for (Map<String, ?> item: ret) {
                        executor.execute(
                                new RawSqlStatement(
                                        "DELETE FROM " + database.escapeTableName(
                                                database.getLiquibaseCatalogName(),
                                                database.getLiquibaseSchemaName(),
                                                database.getDatabaseChangeLogLockTableName())
                                                + " WHERE id = " + item.get("ID") + ";"
                                )
                        );
                    }


                    database.commit();
                }

                if (executor.updatesDatabase() && (database instanceof DerbyDatabase) && ((DerbyDatabase) database)
                        .supportsBooleanDataType() || database.getClass().isAssignableFrom(DB2Database.class) && ((DB2Database) database)
                        .supportsBooleanDataType()) {
                    //check if the changelog table is of an old smallint vs. boolean format
                    String lockTable = database.escapeTableName(
                            database.getLiquibaseCatalogName(),
                            database.getLiquibaseSchemaName(),
                            database.getDatabaseChangeLogLockTableName()
                    );
                    Object obj = executor.queryForObject(
                            new RawSqlStatement(
                                    //"SELECT MIN(locked) AS test FROM " + lockTable + " FETCH FIRST ROW ONLY"
                                    "SELECT locked AS test FROM " + lockTable + " limit 1"
                            ), Object.class
                    );
                    if (!(obj instanceof Boolean)) { //wrong type, need to recreate table
                        executor.execute(
                                new DropTableStatement(
                                        database.getLiquibaseCatalogName(),
                                        database.getLiquibaseSchemaName(),
                                        database.getDatabaseChangeLogLockTableName(),
                                        false
                                )
                        );
                        executor.execute(new CreateDatabaseChangeLogLockTableStatement());
                        executor.execute(new InitializeDatabaseChangeLogLockTableStatement());
                    }
                }
            } catch (Exception e) {
                if (i == maxIterations - 1) {
                    throw e;
                } else {
                    Scope.getCurrentScope().getLog(getClass()).fine("Failed to create or initialize the lock table, trying again, iteration " + (i + 1) + " of " + maxIterations, e);
                    // If another node already created the table, then we need to rollback this current transaction,
                    // otherwise servers like Postgres will not allow continued use of the same connection, failing with
                    // a message like "current transaction is aborted, commands ignored until end of transaction block"

                    // Keyspace takes alot of time to set up, thus a rollback here will not work.
                    //database.rollback();
                    try {
                        Thread.sleep(random.nextInt(1000));
                    } catch (InterruptedException ex) {
                        Scope.getCurrentScope().getLog(getClass()).warning("Lock table retry loop thread sleep interrupted", ex);
                    }
                }
            }
        }
    }

    protected boolean hasDatabaseChangeLogLockTable(boolean forceRecheck) {
        if (forceRecheck || hasKeyspaceChangeLogLockTable == null) {
            try {
                // Does not work as it uses COUNT(*)
//                hasKeyspaceChangeLogLockTable = SnapshotGeneratorFactory.getInstance()
//                        .hasDatabaseChangeLogLockTable(database);

                String lockTable = database.escapeTableName(
                        database.getLiquibaseCatalogName(),
                        database.getLiquibaseSchemaName(),
                        database.getDatabaseChangeLogLockTableName()
                );

                Scope.getCurrentScope().getSingleton(ExecutorService.class).getExecutor("jdbc",  database)
                        .queryForList(
                             new RawSqlStatement("SELECT * FROM " + lockTable + " limit 1")
                         );
                hasKeyspaceChangeLogLockTable = true;
            } catch (liquibase.exception.DatabaseException e) {
                hasKeyspaceChangeLogLockTable = false;
            } catch (Exception e){
                throw new UnexpectedLiquibaseException(e);
            }
        }
        return hasKeyspaceChangeLogLockTable;
    }

    /**
     * Determine whether the databasechangeloglock table has been initialized.
     * @param forceRecheck if true, do not use any cached information, and recheck the actual database
     */
    protected boolean isDatabaseChangeLogLockTableInitialized(final boolean tableJustCreated, final boolean forceRecheck) {
        if (!isDatabaseChangeLogLockTableInitialized || forceRecheck) {
            Executor executor = Scope.getCurrentScope().getSingleton(ExecutorService.class).getExecutor("jdbc", database);

            try {

                // Key
                isDatabaseChangeLogLockTableInitialized = !(executor.queryForList(
                        new RawSqlStatement("SELECT * FROM " +
                                database.escapeTableName(
                                        database.getLiquibaseCatalogName(),
                                        database.getLiquibaseSchemaName(),
                                        database.getDatabaseChangeLogLockTableName()
                                )
                        )
                ).size() > 0);

            } catch (LiquibaseException e) {
                if (executor.updatesDatabase()) {
                    throw new UnexpectedLiquibaseException(e);
                } else {
                    //probably didn't actually create the table yet.
                    isDatabaseChangeLogLockTableInitialized = !tableJustCreated;
                }
            }
        }
        return isDatabaseChangeLogLockTableInitialized;
    }



    @Override
    public int getPriority() {
        return PRIORITY_DATABASE;
    }

    @Override
    public boolean supports(Database database) {
        return database instanceof KeyspaceDatabase;
    }

    @Override
    public boolean acquireLock() throws LockException {

        if (super.hasChangeLogLock) {
            return true;
        }

        Executor executor = Scope.getCurrentScope().getSingleton(ExecutorService.class).getExecutor("jdbc", database);

        try {

            database.rollback();
            init();

            //SELECT locked FROM betterbotz.DATABASECHANGELOGLOCK where locked = TRUE ALLOW FILTERING
            Statement statement = ((KeyspaceDatabase) database).getStatement();
            ResultSet rs = statement.executeQuery("SELECT locked FROM " + database.getDefaultCatalogName() + ".DATABASECHANGELOGLOCK where locked = TRUE ALLOW FILTERING");

            boolean locked;
            if (rs.next()) {
                locked = rs.getBoolean("locked");
            } else {
                locked = false;
            }

            if (locked) {
                return false;
            } else {

                executor.comment("Lock Database");
                int rowsUpdated = executor.update(new LockDatabaseChangeLogStatement());
                if ((rowsUpdated == -1) && (database instanceof MSSQLDatabase)) {

                    Scope.getCurrentScope().getLog(this.getClass()).info("Database did not return a proper row count (Might have NOCOUNT enabled)");
                    database.rollback();
                    Sql[] sql = SqlGeneratorFactory.getInstance().generateSql(
                            new LockDatabaseChangeLogStatement(), database
                    );
                    if (sql.length != 1) {
                        throw new UnexpectedLiquibaseException("Did not expect " + sql.length + " statements");
                    }
                    rowsUpdated = executor.update(new RawSqlStatement("EXEC sp_executesql N'SET NOCOUNT OFF " +
                            sql[0].toSql().replace("'", "''") + "'"));
                }
                if (rowsUpdated > 1) {
                    throw new LockException("Did not update change log lock correctly");
                }
                if (rowsUpdated == 0) {
                    // another node was faster
                    return false;
                }
                database.commit();
                Scope.getCurrentScope().getLog(this.getClass()).info("successfully.acquired.change.log.lock");


                hasChangeLogLock = true;

                database.setCanCacheLiquibaseTableInfo(true);
                return true;
            }
        } catch (Exception e) {
            throw new LockException(e);
        } finally {
            try {
                database.rollback();
            } catch (DatabaseException e) {
            }
        }

    }

    @Override
    public void releaseLock() throws LockException {

        ObjectQuotingStrategy incomingQuotingStrategy = null;
        if (this.quotingStrategy != null) {
            incomingQuotingStrategy = database.getObjectQuotingStrategy();
            database.setObjectQuotingStrategy(this.quotingStrategy);
        }

        Executor executor = Scope.getCurrentScope().getSingleton(ExecutorService.class).getExecutor("jdbc", database);
        try {
            if (this.hasDatabaseChangeLogLockTable()) {
                executor.comment("Release Database Lock");
                database.rollback();
                executor.update(new UnlockDatabaseChangeLogStatement());
                database.commit();
            }
        } catch (Exception e) {
            throw new LockException(e);
        } finally {
            try {
                hasChangeLogLock = false;

                database.setCanCacheLiquibaseTableInfo(false);
                Scope.getCurrentScope().getLog(getClass()).info("Successfully released change log lock");
                database.rollback();
            } catch (DatabaseException e) {
            }
            if (incomingQuotingStrategy != null) {
                database.setObjectQuotingStrategy(incomingQuotingStrategy);
            }
        }
    }

    @Override
    public boolean hasDatabaseChangeLogLockTable() {
        return ((KeyspaceDatabase)database).hasDatabaseChangeLogLockTable();
    }

    @Override
    public boolean isDatabaseChangeLogLockTableInitialized(final boolean tableJustCreated) {
        if (!isDatabaseChangeLogLockTableInitialized) {

            // table creation in AWS Keyspaces is not immediate like other Cassandras
            // https://docs.aws.amazon.com/keyspaces/latest/devguide/working-with-tables.html#tables-create
            // let's see if the DATABASECHANGELOG table is active before doing stuff
            //TODO improve this AWS check when we find out better way
            if (database.getConnection().getURL().toLowerCase().contains("amazonaws")) {
                try {
                    int DBCL_GET_TABLE_ACTIVE_ATTEMPS = 10;
                    while (DBCL_GET_TABLE_ACTIVE_ATTEMPS >= 0) {

                        Statement statement = ((KeyspaceDatabase) database).getStatement();
                        ResultSet rs = statement.executeQuery("SELECT keyspace_name, table_name, status FROM " +
                                "system_schema_mcs.tables WHERE keyspace_name = '" + database.getDefaultCatalogName() +
                                "' AND table_name = 'databasechangeloglock'"); //todo: aws keyspaces appears to be all lowercase, dunno if that's the same with other cassandras...
                        if (!rs.next()) {
                            //need to create table
                            return false;
                        } else {
                            do {
                                String status = rs.getString("status");
                                if (status.equals("ACTIVE")) {
                                    isDatabaseChangeLogLockTableInitialized = true;
                                    return true;
                                } else if (status.equals("CREATING")) {
                                    DBCL_GET_TABLE_ACTIVE_ATTEMPS--;
                                    int timeout = 3;
                                    Scope.getCurrentScope().getLog(this.getClass()).info("DATABASECHANGELOGLOCK table in CREATING state. Checking again in " + timeout + " seconds.");
                                    TimeUnit.SECONDS.sleep(timeout);
                                } else {
                                    Scope.getCurrentScope().getLog(this.getClass()).severe(String.format("DATABASECHANGELOGLOCK table in %s state. ", status));
                                    return false;
                                    // something went very wrong, are we having issues with another Cassandra platform...?
                                }

                            } while (rs.next());
                        }
                    }
                } catch (InterruptedException | SQLException |  DatabaseException e) {
                    throw new UnexpectedLiquibaseException(e);
                }
                //not AWS scenario
            } else {
                Executor executor = Scope.getCurrentScope().getSingleton(ExecutorService.class).getExecutor("jdbc", database);
                try {
                    isDatabaseChangeLogLockTableInitialized = executor.queryForInt(
                            new RawSqlStatement("SELECT COUNT(*) FROM " + database.getDefaultCatalogName() + ".DATABASECHANGELOGLOCK")
                    ) > 0;
                } catch (LiquibaseException e) {
                    if (executor.updatesDatabase()) {
                        throw new UnexpectedLiquibaseException(e);
                    } else {
                        //probably didn't actually create the table yet.
                        isDatabaseChangeLogLockTableInitialized = !tableJustCreated;
                    }
                }
            }
        }
        return isDatabaseChangeLogLockTableInitialized;
    }

}
