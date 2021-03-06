package liquibase.ext.keyspace.sqlgenerator;

import liquibase.database.Database;
import liquibase.ext.keyspace.database.KeyspaceDatabase;
import liquibase.sql.Sql;
import liquibase.sql.UnparsedSql;
import liquibase.sqlgenerator.SqlGeneratorChain;
import liquibase.sqlgenerator.core.DeleteGenerator;
import liquibase.statement.core.DeleteStatement;

public class DeleteGeneratorKeyspace extends DeleteGenerator {

	@Override
	public int getPriority() {
		return PRIORITY_DATABASE;
	}

	@Override
	public boolean supports(DeleteStatement statement, Database database) {
		return super.supports(statement, database) && database instanceof KeyspaceDatabase;
	}

	@Override
	public Sql[] generateSql(DeleteStatement statement, Database database, SqlGeneratorChain sqlGeneratorChain) {

		if (statement.getWhere() == null) {

			String sql = "TRUNCATE " + database
					.escapeTableName(statement.getCatalogName(), statement.getSchemaName(), statement.getTableName());
			return new Sql[] { new UnparsedSql(sql, getAffectedTable(statement)) };
		} else {

			return super.generateSql(statement, database, sqlGeneratorChain);
		}

	}

}
