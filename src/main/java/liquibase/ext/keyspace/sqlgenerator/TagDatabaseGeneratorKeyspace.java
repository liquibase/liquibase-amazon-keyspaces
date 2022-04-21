package liquibase.ext.keyspace.sqlgenerator;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import liquibase.database.Database;
import liquibase.database.ObjectQuotingStrategy;
import liquibase.datatype.DataTypeFactory;
import liquibase.exception.DatabaseException;
import liquibase.ext.keyspace.database.KeyspaceDatabase;
import liquibase.sql.Sql;
import liquibase.sql.UnparsedSql;
import liquibase.sqlgenerator.SqlGeneratorChain;
import liquibase.sqlgenerator.core.TagDatabaseGenerator;
import liquibase.statement.core.TagDatabaseStatement;

public class TagDatabaseGeneratorKeyspace extends TagDatabaseGenerator {

	@Override
	public int getPriority() {
		return PRIORITY_DATABASE;
	}

	@Override
	public boolean supports(TagDatabaseStatement statement, Database database) {
		return database instanceof KeyspaceDatabase;
	}

	@Override
	public Sql[] generateSql(TagDatabaseStatement statement, Database database, SqlGeneratorChain sqlGeneratorChain) {
		ObjectQuotingStrategy currentStrategy = database.getObjectQuotingStrategy();
		database.setObjectQuotingStrategy(ObjectQuotingStrategy.LEGACY);

		try {
			String tagEscaped = DataTypeFactory.getInstance().fromObject(statement.getTag(), database).objectToSql(statement.getTag(), database);
			

			Statement statement1 = ((KeyspaceDatabase) database).getStatement();
			//Query to get last executed changeset date
			String query1 = "SELECT TOUNIXTIMESTAMP(MAX(DATEEXECUTED)) as DATEEXECUTED FROM " + 
					database.escapeTableName(database.getLiquibaseCatalogName(), database.getLiquibaseSchemaName(), "databasechangelog");
			ResultSet rs1 = statement1.executeQuery(query1);
			String date = "";
			while (rs1.next()) {
				date =  rs1.getString("DATEEXECUTED");
			}
			rs1.close();
			//Query to get composite key details of last executed change set
			String query2 = "select id,author, filename from " + 
					database.escapeTableName(database.getLiquibaseCatalogName(), database.getLiquibaseSchemaName(), "databasechangelog")
							+ " where dateexecuted = '"+date+"' ALLOW FILTERING";
			ResultSet rs2 = statement1.executeQuery(query2);
			String id = "", author = "", filename = "";
			while (rs2.next()) {
				id = rs2.getString("id");
				author = rs2.getString("author");
				filename = rs2.getString("filename");
			}
			rs2.close();
			statement1.close();
			//Query to update tag 
			String updateQuery = "UPDATE " 
					+ database.escapeTableName(database.getLiquibaseCatalogName(), database.getLiquibaseSchemaName(), "databasechangelog")
					+ " SET TAG = "+tagEscaped
					+ " WHERE id = '"+ id +"' and author = '"+ author +"' and filename = '"+ filename+ "'";

			return new Sql[]{
				new UnparsedSql(updateQuery)
			};

			

		} catch (SQLException | DatabaseException e) {
			return super.generateSql(statement, database, sqlGeneratorChain);
		} finally {
			database.setObjectQuotingStrategy(currentStrategy);
		}
	}
}
