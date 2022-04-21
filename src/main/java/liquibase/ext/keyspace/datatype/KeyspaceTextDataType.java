package liquibase.ext.keyspace.datatype;

import liquibase.change.core.LoadDataChange;
import liquibase.database.Database;
import liquibase.datatype.DataTypeInfo;
import liquibase.datatype.DatabaseDataType;
import liquibase.datatype.LiquibaseDataType;
import liquibase.ext.keyspace.database.KeyspaceDatabase;

@DataTypeInfo(name = "text", minParameters = 0, maxParameters = 0, priority = LiquibaseDataType.PRIORITY_DATABASE)
public class KeyspaceTextDataType extends LiquibaseDataType {

    @Override
    public boolean supports(Database database) {
        return database instanceof KeyspaceDatabase;
    }

    @Override
    public DatabaseDataType toDatabaseDataType(Database database) {
        if (database instanceof KeyspaceDatabase) {
            return new DatabaseDataType("TEXT", getParameters());
        }
        return super.toDatabaseDataType(database);
    }

    @Override
    public LoadDataChange.LOAD_DATA_TYPE getLoadTypeName() {
        return LoadDataChange.LOAD_DATA_TYPE.STRING;
    }
}
