package liquibase.ext.keyspace.sqlgenerator;

import liquibase.database.Database;
import liquibase.ext.keyspace.database.KeyspaceDatabase;
import liquibase.sqlgenerator.core.GetNextChangeSetSequenceValueGenerator;
import liquibase.statement.core.GetNextChangeSetSequenceValueStatement;

public class GetNextChangeSetSequenceValueGeneratorKeyspace extends GetNextChangeSetSequenceValueGenerator {

    @Override
    public int getPriority() {
        return PRIORITY_DATABASE;
    }

    @Override
    public boolean supports(GetNextChangeSetSequenceValueStatement statement, Database database) {
        return super.supports(statement, database) && database instanceof KeyspaceDatabase;
    }

}
