package liquibase.ext.keyspace.sqlgenerator;

import liquibase.database.Database;
import liquibase.ext.keyspace.database.KeyspaceDatabase;
import liquibase.sql.Sql;
import liquibase.sqlgenerator.SqlGeneratorChain;
import liquibase.sqlgenerator.SqlGeneratorFactory;
import liquibase.sqlgenerator.core.UnlockDatabaseChangeLogGenerator;
import liquibase.statement.core.UnlockDatabaseChangeLogStatement;
import liquibase.statement.core.UpdateStatement;

public class UnlockDatabaseChangeLogGeneratorKeyspace extends UnlockDatabaseChangeLogGenerator {

    @Override
    public int getPriority() {
        return PRIORITY_DATABASE;
    }

    @Override
    public boolean supports(UnlockDatabaseChangeLogStatement statement, Database database) {
        return database instanceof KeyspaceDatabase;
    }

    @Override
    public Sql[] generateSql(UnlockDatabaseChangeLogStatement statement, Database database, SqlGeneratorChain sqlGeneratorChain) {

        UpdateStatement releaseStatement = new UpdateStatement(database.getLiquibaseCatalogName(), null,
                database.getDatabaseChangeLogLockTableName());
        releaseStatement.addNewColumnValue("LOCKED", false);
        releaseStatement.addNewColumnValue("LOCKEDBY", null);
        releaseStatement.setWhereClause(database.escapeColumnName(database.getLiquibaseCatalogName(), null,
                database.getDatabaseChangeLogTableName(), "ID") + " = 1");

        return SqlGeneratorFactory.getInstance().generateSql(releaseStatement, database);
    }

}
