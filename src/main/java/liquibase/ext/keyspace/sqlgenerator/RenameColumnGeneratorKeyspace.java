package liquibase.ext.keyspace.sqlgenerator;

import liquibase.database.Database;
import liquibase.ext.keyspace.database.KeyspaceDatabase;
import liquibase.sql.Sql;
import liquibase.sql.UnparsedSql;
import liquibase.sqlgenerator.SqlGeneratorChain;
import liquibase.sqlgenerator.core.RenameColumnGenerator;
import liquibase.statement.core.RenameColumnStatement;

public class RenameColumnGeneratorKeyspace extends RenameColumnGenerator {
    @Override
    public int getPriority() {
        return PRIORITY_DATABASE;
    }

    @Override
    public boolean supports(RenameColumnStatement statement, Database database) {
        return database instanceof KeyspaceDatabase;
    }

    @Override
    public Sql[] generateSql(RenameColumnStatement statement, Database database, SqlGeneratorChain sqlGeneratorChain) {
        return new Sql[]{
                new UnparsedSql(
                        "ALTER TABLE "
                                + database.escapeTableName(statement.getCatalogName(), statement.getSchemaName(), statement.getTableName())
                                + " RENAME "
                                + database.escapeColumnName(statement.getCatalogName(), statement.getSchemaName(), statement.getTableName(), statement.getOldColumnName())
                                + " TO "
                                + database.escapeColumnName(statement.getCatalogName(), statement.getSchemaName(), statement.getTableName(), statement.getNewColumnName()),
                        getAffectedOldColumn(statement), getAffectedNewColumn(statement))};
    }
}
